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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
    val category: String = "อื่น ๆ",
    val remark: String = "",
)

enum class TransactionType { INCOME, EXPENSE }

@Composable
fun RubJaiApp(repository: TransactionRepository, launchIntent: Intent) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository() }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(authRepository.auth.currentUser) }
    var authRefresh by remember { mutableIntStateOf(0) }
    DisposableEffect(authRepository) {
        val listener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser }
        authRepository.auth.addAuthStateListener(listener)
        onDispose { authRepository.auth.removeAuthStateListener(listener) }
    }
    val colors = lightColorScheme(primary = Color(0xFF0B9B73), secondary = Color(0xFFFF6B57), background = Color(0xFFF4F7FA))
    if (currentUser == null) {
        MaterialTheme(colorScheme = colors) { AuthScreen(authRepository) }
        return
    }
    if (currentUser?.isEmailVerified != true) {
        MaterialTheme(colorScheme = colors) { VerifyEmailScreen(authRepository, currentUser?.email.orEmpty()) { authRefresh++; currentUser = authRepository.auth.currentUser } }
        return
    }
    var entries by remember { mutableStateOf(emptyList<MoneyTransaction>()) }
    var draft by remember { mutableStateOf<DraftTransaction?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val updateManager = remember { InAppUpdateManager() }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateProgress by remember { mutableStateOf<Float?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var showProfile by remember { mutableStateOf(false) }
    var showDebts by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val verificationRefresh = authRefresh

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
            topBar = { Surface(color = Color(0xFF071A3D)) { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text("RubJai", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); IconButton(onClick = { showProfile = true }) { Icon(Icons.Default.AccountCircle, "โปรไฟล์", tint = Color.White) } } } },
            floatingActionButton = { FloatingActionButton(onClick = { draft = DraftTransaction() }) { Icon(Icons.Default.Add, "เพิ่มรายการ") } }
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SummaryCard(entries) }
                item {
                    Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                        Icon(Icons.Default.ImageSearch, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "กำลังอ่านสลิป…" else "เลือกสลิปจากเครื่อง")
                    }
                }
                item { QuickOverview(entries) }
                item { OutlinedButton(onClick = { showDebts = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CreditCard, null); Spacer(Modifier.width(8.dp)); Text("แผนปลดหนี้") } }
                item { Text("สแกนแล้ว RubJai จะสรุปยอด ร้านค้า หมวด และหมายเหตุให้อัตโนมัติ เพียงตรวจแล้วกดบันทึก", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
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
        if (showProfile) ProfileDialog(authRepository, onDismiss = { showProfile = false })
        if (showDebts) Dialog(onDismissRequest = { showDebts = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { DebtPlannerScreen(onClose = { showDebts = false }) } }
    }
}

@Composable
private fun DebtPlannerScreen(onClose: () -> Unit) {
    val context = LocalContext.current; val repository = remember { DebtRepository() }
    var debts by remember { mutableStateOf(emptyList<Debt>()) }; var create by remember { mutableStateOf(false) }; var target by remember { mutableStateOf<Debt?>(null) }; var draft by remember { mutableStateOf<DraftTransaction?>(null) }; var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { repository.observe { debts = it } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if (uri != null) TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromFilePath(context, uri)).addOnSuccessListener { draft = SlipParser.parse(it.text, "debt_slip") }.addOnFailureListener { message = "อ่านสลิปไม่สำเร็จ" } }
    Column(Modifier.fillMaxSize()) {
        Surface(color = Color(0xFF071A3D)) { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "กลับ", tint = Color.White) }; Text("แผนปลดหนี้", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); IconButton(onClick = { create = true }) { Icon(Icons.Default.Add, "เพิ่มหนี้", tint = Color.White) } } }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text("ชำระทีละเดือน แล้วดูเส้นชัยของคุณ", style = MaterialTheme.typography.titleMedium); Text("ระยะเวลาเป็นการประมาณจากยอดล่าสุดและดอกเบี้ยที่กรอก", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
            if (debts.isEmpty()) item { Card { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("ยังไม่มีรายการหนี้"); TextButton(onClick = { create = true }) { Text("+ เพิ่มหนี้ก้อนแรก") } } } }
            items(debts, key = { it.id }) { debt -> DebtCard(debt) { target = debt; picker.launch("image/*") } }
        }
    }
    if (create) CreateDebtDialog({ create = false }) { name, balance, interest -> repository.add(name, balance, interest) { message = it ?: "เพิ่มหนี้แล้ว" }; create = false }
    draft?.let { parsed -> AlertDialog(onDismissRequest = { draft = null }, title = { Text("ยืนยันตัดยอดหนี้") }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { Text(target?.name.orEmpty(), fontWeight = FontWeight.Bold); Text("ยอดจากสลิป ${parsed.amount.ifBlank { "อ่านไม่พบ" }} บาท"); Text("หมายเหตุ: ${parsed.remark.ifBlank { "ไม่มี" }}"); Text("ระบบจะกันสลิปซ้ำจากข้อมูลในสลิป") } }, confirmButton = { Button(enabled = parsed.amount.toDoubleOrNull()?.let { it > 0 } == true, onClick = { target?.let { repository.applySlip(it, parsed) { error -> message = error ?: "ตัดยอดหนี้แล้ว" } }; draft = null }) { Text("ยืนยันตัดยอด") } }, dismissButton = { TextButton(onClick = { draft = null }) { Text("ยกเลิก") } }) }
    message?.let { AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("ตกลง") } }, text = { Text(it) }) }
}

