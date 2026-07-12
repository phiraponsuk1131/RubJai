package app.rubjai.mobile

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

data class PendingSlip(val id: String, val draft: DraftTransaction)

object PendingSlipStore {
    private const val PREFS = "auto_kplus_scan"
    private const val PENDING = "pending"
    private const val PROCESSED = "processed"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized fun load(context: Context): List<PendingSlip> = runCatching {
        val array = JSONArray(prefs(context).getString(PENDING, "[]"))
        (0 until array.length()).map { index -> array.getJSONObject(index).toPendingSlip() }
    }.getOrDefault(emptyList())

    @Synchronized fun add(context: Context, mediaId: String, draft: DraftTransaction) {
        val processed = prefs(context).getStringSet(PROCESSED, emptySet()).orEmpty().toMutableSet()
        if (!processed.add(mediaId)) return
        val items = load(context).toMutableList().apply { add(PendingSlip(UUID.randomUUID().toString(), draft)) }
        save(context, items)
        prefs(context).edit().putStringSet(PROCESSED, processed.toList().takeLast(1000).toSet()).apply()
    }

    fun isProcessed(context: Context, mediaId: String) = prefs(context).getStringSet(PROCESSED, emptySet()).orEmpty().contains(mediaId)
    @Synchronized fun markProcessed(context: Context, mediaId: String) {
        val processed = prefs(context).getStringSet(PROCESSED, emptySet()).orEmpty().toMutableSet().apply { add(mediaId) }
        prefs(context).edit().putStringSet(PROCESSED, processed.toList().takeLast(1000).toSet()).apply()
    }
    @Synchronized fun remove(context: Context, id: String) = save(context, load(context).filterNot { it.id == id })
    @Synchronized fun clearUsage(context: Context) { prefs(context).edit().remove(PENDING).remove(PROCESSED).apply() }
    private fun save(context: Context, items: List<PendingSlip>) { prefs(context).edit().putString(PENDING, JSONArray(items.map { it.toJson() }).toString()).apply() }
    private fun PendingSlip.toJson() = JSONObject().put("id", id).put("amount", draft.amount).put("title", draft.title).put("rawText", draft.rawText.take(3000)).put("category", draft.category).put("remark", draft.remark).put("occurredAt", draft.occurredAt)
    private fun JSONObject.toPendingSlip() = PendingSlip(getString("id"), DraftTransaction(optString("amount"), optString("title"), TransactionType.EXPENSE, "auto_kplus", optString("rawText"), optString("category", "อื่น ๆ"), optString("remark"), optString("occurredAt")))
}

object AutoSlipScheduler {
    private const val WORK = "rubjai_daily_kplus_scan"
    private const val PREFS = "auto_kplus_scan"
    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
        val manager = WorkManager.getInstance(context)
        if (!enabled) { manager.cancelUniqueWork(WORK); return }
        val now = Calendar.getInstance()
        val dailyRun = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 30); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val request = PeriodicWorkRequestBuilder<KPlusScanWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(dailyRun.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()
        manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        manager.enqueue(OneTimeWorkRequestBuilder<KPlusScanWorker>().build())
    }
}

class KPlusScanWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val kPlusReference = Regex("[0-9]{10,}(?:CPM|DQR|DTF)[0-9]+", RegexOption.IGNORE_CASE)

    override fun doWork(): Result {
        if (!AutoSlipScheduler.enabled(applicationContext)) return Result.success()
        val startToday = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val startTomorrow = (startToday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val since = startToday.timeInMillis / 1000
        val before = startTomorrow.timeInMillis / 1000
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?"
        val cursor = runCatching { applicationContext.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, arrayOf(since.toString(), before.toString()), "${MediaStore.Images.Media.DATE_ADDED} DESC") }.getOrNull() ?: return Result.success()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            cursor.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                var scanned = 0
                while (it.moveToNext() && scanned < 200) {
                    val id = it.getLong(idColumn)
                    val mediaKey = id.toString()
                    if (PendingSlipStore.isProcessed(applicationContext, mediaKey)) continue
                    scanned++
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val imageDate = Date(it.getLong(dateColumn) * 1000)
                    runCatching {
                        val text = Tasks.await(recognizer.process(InputImage.fromFilePath(applicationContext, uri))).text
                        val isKPlus = text.contains("K+", true) || kPlusReference.containsMatchIn(text)
                        val draft = if (isKPlus) SlipParser.parse(text, "auto_kplus", imageDate) else null
                        if (draft?.amount?.toDoubleOrNull()?.let { value -> value > 0 } == true) PendingSlipStore.add(applicationContext, mediaKey, draft)
                        else PendingSlipStore.markProcessed(applicationContext, mediaKey)
                    }
                }
            }
        } finally {
            recognizer.close()
        }
        return Result.success()
    }
}
