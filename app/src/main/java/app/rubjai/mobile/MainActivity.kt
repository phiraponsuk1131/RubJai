package app.rubjai.mobile

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

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
    val occurredAt: String = "",
)

enum class TransactionType { INCOME, EXPENSE }
private enum class EntryPeriod(val label: String) { TODAY("วันนี้"), WEEK("สัปดาห์นี้"), MONTH("เดือนนี้"), ALL("ทั้งหมด") }
private enum class EntryKind(val label: String) { ALL("ทั้งหมด"), INCOME("รายรับ"), EXPENSE("รายจ่าย") }

@Composable
fun RubJaiApp(repository: TransactionRepository, launchIntent: Intent) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository() }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(authRepository.auth.currentUser) }
    var verificationPassed by remember(currentUser?.uid) { mutableStateOf(currentUser?.isEmailVerified == true) }
    DisposableEffect(authRepository) {
        val listener = FirebaseAuth.AuthStateListener {
            currentUser = it.currentUser
            verificationPassed = it.currentUser?.isEmailVerified == true
        }
        authRepository.auth.addAuthStateListener(listener)
        onDispose { authRepository.auth.removeAuthStateListener(listener) }
    }
    val colors = lightColorScheme(primary = Color(0xFF0B9B73), secondary = Color(0xFFFF6B57), background = Color(0xFFF4F7FA))
    if (currentUser == null) {
        MaterialTheme(colorScheme = colors) { AuthScreen(authRepository) }
        return
    }
    if (!verificationPassed) {
        MaterialTheme(colorScheme = colors) { VerifyEmailScreen(authRepository, currentUser?.email.orEmpty()) { currentUser = authRepository.auth.currentUser; verificationPassed = true } }
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
    var entryPeriod by remember { mutableStateOf(EntryPeriod.MONTH) }
    var entryKind by remember { mutableStateOf(EntryKind.ALL) }
    var autoScanEnabled by remember { mutableStateOf(AutoSlipScheduler.enabled(context)) }
    var pendingSlips by remember { mutableStateOf(PendingSlipStore.load(context)) }
    var showPending by remember { mutableStateOf(false) }
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
                .addOnSuccessListener { result ->
                    val parsed = SlipParser.parse(result.text, "slip_ocr", imageDate(context, uri))
                    if (parsed.amount.toDoubleOrNull()?.let { it > 0 } == true) draft = parsed else message = "อ่านยอดจากรูปไม่พบ กรุณาเลือกภาพสลิปที่ชัดเจน"
                    busy = false
                }
                .addOnFailureListener { error -> message = "อ่านสลิปไม่สำเร็จ: ${error.localizedMessage}"; busy = false }
        }
    }
    val scanPermission = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        autoScanEnabled = granted
        AutoSlipScheduler.setEnabled(context, granted)
        if (!granted) message = "ต้องอนุญาตการเข้าถึงรูปจึงจะตรวจสลิป K PLUS อัตโนมัติได้"
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = { Surface(color = Color(0xFF071A3D)) { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text("RubJai", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); IconButton(onClick = { showProfile = true }) { Icon(Icons.Default.AccountCircle, "โปรไฟล์", tint = Color.White) } } } },
            floatingActionButton = { FloatingActionButton(onClick = { draft = DraftTransaction(type = TransactionType.INCOME, source = "manual_income", category = "รายรับ") }) { Icon(Icons.Default.Add, "เพิ่มรายรับ") } }
        ) { padding ->
            val visibleEntries = remember(entries, entryPeriod, entryKind) { entries.filterFor(entryPeriod, entryKind) }
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SummaryCard(entries) }
                item {
                    Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                        Icon(Icons.Default.ImageSearch, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "กำลังอ่านสลิป…" else "เลือกสลิปจากเครื่อง")
                    }
                }
                item { AutoScanCard(autoScanEnabled, pendingSlips.size, onToggle = { enable -> if (!enable) { AutoSlipScheduler.setEnabled(context, false); autoScanEnabled = false } else if (ContextCompat.checkSelfPermission(context, scanPermission) == PackageManager.PERMISSION_GRANTED) { AutoSlipScheduler.setEnabled(context, true); autoScanEnabled = true } else permissionLauncher.launch(scanPermission) }, onReview = { pendingSlips = PendingSlipStore.load(context); showPending = true }) }
                item { QuickOverview(entries) }
                item { OutlinedButton(onClick = { showDebts = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CreditCard, null); Spacer(Modifier.width(8.dp)); Text("แผนปลดหนี้") } }
                item { Text("รายการของคุณ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); EntryFilters(entryPeriod, entryKind, { entryPeriod = it }, { entryKind = it }) }
                if (entryPeriod != EntryPeriod.ALL) item { SpendingOverview(entries.filterFor(entryPeriod, EntryKind.EXPENSE), entryPeriod) }
                if (visibleEntries.isEmpty()) item { Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("ยังไม่มีรายการในช่วงนี้", fontWeight = FontWeight.SemiBold); Text("เพิ่มรายรับด้วยปุ่ม + หรือเลือกสลิปรายจ่าย", color = Color.Gray, style = MaterialTheme.typography.bodySmall) } } }
                items(visibleEntries, key = { it.id }) { EntryRow(it) }
            }
        }
        draft?.let { current -> TransactionDialog(current, onDismiss = { draft = null }, onSave = { busy = true; repository.add(it, onDone = { error -> busy = false; message = error ?: "บันทึกแล้ว" }); draft = null }) }
        message?.let { AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("ตกลง") } }, text = { Text(it) }) }
        if (showPending) PendingSlipDialog(pendingSlips, onClose = { showPending = false }, onApprove = { pending -> repository.add(pending.draft) { error -> if (error == null) { PendingSlipStore.remove(context, pending.id); pendingSlips = PendingSlipStore.load(context); message = "บันทึกรายจ่ายแล้ว" } else message = error } }, onReject = { pending -> PendingSlipStore.remove(context, pending.id); pendingSlips = PendingSlipStore.load(context); if (pendingSlips.isEmpty()) showPending = false })
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
    var debts by remember { mutableStateOf(emptyList<Debt>()) }; var create by remember { mutableStateOf(false) }; var selected by remember { mutableStateOf<Debt?>(null) }; var target by remember { mutableStateOf<Debt?>(null) }; var draft by remember { mutableStateOf<DraftTransaction?>(null) }; var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { repository.observe { updated -> debts = updated; selected = selected?.let { current -> updated.firstOrNull { it.id == current.id } } } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if (uri != null) TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromFilePath(context, uri)).addOnSuccessListener { result -> val parsed = SlipParser.parse(result.text, "debt_slip", imageDate(context, uri)); if (parsed.amount.toDoubleOrNull()?.let { it > 0 } == true) draft = parsed else message = "อ่านยอดจากสลิปไม่พบ กรุณาเลือกสลิปที่ชัดเจน" }.addOnFailureListener { message = "อ่านสลิปไม่สำเร็จ" } }
    Column(Modifier.fillMaxSize()) {
        Surface(color = Color(0xFF071A3D)) { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { if (selected != null) selected = null else onClose() }) { Icon(Icons.Default.ArrowBack, "กลับ", tint = Color.White) }; Text(selected?.name ?: "รายการหนี้", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); if (selected == null) IconButton(onClick = { create = true }) { Icon(Icons.Default.Add, "เพิ่มหนี้", tint = Color.White) } } }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (selected == null) {
                item { Text("เลือกหนี้เพื่อดูรายละเอียดและประวัติชำระ", style = MaterialTheme.typography.titleMedium); Text("รองรับหนี้หลายก้อนและแยกประวัติของแต่ละรายการ", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
                if (debts.isEmpty()) item { Card { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("ยังไม่มีรายการหนี้"); TextButton(onClick = { create = true }) { Text("+ เพิ่มหนี้ก้อนแรก") } } } }
                items(debts, key = { it.id }) { DebtListCard(it) { selected = it } }
            } else item { DebtDetailCard(selected!!, repository) { target = selected; picker.launch("image/*") } }
        }
    }
    if (create) CreateDebtDialog({ create = false }) { name, balance, interest -> repository.add(name, balance, interest) { message = it ?: "เพิ่มหนี้แล้ว" }; create = false }
    draft?.let { parsed -> AlertDialog(onDismissRequest = { draft = null }, title = { Text("ยืนยันตัดยอดหนี้") }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { Text(target?.name.orEmpty(), fontWeight = FontWeight.Bold); Text("ร้าน/ผู้รับ: ${parsed.title.ifBlank { "ไม่ระบุ" }}"); Text("ยอด ${parsed.amount} บาท", fontWeight = FontWeight.Bold); if (parsed.occurredAt.isNotBlank()) Text("เวลา: ${parsed.occurredAt}"); Text("ระบบจะกันสลิปซ้ำจากข้อมูลในสลิป") } }, confirmButton = { Button(onClick = { target?.let { repository.applySlip(it, parsed) { error -> message = error ?: "ตัดยอดหนี้แล้ว" } }; draft = null }) { Text("ยืนยันตัดยอด") } }, dismissButton = { TextButton(onClick = { draft = null }) { Text("ยกเลิก") } }) }
    message?.let { AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("ตกลง") } }, text = { Text(it) }) }
}