@Composable private fun DebtCard(debt: Debt, pay: () -> Unit) {
    val progress = if (debt.originalBalance > 0) (1 - debt.remainingBalance / debt.originalBalance).toFloat().coerceIn(0f, 1f) else 0f
    Card { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row { Text(debt.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(money(debt.remainingBalance), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }; LinearProgressIndicator({ progress }, Modifier.fillMaxWidth()); Text("ชำระแล้ว ${(progress * 100).toInt()}% • ล่าสุด ${money(debt.latestPayment)}"); Text(debt.estimatedMonths()?.let { if (it == 0) "ปิดหนี้แล้ว" else "คาดว่าจะหมดในประมาณ $it เดือน" } ?: "ยอดล่าสุดยังไม่พอคำนวณระยะเวลา", fontWeight = FontWeight.SemiBold); Text(debt.encouragement(), color = Color(0xFF0B9B73)); Button(onClick = pay, modifier = Modifier.fillMaxWidth()) { Text("เลือกสลิปเพื่อตัดยอด") } } }
}

@Composable private fun CreateDebtDialog(dismiss: () -> Unit, save: (String, Double, Double) -> Unit) {
    var name by remember { mutableStateOf("") }; var balance by remember { mutableStateOf("") }; var interest by remember { mutableStateOf("0") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("เพิ่มหนี้") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(name, { name = it }, label = { Text("ชื่อเจ้าหนี้/รายการ") }); OutlinedTextField(balance, { balance = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("ยอดหนี้ตั้งต้น") }); OutlinedTextField(interest, { interest = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("ดอกเบี้ยต่อปี % (ถ้าไม่มีใส่ 0)") }) } }, confirmButton = { Button(enabled = name.isNotBlank() && balance.toDoubleOrNull()?.let { it > 0 } == true, onClick = { save(name, balance.toDouble(), interest.toDoubleOrNull() ?: 0.0) }) { Text("บันทึก") } }, dismissButton = { TextButton(onClick = dismiss) { Text("ยกเลิก") } })
}

