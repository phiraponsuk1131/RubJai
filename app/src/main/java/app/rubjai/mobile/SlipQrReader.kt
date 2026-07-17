package app.rubjai.mobile

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.security.MessageDigest

data class SlipQrResult(
    val rawValues: List<String> = emptyList(),
    val transactionReference: String = "",
    val amount: String = "",
    val merchantName: String = "",
    val sendingBank: String = "",
)

object SlipQrReader {
    private val transactionReferencePatterns = listOf(
        Regex("[A-Z0-9]{8,}(?:CPM|DQR|DTF)[A-Z0-9-]*", RegexOption.IGNORE_CASE),
        Regex("(?i)(?:transRef|transactionRef|reference|ref|เลขที่รายการ|รหัสอ้างอิง)[:=\\s-]*([A-Z0-9-]{6,})"),
        Regex("(?i)(?:^|[^A-Z0-9])([A-Z0-9-]{12,})(?:$|[^A-Z0-9])"),
    )

    fun scanBlocking(context: Context, uri: Uri): SlipQrResult {
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
        return try {
            val values = Tasks.await(scanner.process(InputImage.fromFilePath(context, uri)))
                .mapNotNull { it.rawValue?.trim() }
                .filter(String::isNotBlank)
                .distinct()
            val parsed = values.map(::parseQrPayload)
            SlipQrResult(
                rawValues = values,
                transactionReference = parsed.firstNotNullOfOrNull { it.transactionReference.ifBlank { null } }
                    ?: values.firstNotNullOfOrNull(::extractReference).orEmpty(),
                amount = parsed.firstNotNullOfOrNull { it.amount.ifBlank { null } }.orEmpty(),
                merchantName = parsed.firstNotNullOfOrNull { it.merchantName.ifBlank { null } }.orEmpty(),
                sendingBank = parsed.firstNotNullOfOrNull { it.sendingBank.ifBlank { null } }.orEmpty(),
            )
        } finally {
            scanner.close()
        }
    }

    fun appendToOcrText(text: String, qr: SlipQrResult): String {
        if (qr.rawValues.isEmpty()) return text
        return buildString {
            appendLine("QR สลิป")
            qr.amount.takeIf(String::isNotBlank)?.let { appendLine("จำนวน $it บาท") }
            qr.merchantName.takeIf(String::isNotBlank)?.let { appendLine("ชื่อผู้รับ $it") }
            qr.transactionReference.takeIf(String::isNotBlank)?.let { appendLine("รหัสอ้างอิง $it") }
            qr.sendingBank.takeIf(String::isNotBlank)?.let { appendLine("ธนาคาร $it") }
            appendLine("OCR สำรอง")
            append(text)
            appendLine()
            appendLine("QR ดิบ")
            qr.rawValues.forEach { appendLine(it) }
        }
    }

    fun fingerprint(qr: SlipQrResult): String {
        val stable = qr.transactionReference.ifBlank { qr.rawValues.joinToString("|") }.trim()
        if (stable.isBlank()) return ""
        return MessageDigest.getInstance("SHA-256").digest(stable.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun extractReference(value: String): String? =
        transactionReferencePatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(value)?.groupValues?.lastOrNull()?.takeIf(String::isNotBlank)
        }

    private fun parseQrPayload(value: String): SlipQrResult {
        val tags = parseTlv(value)
        if (tags.isEmpty()) return SlipQrResult(transactionReference = extractReference(value).orEmpty())
        val nested = tags.values.flatMap { parseTlv(it).entries }.associate { it.key to it.value }
        val reference = nested["02"].orEmpty()
            .ifBlank { nested["03"].orEmpty() }
            .ifBlank { tags["62"]?.let { parseTlv(it)["05"].orEmpty() }.orEmpty() }
            .ifBlank { extractReference(value).orEmpty() }
        return SlipQrResult(
            transactionReference = reference,
            amount = tags["54"].orEmpty(),
            merchantName = tags["59"].orEmpty(),
            sendingBank = nested["01"].orEmpty().takeIf { it.length == 3 }.orEmpty(),
        )
    }

    private fun parseTlv(value: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var index = 0
        while (index + 4 <= value.length) {
            val tag = value.substring(index, index + 2)
            val length = value.substring(index + 2, index + 4).toIntOrNull() ?: break
            val start = index + 4
            val end = start + length
            if (tag.any { !it.isDigit() } || end > value.length) break
            result[tag] = value.substring(start, end)
            index = end
        }
        return if (index == value.length) result else emptyMap()
    }
}