@Composable private fun DebtListCard(debt: Debt, open: () -> Unit) {
    val progress = if (debt.originalBalance > 0) (1 - debt.remainingBalance / debt.originalBalance).toFloat().coerceIn(0f, 1f) else 0f
    Card(Modifier.fillMaxWidth().clickable(onClick = open), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row { Text(debt.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(money(debt.remainingBalance), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }; LinearProgressIndicator({ progress }, Modifier.fillMaxWidth()); Text("ชำระแล้ว ${debt.paymentsMade} ครั้ง • แตะเพื่อดูรายละเอียด", color = Color.Gray, style = MaterialTheme.typography.bodySmall) } }
}

@Composable private fun DebtDetailCard(debt: Debt, repository: DebtRepository, pay: () -> Unit) {
    val progress = if (debt.originalBalance > 0) (1 - debt.remainingBalance / debt.originalBalance).toFloat().coerceIn(0f, 1f) else 0f
    var payments by remember(debt.id) { mutableStateOf(emptyList<DebtPayment>()) }
    DisposableEffect(debt.id) { val registration = repository.observePayments(debt.id) { payments = it }; onDispose { registration.remove() } }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row { Text(debt.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(money(debt.remainingBalance), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }; LinearProgressIndicator({ progress }, Modifier.fillMaxWidth()); Text("ชำระแล้ว ${(progress * 100).toInt()}% • ล่าสุด ${money(debt.latestPayment)}"); Text(debt.estimatedMonths()?.let { if (it == 0) "ปิดหนี้แล้ว" else "คาดว่าจะหมดในประมาณ $it เดือน" } ?: "ยอดล่าสุดยังไม่พอคำนวณระยะเวลา", fontWeight = FontWeight.SemiBold); Text(debt.encouragement(), color = Color(0xFF0B9B73)); Button(onClick = pay, modifier = Modifier.fillMaxWidth()) { Text("เลือกสลิปเพื่อตัดยอด") }; if (payments.isNotEmpty()) { HorizontalDivider(); Text("ประวัติชำระ", fontWeight = FontWeight.Bold); payments.forEach { DebtPaymentRow(it) } } } }
}

@Composable private fun DebtPaymentRow(payment: DebtPayment) {
    val savedTime = payment.paidAt?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("th", "TH")).format(it) }.orEmpty()
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(payment.merchant.ifBlank { "ชำระหนี้" }, fontWeight = FontWeight.SemiBold); Text(payment.occurredAt.ifBlank { savedTime }.ifBlank { "ไม่พบวันที่/เวลา" }, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; Text("-${money(payment.amount)}", color = Color(0xFFD84A3A), fontWeight = FontWeight.Bold) }
}

