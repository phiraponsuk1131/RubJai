package app.rubjai.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val repository = TransactionRepository()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RubJaiApp(repository, intent) }
    }
}

data class DraftTransaction(
    val amount: String = "",
    val title: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val source: String = "manual",
    val rawText: String = "",
)

enum class TransactionType { INCOME, EXPENSE }

@Composable
fun RubJaiApp(repository: TransactionRepository, launchIntent: Intent) {
    val context = LocalContext.current
    val colors = lightColorScheme(primary = Color(0xFF0B9B73), secondary = Color(0xFFFF6B57), background = Color(0xFFF4F7FA))
    var entries by remember { mutableStateOf(emptyList<MoneyTransaction>()) }
    var draft by remember { mutableStateOf<DraftTransaction?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val updateManager = remember { InAppUpdateManager() }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateProgress by remember { mutableStateOf<Float?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        repository.observe { entries = it }
        draft = sharedDraft(launchIntent)
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        updateManager.checkForUpdate(version) { availableUpdate = it }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromFilePath(context, uri))
                .addOnSuccessListener { result -> draft = SlipParser.parse(result.text, "slip_ocr"); busy = false }
                .addOnFailureListener { error -> message = "อ่านสลิปไม่สำเร็จ: ${error.localizedMessage}"; busy = false }
        }
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = { Surface(color = Color(0xFF071A3D)) { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Text("RubJai", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("เงินของคุณ เข้าใจง่าย", color = Color(0xFFB9C8E5)) } } },
            floatingActionButton = { FloatingActionButton(onClick = { draft = DraftTransaction() }) { Icon(Icons.Default.Add, "เพิ่มรายการ") } }
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SummaryCard(entries) }
                item {
                    Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                        Icon(Icons.Default.ImageSearch, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "กำลังอ่านสลิป…" else "เลือกสลิปจากเครื่อง")
                    }
                }
                item { Text("แชร์รูปสลิปหรือข้อความแจ้งเงินเข้าจาก LINE มาที่ RubJai แล้วตรวจข้อมูลก่อนบันทึก เงินเดือนเพิ่มเป็นรายรับและตั้งชื่อว่า “เงินเดือน” ได้", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                items(entries, key = { it.id }) { EntryRow(it) }
            }
        }
        draft?.let { current -> TransactionDialog(current, onDismiss = { draft = null }, onSave = { busy = true; repository.add(it, onDone = { error -> busy = false; message = error ?: "บันทึกแล้ว" }); draft = null }) }
        message?.let { AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("ตกลง") } }, text = { Text(it) }) }
        availableUpdate?.let { update ->
            AlertDialog(
                onDismissRequest = { if (updateProgress == null) availableUpdate = null },
                title = { Text("RubJai ${update.version} พร้อมอัปเดต") },
                text = {
                    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        Text(update.releaseNotes)
                        updateProgress?.let { progress ->
                            Spacer(Modifier.height(16.dp)); LinearProgressIndicator({ progress }, Modifier.fillMaxWidth()); Text("กำลังดาวน์โหลด ${(progress * 100).toInt()}%")
                        }
                        updateMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = { Button(enabled = updateProgress == null, onClick = {
                    if (!updateManager.canRequestInstall(context)) { updateMessage = "อนุญาตติดตั้งจากแหล่งนี้ แล้วกลับมากดดาวน์โหลดอีกครั้ง"; updateManager.openInstallPermission(context) }
                    else { updateProgress = 0f; updateManager.download(context, update, { updateProgress = it }) { uri -> updateProgress = null; if (uri != null) updateManager.launchInstaller(context, uri) else updateMessage = "ดาวน์โหลดไม่สำเร็จ กรุณาลองใหม่" } }
                }) { Text("ดาวน์โหลดอัปเดต") } },
                dismissButton = { if (updateProgress == null) TextButton(onClick = { availableUpdate = null }) { Text("ภายหลัง") } },
            )
        }
    }
}

@Composable
private fun SummaryCard(entries: List<MoneyTransaction>) {
    val income = entries.filter { it.type == "INCOME" }.sumOf { it.amount }
    val expense = entries.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF071A3D))) {
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Text("คงเหลือ", color = Color(0xFFB9C8E5)); Text(money(income - expense), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp)); Row { Text("รับ  ${money(income)}", color = Color(0xFF43E1A4)); Spacer(Modifier.weight(1f)); Text("จ่าย  ${money(expense)}", color = Color(0xFFFF8A78)) }
        }
    }
}

@Composable private fun EntryRow(item: MoneyTransaction) { Card { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.title.ifBlank { "ไม่ระบุรายการ" }, fontWeight = FontWeight.SemiBold); Text(item.source, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; Text((if (item.type == "INCOME") "+" else "-") + money(item.amount), color = if (item.type == "INCOME") Color(0xFF0B9B73) else Color(0xFFD84A3A), fontWeight = FontWeight.Bold) } }

@Composable
private fun TransactionDialog(initial: DraftTransaction, onDismiss: () -> Unit, onSave: (DraftTransaction) -> Unit) {
    var amount by remember(initial) { mutableStateOf(initial.amount) }; var title by remember(initial) { mutableStateOf(initial.title) }; var type by remember(initial) { mutableStateOf(initial.type) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("ตรวจรายการ") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row { FilterChip(type == TransactionType.INCOME, { type = TransactionType.INCOME }, { Text("รายรับ") }); Spacer(Modifier.width(8.dp)); FilterChip(type == TransactionType.EXPENSE, { type = TransactionType.EXPENSE }, { Text("รายจ่าย") }) }
        OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("จำนวนเงิน") }, singleLine = true)
        OutlinedTextField(title, { title = it }, label = { Text("รายการ/ร้านค้า") }, singleLine = true)
    } }, confirmButton = { Button(enabled = amount.toDoubleOrNull()?.let { it > 0 } == true, onClick = { onSave(initial.copy(amount = amount, title = title, type = type)) }) { Text("บันทึก") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } })
}

private fun sharedDraft(intent: Intent): DraftTransaction? {
    if (intent.action != Intent.ACTION_SEND) return null
    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
    return SlipParser.parse(text, "line_share")
}

private fun money(value: Double) = NumberFormat.getCurrencyInstance(Locale("th", "TH")).format(value)
