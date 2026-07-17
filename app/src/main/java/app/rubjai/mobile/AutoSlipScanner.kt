package app.rubjai.mobile

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Date
import java.util.UUID

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
    private fun PendingSlip.toJson() = JSONObject()
        .put("id", id)
        .put("amount", draft.amount)
        .put("title", draft.title)
        .put("rawText", draft.rawText.take(3000))
        .put("category", draft.category)
        .put("remark", draft.remark)
        .put("occurredAt", draft.occurredAt)
        .put("slipUri", draft.slipUri)
        .put("slipFingerprint", draft.slipFingerprint)

    private fun JSONObject.toPendingSlip() = PendingSlip(
        getString("id"),
        DraftTransaction(
            optString("amount"),
            optString("title"),
            TransactionType.EXPENSE,
            "auto_bank_slip_qr",
            optString("rawText"),
            optString("category", "ยังไม่จัดหมวด"),
            optString("remark"),
            optString("occurredAt"),
            slipFingerprint = optString("slipFingerprint"),
            slipUri = optString("slipUri"),
        )
    )
}

object KPlusSyncManager {
    private const val WORK = "rubjai_kplus_sync_now"
    private const val PREFS = "auto_kplus_scan"
    private const val MIN_REALTIME_GAP_MS = 3_000L
    const val MAX_LOOKBACK_DAYS = 31
    private var realtimeObserver: ContentObserver? = null
    private var lastRealtimeSyncAt = 0L

    fun hasConsent(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("consent", false)
    fun setConsent(context: Context, consent: Boolean) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("consent", consent).apply()
    fun syncNow(context: Context): UUID {
        val request = OneTimeWorkRequestBuilder<KPlusScanWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
        return request.id
    }
    fun workInfo(context: Context, id: UUID): WorkInfo? = WorkManager.getInstance(context).getWorkInfoById(id).get()
    fun revoke(context: Context) { setConsent(context, false); WorkManager.getInstance(context).cancelUniqueWork(WORK); stopRealtime(context) }

    fun startRealtime(context: Context, onSync: (UUID) -> Unit) {
        if (realtimeObserver != null) return
        val appContext = context.applicationContext
        realtimeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val now = System.currentTimeMillis()
                if (!hasConsent(appContext) || now - lastRealtimeSyncAt < MIN_REALTIME_GAP_MS) return
                lastRealtimeSyncAt = now
                onSync(syncNow(appContext))
            }
        }
        appContext.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, realtimeObserver!!)
    }

    fun stopRealtime(context: Context) {
        realtimeObserver?.let { context.applicationContext.contentResolver.unregisterContentObserver(it) }
        realtimeObserver = null
    }
}

class KPlusScanWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val parserVersion = "slip-v3.0.4-qr-only-1"

    override fun doWork(): Result {
        if (!KPlusSyncManager.hasConsent(applicationContext)) return Result.success()
        val startWindow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -(KPlusSyncManager.MAX_LOOKBACK_DAYS - 1))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val since = startWindow.timeInMillis / 1000
        val before = startTomorrow.timeInMillis / 1000
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?"
        val cursor = runCatching {
            applicationContext.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, arrayOf(since.toString(), before.toString()), "${MediaStore.Images.Media.DATE_ADDED} DESC")
        }.getOrNull() ?: return Result.success()
        var found = 0
        cursor.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            var scanned = 0
            while (it.moveToNext() && scanned < 1000) {
                val id = it.getLong(idColumn)
                val mediaKey = "$parserVersion:$id"
                if (PendingSlipStore.isProcessed(applicationContext, mediaKey)) continue
                scanned++
                setProgressAsync(workDataOf("scanned" to scanned))
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val imageDate = Date(it.getLong(dateColumn) * 1000)
                runCatching {
                    val qr = SlipQrReader.scanBlocking(applicationContext, uri)
                    val draft = SlipQrReader.toDraft(qr, "auto_bank_slip_qr", imageDate)
                        ?.copy(slipUri = uri.toString())
                    if (draft != null && draft.hasCompleteSlipBasics()) {
                        PendingSlipStore.add(applicationContext, mediaKey, draft)
                        found++
                    } else {
                        PendingSlipStore.markProcessed(applicationContext, mediaKey)
                    }
                }
            }
        }
        return Result.success(workDataOf("found" to found))
    }

    private fun DraftTransaction.hasCompleteSlipBasics(): Boolean {
        val hasAmount = amount.toDoubleOrNull()?.let { it > 0 } == true
        val hasQrFingerprint = slipFingerprint.isNotBlank()
        val hasDateTime = occurredAt.isNotBlank() && Regex("[0-2]?[0-9]:[0-5][0-9]").containsMatchIn(occurredAt)
        return hasAmount && hasQrFingerprint && hasDateTime
    }
}