@Composable private fun CreateDebtDialog(dismiss: () -> Unit, save: (String, Double, Double) -> Unit) {
    var name by remember { mutableStateOf("") }; var balance by remember { mutableStateOf("") }; var interest by remember { mutableStateOf("0") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("เพิ่มหนี้") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(name, { name = it }, label = { Text("ชื่อเจ้าหนี้/รายการ") }); OutlinedTextField(balance, { balance = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("ยอดหนี้ตั้งต้น") }); OutlinedTextField(interest, { interest = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("ดอกเบี้ยต่อปี % (ถ้าไม่มีใส่ 0)") }) } }, confirmButton = { Button(enabled = name.isNotBlank() && balance.toDoubleOrNull()?.let { it > 0 } == true, onClick = { save(name, balance.toDouble(), interest.toDoubleOrNull() ?: 0.0) }) { Text("บันทึก") } }, dismissButton = { TextButton(onClick = dismiss) { Text("ยกเลิก") } })
}

@Composable
private fun AuthScreen(repository: AuthRepository) {
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var passwordVisible by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF4F7FA)) {
        Column(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Spacer(Modifier.height(48.dp)); Text("RubJai", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color(0xFF071A3D)); Text(if (creating) "สร้างบัญชีใหม่" else "เข้าสู่ระบบเพื่อซิงก์ข้อมูลของคุณ")
            Spacer(Modifier.height(24.dp))
            if (creating) OutlinedTextField(name, { name = it }, label = { Text("ชื่อที่แสดง") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(email, { email = it }, label = { Text("อีเมล") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("รหัสผ่านอย่างน้อย 6 ตัว") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (passwordVisible) "ซ่อนรหัสผ่าน" else "แสดงรหัสผ่าน") } })
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
    LaunchedEffect(email) {
        while (true) {
            delay(2_500)
            repository.refreshUser { verified, _ -> if (verified) onVerified() }
        }
    }
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF4F7FA)) { Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("ยืนยันอีเมล", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text(message); Spacer(Modifier.height(20.dp)); CircularProgressIndicator(); Spacer(Modifier.height(8.dp)); Text("กำลังตรวจสอบอัตโนมัติ…", color = Color.Gray)
        OutlinedButton(enabled = !busy, onClick = { repository.resendVerification { message = it ?: "ส่งลิงก์ยืนยันใหม่แล้ว" } }, modifier = Modifier.fillMaxWidth()) { Text("ส่งอีเมลยืนยันอีกครั้ง") }
        TextButton(onClick = { repository.signOut() }) { Text("กลับไปหน้าเข้าสู่ระบบ") }
    } }
}