@Composable
private fun AuthScreen(repository: AuthRepository) {
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF4F7FA)) {
        Column(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Spacer(Modifier.height(48.dp)); Text("RubJai", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color(0xFF071A3D)); Text(if (creating) "สร้างบัญชีใหม่" else "เข้าสู่ระบบเพื่อซิงก์ข้อมูลของคุณ")
            Spacer(Modifier.height(24.dp))
            if (creating) OutlinedTextField(name, { name = it }, label = { Text("ชื่อที่แสดง") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(email, { email = it }, label = { Text("อีเมล") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("รหัสผ่านอย่างน้อย 6 ตัว") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
            Button(enabled = !busy && email.isNotBlank() && password.length >= 6 && (!creating || name.isNotBlank()), onClick = { busy = true; if (creating) repository.createAccount(email, password, name) { busy = false; error = it } else repository.signIn(email, password) { busy = false; error = it } }, modifier = Modifier.fillMaxWidth()) { Text(if (creating) "สมัครสมาชิก" else "เข้าสู่ระบบ") }
            TextButton(onClick = { creating = !creating; error = null }) { Text(if (creating) "มีบัญชีแล้ว? เข้าสู่ระบบ" else "ยังไม่มีบัญชี? สมัครสมาชิก") }
            Text("หลังสมัคร ระบบจะส่งลิงก์ยืนยันอีเมล ต้องยืนยันก่อนเข้าดูข้อมูลการเงิน", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun VerifyEmailScreen(repository: AuthRepository, email: String, onVerified: () -> Unit) {
    var message by remember { mutableStateOf("ส่งลิงก์ยืนยันไปที่ $email แล้ว") }; var busy by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF4F7FA)) { Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("ยืนยันอีเมล", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text(message); Spacer(Modifier.height(20.dp))
        Button(enabled = !busy, onClick = { busy = true; repository.refreshUser { verified, error -> busy = false; if (verified) onVerified() else message = error ?: "ยังไม่พบการยืนยัน กรุณากดลิงก์ในอีเมลแล้วลองอีกครั้ง" } }, modifier = Modifier.fillMaxWidth()) { Text("ยืนยันแล้ว — ตรวจสอบอีกครั้ง") }
        OutlinedButton(enabled = !busy, onClick = { repository.resendVerification { message = it ?: "ส่งลิงก์ยืนยันใหม่แล้ว" } }, modifier = Modifier.fillMaxWidth()) { Text("ส่งอีเมลยืนยันอีกครั้ง") }
        TextButton(onClick = { repository.signOut() }) { Text("กลับไปหน้าเข้าสู่ระบบ") }
    } }
}

@Composable
private fun ProfileDialog(repository: AuthRepository, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { repository.loadProfile { savedName, savedPhone -> name = savedName; phone = savedPhone } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("โปรไฟล์") }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(repository.auth.currentUser?.email ?: "บัญชีทดลอง", color = Color.Gray)
        OutlinedTextField(name, { name = it }, label = { Text("ชื่อที่แสดง") }, singleLine = true)
        OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == '-' } }, label = { Text("เบอร์โทร (ไม่บังคับ)") }, singleLine = true)
        message?.let { Text(it) }
        TextButton(onClick = { repository.signOut(); onDismiss() }) { Text("ออกจากระบบ", color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(onClick = { repository.updateProfile(name, phone) { message = it ?: "บันทึกแล้ว" } }) { Text("บันทึก") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("ปิด") } })
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

@Composable private fun EntryRow(item: MoneyTransaction) { Card { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.title.ifBlank { "ไม่ระบุรายการ" }, fontWeight = FontWeight.SemiBold); Text(item.source, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; Text((if (item.type == "INCOME") "+" else "-") + money(item.amount), color = if (item.type == "INCOME") Color(0xFF0B9B73) else Color(0xFFD84A3A), fontWeight = FontWeight.Bold) } } }

@Composable private fun QuickOverview(entries: List<MoneyTransaction>) {
    val top = entries.groupBy { it.category }.maxByOrNull { it.value.sumOf(MoneyTransaction::amount) }?.key ?: "ยังไม่มีข้อมูล"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp)) { Text("รายการเดือนนี้", color = Color.Gray); Text("${entries.size} รายการ", fontWeight = FontWeight.Bold) } }
        Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp)) { Text("หมวดหลัก", color = Color.Gray); Text(top, fontWeight = FontWeight.Bold, maxLines = 1) } }
    }
}

@Composable
private fun TransactionDialog(initial: DraftTransaction, onDismiss: () -> Unit, onSave: (DraftTransaction) -> Unit) {
    var amount by remember(initial) { mutableStateOf(initial.amount) }; var title by remember(initial) { mutableStateOf(initial.title) }; var type by remember(initial) { mutableStateOf(initial.type) }; var category by remember(initial) { mutableStateOf(initial.category) }; var remark by remember(initial) { mutableStateOf(initial.remark) }
    val categories = listOf("อาหารและเครื่องดื่ม", "เดินทาง", "ของใช้/ช้อปปิ้ง", "บิล/สาธารณูปโภค", "สุขภาพ", "เงินเดือน", "โอนเงิน", "อื่น ๆ")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.source == "slip_ocr") "สรุปจากสลิป" else "ตรวจรายการ") }, text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row { FilterChip(type == TransactionType.INCOME, { type = TransactionType.INCOME }, { Text("รายรับ") }); Spacer(Modifier.width(8.dp)); FilterChip(type == TransactionType.EXPENSE, { type = TransactionType.EXPENSE }, { Text("รายจ่าย") }) }
        OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("จำนวนเงิน") }, singleLine = true)
        OutlinedTextField(title, { title = it }, label = { Text("รายการ/ร้านค้า") }, singleLine = true)
        Text("หมวดที่แนะนำ", fontWeight = FontWeight.SemiBold)
        categories.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { value -> FilterChip(category == value, { category = value }, { Text(value) }) } } }
        OutlinedTextField(remark, { remark = it }, label = { Text("Remark / หมายเหตุ") }, minLines = 2)
    } }, confirmButton = { Button(enabled = amount.toDoubleOrNull()?.let { it > 0 } == true, onClick = { onSave(initial.copy(amount = amount, title = title, type = type, category = category, remark = remark)) }) { Text("ยืนยันและบันทึก") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } })
}

private fun sharedDraft(intent: Intent): DraftTransaction? {
    if (intent.action != Intent.ACTION_SEND) return null
    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
    return SlipParser.parse(text, "line_share")
}

private fun money(value: Double) = NumberFormat.getCurrencyInstance(Locale("th", "TH")).format(value)