@Composable
private fun ProfileDialog(repository: AuthRepository, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var message by remember { mutableStateOf<String?>(null) }; var confirmDelete by remember { mutableStateOf(false) }; var deleting by remember { mutableStateOf(false) }; var isAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { repository.loadProfile { savedName, savedPhone -> name = savedName; phone = savedPhone }; repository.checkAdmin { isAdmin = it } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("โปรไฟล์") }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(repository.auth.currentUser?.email ?: "บัญชีทดลอง", color = Color.Gray)
        OutlinedTextField(name, { name = it }, label = { Text("ชื่อที่แสดง") }, singleLine = true)
        OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == '-' } }, label = { Text("เบอร์โทร (ไม่บังคับ)") }, singleLine = true)
        message?.let { Text(it) }
        TextButton(onClick = { repository.signOut(); onDismiss() }) { Text("ออกจากระบบ", color = MaterialTheme.colorScheme.error) }
        if (isAdmin) TextButton(onClick = { confirmDelete = true }) { Text("ลบบัญชีและข้อมูลของฉัน", color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(onClick = { repository.updateProfile(name, phone) { message = it ?: "บันทึกแล้ว" } }) { Text("บันทึก") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("ปิด") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!deleting) confirmDelete = false }, title = { Text("ลบบัญชีถาวร?") }, text = { Text("รายการรายรับรายจ่าย หนี้ สลิปชำระ โปรไฟล์ และบัญชีเข้าสู่ระบบจะถูกลบทั้งหมดและกู้คืนไม่ได้") }, confirmButton = { Button(enabled = !deleting, onClick = { deleting = true; repository.deleteAccountAndData { error -> deleting = false; if (error == null) onDismiss() else message = error; confirmDelete = false } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(if (deleting) "กำลังลบ…" else "ลบถาวร") } }, dismissButton = { TextButton(enabled = !deleting, onClick = { confirmDelete = false }) { Text("ยกเลิก") } })
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

@Composable private fun AutoScanCard(enabled: Boolean, pendingCount: Int, onToggle: (Boolean) -> Unit, onReview: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F7F2))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("ตรวจสลิป K PLUS รายวัน", fontWeight = FontWeight.Bold); Text(if (enabled) "ตรวจเฉพาะรูปใหม่ ผลจะรอคุณอนุมัติ" else "ปิดอยู่ รูปจะไม่ถูกตรวจอัตโนมัติ", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; Switch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.semantics { contentDescription = "เปิดหรือปิดการตรวจสลิป K PLUS รายวัน" }) }
            if (pendingCount > 0) Button(onClick = onReview, modifier = Modifier.fillMaxWidth()) { Text("ตรวจรายการรออนุมัติ $pendingCount รายการ") }
        }
    }
}

@Composable private fun PendingSlipDialog(items: List<PendingSlip>, onClose: () -> Unit, onApprove: (PendingSlip) -> Unit, onReject: (PendingSlip) -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("รายการ K PLUS รออนุมัติ") }, text = {
        if (items.isEmpty()) Text("ไม่มีรายการรอตรวจ") else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { pending ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F7FA))) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(pending.draft.title.ifBlank { "ไม่พบชื่อร้าน" }, fontWeight = FontWeight.Bold)
                        Text("${pending.draft.amount} บาท", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(listOf(pending.draft.occurredAt, pending.draft.category).filter(String::isNotBlank).joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onReject(pending) }) { Text("ลบออก") }
                            Button(onClick = { onApprove(pending) }) { Text("ยืนยันและเก็บ") }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("ปิด") } })
}

private fun List<MoneyTransaction>.filterFor(period: EntryPeriod, kind: EntryKind): List<MoneyTransaction> {
    val calendar = Calendar.getInstance()
    val threshold = when (period) {
        EntryPeriod.ALL -> null
        EntryPeriod.TODAY -> calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time
        EntryPeriod.WEEK -> calendar.apply { add(Calendar.DAY_OF_YEAR, -6); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time
        EntryPeriod.MONTH -> calendar.apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time
    }
    return filter { item -> (threshold == null || item.createdAt?.let { !it.before(threshold) } == true) && when (kind) { EntryKind.ALL -> true; EntryKind.INCOME -> item.type == "INCOME"; EntryKind.EXPENSE -> item.type == "EXPENSE" } }
}

@Composable private fun EntryFilters(period: EntryPeriod, kind: EntryKind, setPeriod: (EntryPeriod) -> Unit, setKind: (EntryKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { EntryPeriod.entries.forEach { FilterChip(selected = period == it, onClick = { setPeriod(it) }, label = { Text(it.label) }) } }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { EntryKind.entries.forEach { FilterChip(selected = kind == it, onClick = { setKind(it) }, label = { Text(it.label) }) } }
    }
}

@Composable private fun SpendingOverview(entries: List<MoneyTransaction>, period: EntryPeriod) {
    val groups = entries.groupBy { it.category.ifBlank { "อื่น ๆ" } }.mapValues { it.value.sumOf(MoneyTransaction::amount) }.entries.sortedByDescending { it.value }
    val total = groups.sumOf { it.value }
    val colors = listOf(Color(0xFF0B9B73), Color(0xFFFF8A65), Color(0xFF5C6BC0), Color(0xFFFFC107), Color(0xFF26A69A), Color(0xFFAB47BC))
    val description = if (total > 0) "ภาพรวมรายจ่าย${period.label} รวม ${money(total)} จำนวน ${groups.size} หมวด" else "ยังไม่มีรายจ่าย${period.label}"
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(18.dp).semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ภาพรวมรายจ่าย • ${period.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (total <= 0) Text("ยังไม่มีรายจ่ายในช่วงนี้", color = Color.Gray)
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Canvas(Modifier.size(128.dp)) {
                    drawArc(Color(0xFFE8EEF3), -90f, 360f, false, style = Stroke(22.dp.toPx()))
                    var start = -90f
                    groups.forEachIndexed { index, group -> val sweep = (group.value / total * 360).toFloat(); drawArc(colors[index % colors.size], start, sweep, false, style = Stroke(22.dp.toPx())); start += sweep }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(money(total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); groups.take(5).forEachIndexed { index, group -> Row(verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(12.dp), shape = RoundedCornerShape(50), color = colors[index % colors.size]) {}; Spacer(Modifier.width(8.dp)); Text(group.key, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text(money(group.value), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) } } }
            }
        }
    }
}

@Composable private fun EntryRow(item: MoneyTransaction) { Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.title.ifBlank { "ไม่ระบุรายการ" }, fontWeight = FontWeight.SemiBold); Text(listOf(item.category, item.occurredAt).filter(String::isNotBlank).joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; Text((if (item.type == "INCOME") "+" else "-") + money(item.amount), color = if (item.type == "INCOME") Color(0xFF0B9B73) else Color(0xFFD84A3A), fontWeight = FontWeight.Bold) } } }

@Composable private fun QuickOverview(entries: List<MoneyTransaction>) {
    val top = entries.groupBy { it.category }.maxByOrNull { it.value.sumOf(MoneyTransaction::amount) }?.key ?: "ยังไม่มีข้อมูล"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp)) { Text("รายการเดือนนี้", color = Color.Gray); Text("${entries.size} รายการ", fontWeight = FontWeight.Bold) } }
        Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp)) { Text("หมวดหลัก", color = Color.Gray); Text(top, fontWeight = FontWeight.Bold, maxLines = 1) } }
    }
}

@Composable
private fun TransactionDialog(initial: DraftTransaction, onDismiss: () -> Unit, onSave: (DraftTransaction) -> Unit) {
    var amount by remember(initial) { mutableStateOf(initial.amount) }; var title by remember(initial) { mutableStateOf(initial.title) }
    val manualIncome = initial.source == "manual_income"
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (manualIncome) "เพิ่มรายรับ" else "สรุปจากสลิป") }, text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (manualIncome) {
            OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("จำนวนเงินรายรับ") }, singleLine = true)
            OutlinedTextField(title, { title = it }, label = { Text("ชื่อรายรับ เช่น เงินเดือน") }, singleLine = true)
        } else {
            Text(title.ifBlank { "ไม่พบชื่อร้าน" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${amount} บาท", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            if (initial.occurredAt.isNotBlank()) Text("วันที่/เวลา ${initial.occurredAt}")
            Text("หมวด ${initial.category}")
            if (initial.remark.isNotBlank()) Text("หมายเหตุ ${initial.remark}")
            Text("ข้อมูลอ่านจากรูปอัตโนมัติ หากไม่ถูกต้องให้ยกเลิกและเลือกสลิปที่ชัดกว่า", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    } }, confirmButton = { Button(enabled = amount.toDoubleOrNull()?.let { it > 0 } == true, onClick = { onSave(initial.copy(amount = amount, title = title, type = if (manualIncome) TransactionType.INCOME else TransactionType.EXPENSE, category = if (manualIncome && title.contains("เงินเดือน", true)) "เงินเดือน" else initial.category)) }) { Text(if (manualIncome) "บันทึกรายรับ" else "บันทึกรายจ่าย") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } })
}

private fun sharedDraft(intent: Intent): DraftTransaction? {
    if (intent.action != Intent.ACTION_SEND) return null
    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
    return SlipParser.parse(text, "line_share")
}

private fun imageDate(context: Context, uri: Uri): Date? = runCatching {
    context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val taken = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN).takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L
        val added = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED).takeIf { it >= 0 }?.let(cursor::getLong)?.times(1000) ?: 0L
        maxOf(taken, added).takeIf { it > 0 }?.let(::Date)
    }
}.getOrNull()

private fun money(value: Double) = NumberFormat.getCurrencyInstance(Locale("th", "TH")).format(value)
