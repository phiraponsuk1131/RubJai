package app.rubjai.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import com.google.firebase.auth.PhoneAuthCredential
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

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
    val category: String = "ใช้จ่ายทั่วไป",
    val remark: String = "",
    val occurredAt: String = "",
    val slipFingerprint: String = "",
    val slipUri: String = "",
)

enum class TransactionType { INCOME, EXPENSE }
private enum class EntryPeriod(val label: String) { TODAY("วันนี้"), WEEK("สัปดาห์นี้"), MONTH("เดือนนี้"), ALL("ทั้งหมด") }
private enum class EntryKind(val label: String) { ALL("ทั้งหมด"), INCOME("รายรับ"), EXPENSE("รายจ่าย") }

private val RubInk = Color(0xFF082F36)
private val RubPanel = Color(0xFF124A50)
private val RubCream = Color(0xFFFFF7E8)
private val RubCoral = Color(0xFFFF8A72)
private val RubMint = Color(0xFF72DCC0)
private val RubPlum = Color(0xFF765C80)
private val RubMuted = Color(0xFFBBD5D1)

@Composable
fun RubJaiApp(repository: TransactionRepository, launchIntent: Intent) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository() }
    val setup = remember { context.getSharedPreferences("rubjai_setup", Context.MODE_PRIVATE) }
    var onboardingDone by remember { mutableStateOf(setup.getBoolean("onboarding_v201", false)) }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(authRepository.auth.currentUser) }
    var verificationPassed by remember(currentUser?.uid) { mutableStateOf(currentUser?.phoneNumber != null || currentUser?.isEmailVerified == true) }
    DisposableEffect(authRepository) {
        val listener = FirebaseAuth.AuthStateListener {
            currentUser = it.currentUser
            verificationPassed = it.currentUser?.phoneNumber != null || it.currentUser?.isEmailVerified == true
        }
        authRepository.auth.addAuthStateListener(listener)
        onDispose { authRepository.auth.removeAuthStateListener(listener) }
    }
    val colors = darkColorScheme(
        primary = RubMint,
        onPrimary = RubInk,
        secondary = RubCoral,
        onSecondary = RubInk,
        background = RubInk,
        surface = RubPanel,
        onSurface = RubCream,
    )
    if (!onboardingDone) {
        MaterialTheme(colorScheme = colors) { OnboardingScreen { setup.edit().putBoolean("onboarding_v201", true).apply(); onboardingDone = true } }
        return
    }
    if (currentUser == null) {
        MaterialTheme(colorScheme = colors) { AuthScreen(authRepository) }
        return
    }
    if (currentUser?.email != null && !verificationPassed) {
        MaterialTheme(colorScheme = colors) { VerifyEmailScreen(authRepository, currentUser?.email.orEmpty()) { currentUser = authRepository.auth.currentUser; verificationPassed = true } }
        return
    }
    var migrationReady by remember(currentUser?.uid) { mutableStateOf(false) }
    var migrationError by remember(currentUser?.uid) { mutableStateOf<String?>(null) }
    LaunchedEffect(currentUser?.uid) {
        migrationReady = false
        authRepository.ensureCleanStartV2 { error ->
            migrationError = error
            if (error == null) {
                PendingSlipStore.clearUsage(context)
                LocalSlipLinkStore.clear(context)
                CategoryStore.clear(context)
                migrationReady = true
            }
        }
    }
    if (!migrationReady) {
        MaterialTheme(colorScheme = colors) {
            Box(Modifier.fillMaxSize().background(RubInk), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    androidx.compose.foundation.Image(painterResource(R.drawable.rubjai_mascot), null, Modifier.size(132.dp))
                    if (migrationError == null) { CircularProgressIndicator(color = RubMint); Text("กำลังเตรียมน้องรับจ่าย 2.0", color = RubCream) }
                    else { Text(migrationError.orEmpty(), color = RubCoral); Button(onClick = { migrationError = null; authRepository.ensureCleanStartV2 { error -> migrationError = error; migrationReady = error == null } }) { Text("ลองอีกครั้ง") } }
                }
            }
        }
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
    var syncConsent by remember { mutableStateOf(KPlusSyncManager.hasConsent(context)) }
    var showSyncConsent by remember { mutableStateOf(false) }
    var syncWorkId by remember { mutableStateOf<UUID?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var syncScanned by remember { mutableIntStateOf(0) }
    var pendingSlips by remember { mutableStateOf(PendingSlipStore.load(context)) }
    var showPending by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<MoneyTransaction?>(null) }
    var mainTab by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repository.observe { entries = it }
        draft = sharedDraft(launchIntent)
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        updateManager.checkForUpdate(version) { availableUpdate = it }
    }
    LaunchedEffect(syncWorkId) {
        val workId = syncWorkId ?: return@LaunchedEffect
        while (true) {
            val info = withContext(Dispatchers.IO) { runCatching { KPlusSyncManager.workInfo(context, workId) }.getOrNull() }
            when (info?.state) {
                androidx.work.WorkInfo.State.RUNNING -> { syncing = true; syncScanned = info.progress.getInt("scanned", 0) }
                androidx.work.WorkInfo.State.SUCCEEDED -> { syncing = false; pendingSlips = PendingSlipStore.load(context); val found = info.outputData.getInt("found", 0); message = if (found > 0) "สแกนเสร็จ พบ $found รายการ กรุณาตรวจและอนุมัติ" else "สแกนเสร็จ ไม่พบสลิปธนาคารหรือวอลเล็ตใหม่ของวันนี้"; syncWorkId = null; break }
                androidx.work.WorkInfo.State.FAILED, androidx.work.WorkInfo.State.CANCELLED -> { syncing = false; message = "ซิงไม่สำเร็จ กรุณาตรวจสิทธิ์รูปภาพแล้วลองใหม่"; syncWorkId = null; break }
                else -> syncing = true
            }
            delay(500)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromFilePath(context, uri))
                .addOnSuccessListener { result ->
                    val parsed = SlipParser.parse(result.text, "slip_ocr", imageDate(context, uri))
                    if (parsed.amount.toDoubleOrNull()?.let { it > 0 } == true) draft = parsed.copy(slipUri = uri.toString()) else message = "อ่านยอดจากรูปไม่พบ กรุณาเลือกภาพสลิปที่ชัดเจน"
                    busy = false
                    recognizer.close()
                }
                .addOnFailureListener { error -> message = "อ่านสลิปไม่สำเร็จ: ${error.localizedMessage}"; busy = false; recognizer.close() }
        }
    }
    val scanPermission = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    val startKPlusSync = { syncing = true; syncScanned = 0; syncWorkId = KPlusSyncManager.syncNow(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startKPlusSync()
        else message = "ต้องอนุญาตการเข้าถึงรูปจึงจะสแกนสลิปธนาคารและวอลเล็ตของวันนี้ได้"
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = RubInk,
            topBar = { if (mainTab == 0) RubJaiTopBar(authRepository.auth.currentUser?.displayName.orEmpty()) { mainTab = 1 } },
            bottomBar = {
                NavigationBar(containerColor = RubCream) {
                    NavigationBarItem(mainTab == 0, { mainTab = 0 }, { Icon(Icons.Default.Home, null) }, label = { Text("หน้าหลัก") })
                    NavigationBarItem(mainTab == 1, { mainTab = 1 }, { Icon(Icons.Default.AccountCircle, null) }, label = { Text("บัญชีของฉัน") })
                }
            },
            floatingActionButton = { if (mainTab == 0) FloatingActionButton(containerColor = Color(0xFFF27D6B), onClick = { draft = DraftTransaction(type = TransactionType.INCOME, source = "manual_income", category = "รายรับ") }) { Icon(Icons.Default.Add, "เพิ่มรายรับ", tint = Color.White) } }
        ) { padding ->
            val visibleEntries = remember(entries, entryPeriod, entryKind) { entries.filterFor(entryPeriod, entryKind) }
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (mainTab == 0) {
                    item { SummaryCard(entries) }
                    item { HomeActions(busy, syncing, pendingSlips.size, { imagePicker.launch(arrayOf("image/*")) }, { if (!syncConsent) showSyncConsent = true else if (ContextCompat.checkSelfPermission(context, scanPermission) == PackageManager.PERMISSION_GRANTED) startKPlusSync() else permissionLauncher.launch(scanPermission) }, { pendingSlips = PendingSlipStore.load(context); showPending = true }, { draft = DraftTransaction(type = TransactionType.INCOME, source = "manual_income", category = "รายรับ") }) }
                    item { KPlusSyncStatus(syncing, syncScanned, syncConsent) { KPlusSyncManager.revoke(context); syncConsent = false } }
                    item { Text("รายการของคุณ", style = MaterialTheme.typography.titleLarge, color = RubCream, fontWeight = FontWeight.Bold); EntryFilters(entryPeriod, entryKind, { entryPeriod = it }, { entryKind = it }) }
                    item { SpendingOverview(entries.filterFor(entryPeriod, EntryKind.EXPENSE), entryPeriod) }
                    if (visibleEntries.isEmpty()) item { Card(colors = CardDefaults.cardColors(containerColor = RubPanel)) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("ยังไม่มีรายการในช่วงนี้", color = RubCream, fontWeight = FontWeight.SemiBold); Text("เพิ่มรายรับหรือเลือกสลิปรายจ่าย", color = RubMuted, style = MaterialTheme.typography.bodySmall) } } }
                    items(visibleEntries, key = { it.id }) { EntryRow(it) { selectedEntry = it } }
                } else item { UserHub(authRepository.auth.currentUser?.displayName.orEmpty(), entries.size, { showProfile = true }, { showDebts = true }, { mainTab = 0 }, { showSyncConsent = true }) }
            }
        }
        draft?.let { current -> TransactionDialog(current, onDismiss = { draft = null }, onSave = { saved -> busy = true; repository.add(saved, onDone = { error -> busy = false; if (error == null && saved.slipUri.isNotBlank()) LocalSlipLinkStore.put(context, TransactionRepository.documentIdFor(saved), saved.slipUri); message = error ?: "บันทึกแล้ว" }); draft = null }) }
        message?.let { AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("ตกลง") } }, text = { Text(it) }) }
        if (showSyncConsent) AlertDialog(onDismissRequest = { showSyncConsent = false }, title = { Text("ยินยอมสแกนสลิปวันนี้?") }, text = { Text("RubJai จะอ่านรูปที่เพิ่มในวันนี้เพื่อหาสลิปธนาคารและวอลเล็ตด้วย OCR บนเครื่อง ผลจะอยู่ในคิวรออนุมัติและยังไม่บันทึกจนกว่าคุณจะยืนยัน") }, confirmButton = { Button(onClick = { KPlusSyncManager.setConsent(context, true); syncConsent = true; showSyncConsent = false; if (ContextCompat.checkSelfPermission(context, scanPermission) == PackageManager.PERMISSION_GRANTED) startKPlusSync() else permissionLauncher.launch(scanPermission) }) { Text("ยินยอมและสแกน") } }, dismissButton = { TextButton(onClick = { showSyncConsent = false }) { Text("ยังไม่ยินยอม") } })
        if (showPending) PendingSlipDialog(pendingSlips, onClose = { showPending = false }, onApprove = { pending -> repository.add(pending.draft) { error -> if (error == null) { if (pending.draft.slipUri.isNotBlank()) LocalSlipLinkStore.put(context, TransactionRepository.documentIdFor(pending.draft), pending.draft.slipUri); PendingSlipStore.remove(context, pending.id); pendingSlips = PendingSlipStore.load(context); message = "บันทึกรายจ่ายแล้ว" } else message = error } }, onReject = { pending -> PendingSlipStore.remove(context, pending.id); pendingSlips = PendingSlipStore.load(context); if (pendingSlips.isEmpty()) showPending = false })
        selectedEntry?.let { item -> TransactionDetailDialog(item, onDismiss = { selectedEntry = null }, onUpdate = { draftValue -> repository.update(item, draftValue) { error -> message = error ?: "แก้ไขแล้ว"; if (error == null) selectedEntry = null } }, onDelete = { repository.delete(item) { error -> message = error ?: "ลบรายการแล้ว"; if (error == null) { LocalSlipLinkStore.remove(context, item.id); selectedEntry = null } } }) }
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
private fun OnboardingScreen(done: () -> Unit) {
    val context = LocalContext.current
    val photoPermission = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    var page by remember { mutableIntStateOf(0) }
    var termsAccepted by remember { mutableStateOf(false) }
    var privacyAccepted by remember { mutableStateOf(false) }
    var photoGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, photoPermission) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { photoGranted = it }
    val pages = listOf(
        Triple("สวัสดี เราคือน้องรับจ่าย", "เริ่มดูแลเงินทุกวันให้เข้าใจง่ายขึ้น โดยไม่ต้องจำตัวเลขทั้งหมดเอง", "ทำความรู้จักกัน"),
        Triple("อ่านสลิป แล้วสร้างรายการให้", "รองรับสลิปธนาคารและวอลเล็ตหลายรูปแบบ พร้อมกันสลิปซ้ำก่อนบันทึก", "ต่อไป"),
        Triple("เห็นชัดว่าเงินไปทางไหน", "ดูรายรับ รายจ่าย และภาพรวมตามช่วงเวลา แล้วเลือกหมวดให้ตรงกับชีวิตของคุณ", "ต่อไป"),
        Triple("ข้อตกลงที่อ่านเข้าใจได้", "น้องรับจ่ายช่วยอ่านข้อความจากสลิป ผลอาจคลาดเคลื่อนได้ คุณจึงตรวจและแก้ไขข้อมูลก่อนบันทึกได้เสมอ", "ยอมรับและต่อไป"),
        Triple("ข้อมูลของคุณเป็นของคุณ", "รูปถูกอ่านบนเครื่องและไม่อัปโหลดไปเก็บใน Firebase เราเก็บเฉพาะข้อมูลรายการที่คุณยืนยัน", "ยอมรับและต่อไป"),
        Triple("อนุญาตอ่านรูปเมื่อคุณสั่ง", "สิทธิ์รูปใช้ตอนเลือกสลิปหรือค้นหารูปใหม่เท่านั้น คุณยกเลิกสิทธิ์ภายหลังได้", "ต่อไป"),
        Triple("พร้อมเริ่มแล้ว", "สมัครด้วยเบอร์มือถือและ OTP แล้วน้องรับจ่ายจะสร้างพื้นที่ข้อมูลส่วนตัวให้คุณ", "สมัครหรือเข้าสู่ระบบ"),
    )
    val current = pages[page]
    val canContinue = when (page) { 3 -> termsAccepted; 4 -> privacyAccepted; else -> true }
    Surface(Modifier.fillMaxSize(), color = RubInk) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Crossfade(targetState = page, modifier = Modifier.weight(1f).fillMaxWidth(), animationSpec = tween(420), label = "onboarding-page") { animatedPage ->
                val info = pages[animatedPage]
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(.46f).background(Brush.verticalGradient(listOf(if (animatedPage % 2 == 0) RubCoral else RubMint, RubCream))), contentAlignment = Alignment.Center) {
                        OnboardingVisual(animatedPage)
                        if (animatedPage > 0) IconButton(onClick = { page-- }, Modifier.align(Alignment.TopStart).padding(12.dp)) { Icon(Icons.Default.ArrowBack, "ย้อนกลับ", tint = RubInk) }
                    }
                    Column(Modifier.fillMaxWidth().weight(.54f).padding(horizontal = 28.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(info.first, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = RubCream, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(info.second, style = MaterialTheme.typography.bodyLarge, color = RubMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        when (animatedPage) {
                            3 -> Row(Modifier.fillMaxWidth().padding(top = 18.dp).clickable { termsAccepted = !termsAccepted }, verticalAlignment = Alignment.CenterVertically) { Checkbox(termsAccepted, { termsAccepted = it }); Text("ฉันอ่านและยอมรับข้อตกลง", color = RubCream) }
                            4 -> Row(Modifier.fillMaxWidth().padding(top = 18.dp).clickable { privacyAccepted = !privacyAccepted }, verticalAlignment = Alignment.CenterVertically) { Checkbox(privacyAccepted, { privacyAccepted = it }); Text("ฉันเข้าใจและยอมรับการจัดการข้อมูล", color = RubCream) }
                            5 -> OutlinedButton(onClick = { permissionLauncher.launch(photoPermission) }, Modifier.fillMaxWidth().padding(top = 18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = RubMint)) { Icon(Icons.Default.ImageSearch, null); Spacer(Modifier.width(8.dp)); Text(if (photoGranted) "อนุญาตสิทธิ์รูปแล้ว" else "เลือกสิทธิ์การเข้าถึงรูป") }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(pages.size) { index -> Surface(Modifier.size(if (index == page) 28.dp else 8.dp, 8.dp), RoundedCornerShape(50), color = if (index == page) RubCoral else RubMuted.copy(alpha = .45f)) {} } }
                    }
                }
            }
            Surface(color = RubCream) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (page > 0) TextButton(onClick = { page-- }, Modifier.weight(.35f)) { Text("ย้อนกลับ", color = RubInk, fontWeight = FontWeight.Bold) }
                    Button(onClick = { if (page < pages.lastIndex) page++ else done() }, modifier = Modifier.weight(1f).height(56.dp), enabled = canContinue, shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = RubCoral, contentColor = RubInk)) { Text(current.third, fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

@Composable
private fun OnboardingVisual(page: Int) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.size(if (page in 1..2) 230.dp else 190.dp), RoundedCornerShape(40.dp), color = RubInk.copy(alpha = .92f), shadowElevation = 14.dp) {
            Box(contentAlignment = Alignment.Center) {
                when (page) {
                    1, 5 -> Icon(Icons.Default.ReceiptLong, null, Modifier.size(112.dp), tint = RubMint)
                    2 -> Icon(Icons.Default.PieChart, null, Modifier.size(112.dp), tint = RubCoral)
                    3, 4 -> Icon(Icons.Default.CreditCard, null, Modifier.size(112.dp), tint = RubCream)
                    else -> androidx.compose.foundation.Image(painterResource(R.drawable.rubjai_mascot), "น้องรับจ่าย", Modifier.size(160.dp), contentScale = ContentScale.Fit)
                }
            }
        }
        if (page != 0) androidx.compose.foundation.Image(painterResource(R.drawable.rubjai_mascot), null, Modifier.align(Alignment.BottomEnd).size(108.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun RubJaiTopBar(name: String, profile: () -> Unit) {
    Surface(color = RubCoral) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(painterResource(R.drawable.rubjai_mascot), null, Modifier.size(44.dp), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(10.dp))
            Column { Text("น้องรับจ่าย", color = RubInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text(if (name.isBlank()) "พร้อมช่วยดูแลเงินแล้ว" else "สวัสดี $name", style = MaterialTheme.typography.bodySmall, color = RubInk.copy(alpha = .75f)) }
            Spacer(Modifier.weight(1f)); IconButton(onClick = profile) { Icon(Icons.Default.AccountCircle, "บัญชีของฉัน", tint = RubInk) }
        }
    }
}

@Composable
private fun UserHub(name: String, entryCount: Int, editProfile: () -> Unit, debts: () -> Unit, home: () -> Unit, permission: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(250.dp).background(Brush.verticalGradient(listOf(RubCoral, RubCream)), RoundedCornerShape(28.dp))) {
            Column(Modifier.padding(24.dp)) {
                Text("สวัสดี ${name.ifBlank { "คุณ" }}", color = RubInk, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("ข้อมูลและการตั้งค่าของคุณ", color = RubInk.copy(alpha = .72f))
            }
            androidx.compose.foundation.Image(painterResource(R.drawable.rubjai_mascot), null, Modifier.align(Alignment.BottomEnd).padding(12.dp).size(150.dp))
        }
        Card(colors = CardDefaults.cardColors(containerColor = RubPanel), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ภาพรวมบัญชี", color = RubCream, fontWeight = FontWeight.Bold)
                Text("$entryCount รายการที่บันทึกไว้", color = RubMint, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
        }
        UserHubRow(Icons.Default.AccountCircle, "ข้อมูลส่วนตัวและบัญชี", editProfile)
        UserHubRow(Icons.Default.ImageSearch, "สิทธิ์อ่านรูปและการสแกน", permission)
        UserHubRow(Icons.Default.CreditCard, "แผนปลดหนี้", debts)
        UserHubRow(Icons.Default.Home, "กลับไปดูรายการ", home)
    }
}

@Composable
private fun UserHubRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, action: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = action), colors = CardDefaults.cardColors(containerColor = RubPanel), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = RubMint, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(16.dp)); Text(title, Modifier.weight(1f), color = RubCream, fontWeight = FontWeight.SemiBold); Text("›", color = RubCoral, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun HomeActions(busy: Boolean, syncing: Boolean, pending: Int, scan: () -> Unit, sync: () -> Unit, review: () -> Unit, income: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = scan, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.ImageSearch, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "กำลังอ่านสลิป…" else "เลือกสลิปรายจ่าย") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = income, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("เพิ่มรายรับ") }
            OutlinedButton(onClick = sync, enabled = !syncing, modifier = Modifier.weight(1f)) { Icon(Icons.Default.ReceiptLong, null); Spacer(Modifier.width(4.dp)); Text(if (syncing) "กำลังสแกน" else "สแกนสลิปวันนี้") }
        }
        if (pending > 0) Button(onClick = review, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF27D6B), contentColor = Color.White), modifier = Modifier.fillMaxWidth()) { Text("ตรวจรายการรออนุมัติ $pending รายการ", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun KPlusSyncStatus(syncing: Boolean, scanned: Int, consented: Boolean, revoke: () -> Unit) {
    if (!syncing && !consented) return
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F1FF))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (syncing) { Text("น้องรับจ่ายกำลังหาสลิปธนาคาร/วอลเล็ต", fontWeight = FontWeight.Bold); LinearProgressIndicator(Modifier.fillMaxWidth()); Text(if (scanned == 0) "กำลังเตรียมรูป…" else "ตรวจแล้ว $scanned รูป • ยังไม่บันทึกรายการ", style = MaterialTheme.typography.bodySmall) }
            else Row(verticalAlignment = Alignment.CenterVertically) { Text("อนุญาตสแกนเมื่อคุณกดเท่านั้น", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); TextButton(onClick = revoke) { Text("ยกเลิกสิทธิ์") } }
        }
    }
}

@Composable
private fun TransactionDetailDialog(item: MoneyTransaction, onDismiss: () -> Unit, onUpdate: (DraftTransaction) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var amount by remember(item.id) { mutableStateOf(item.amount.toString()) }
    var title by remember(item.id) { mutableStateOf(item.title) }
    var category by remember(item.id) { mutableStateOf(item.category) }
    var remark by remember(item.id) { mutableStateOf(item.remark) }
    var occurredAt by remember(item.id) { mutableStateOf(item.occurredAt) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showSlip by remember { mutableStateOf(false) }
    val slipUri = remember(item.id) { LocalSlipLinkStore.get(context, item.id) }
    val categories = remember(item.type) { CategoryStore.all(context, item.type == "INCOME") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFFFF8EE)) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Surface(color = Color(0xFFFFF3DF)) { Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "กลับ", tint = Color(0xFF0B5D5B)) }
                    Text("รายละเอียดรายการ", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Color(0xFF0B5D5B), fontWeight = FontWeight.Black)
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "ลบรายการ", tint = MaterialTheme.colorScheme.error) }
                } }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(item.type == "EXPENSE", {}, { Text("รายจ่าย") }, modifier = Modifier.weight(1f))
                    FilterChip(item.type == "INCOME", {}, { Text("รายรับ") }, modifier = Modifier.weight(1f))
                    FilterChip(false, {}, { Text("ย้ายเงิน") }, enabled = false, modifier = Modifier.weight(1f))
                }
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { OutlinedTextField(occurredAt, { occurredAt = it.take(50) }, label = { Text("วันที่/เวลา") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.ReceiptLong, null) }) }
                    item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B5D5B)), shape = RoundedCornerShape(24.dp)) { OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("จำนวนเงิน") }, suffix = { Text("บาท") }, textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), singleLine = true, modifier = Modifier.fillMaxWidth().padding(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Color(0xFF76C7B7), unfocusedLabelColor = Color(0xFFB6DAD3), focusedBorderColor = Color(0xFF76C7B7), unfocusedBorderColor = Color(0xFF76C7B7), focusedSuffixColor = Color.White, unfocusedSuffixColor = Color.White)) } }
                    item { OutlinedTextField(title, { title = it.take(200) }, label = { Text("ชื่อผู้รับ/ชื่อรายการ") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("เลือกหมวด / แท็ก", color = Color(0xFF0B5D5B), fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { categories.forEach { FilterChip(category == it, { category = it }, { Text(it) }) } }; Text("เพิ่มหรือลบหมวดได้จากหน้าโปรไฟล์", style = MaterialTheme.typography.bodySmall, color = Color.Gray) } } }
                    item { OutlinedTextField(remark, { remark = it.take(500) }, label = { Text("เพิ่มโน้ต") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
                    if (slipUri.isNotBlank()) item { SlipSourceCard(slipUri, title, occurredAt) { showSlip = true } }
                    if (item.slipFingerprint.isNotBlank()) item { Text("รูปสลิปยังอยู่ในเครื่องเดิมและไม่ถูกอัปโหลด หากลบหรือย้ายรูป แอปจะเปิดดูไม่ได้", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                }
                Surface(color = Color.White, tonalElevation = 4.dp) { Button(enabled = amount.toDoubleOrNull()?.let { it > 0 } == true, onClick = { onUpdate(DraftTransaction(amount, title, TransactionType.valueOf(item.type), item.source, category = category, remark = remark, occurredAt = occurredAt, slipFingerprint = item.slipFingerprint)) }, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(54.dp), shape = RoundedCornerShape(20.dp)) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("บันทึกการแก้ไข", fontWeight = FontWeight.Bold) } }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("ลบรายการนี้?") }, text = { Text("ยอดและข้อมูลรายการจะถูกลบจากบัญชีของคุณ") }, confirmButton = { Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("ลบ") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ยกเลิก") } })
    if (showSlip) FullScreenSlipDialog(slipUri) { showSlip = false }
}

@Composable
private fun SlipSourceCard(uri: String, title: String, occurredAt: String, open: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) { bitmap = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream) }.getOrNull() } }
    Card(Modifier.fillMaxWidth().clickable(onClick = open), colors = CardDefaults.cardColors(containerColor = Color(0xFFE5F2EF))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("ข้อมูลจากสลิป", fontWeight = FontWeight.Bold, color = Color(0xFF0B5D5B)); Text(title.ifBlank { "ไม่พบชื่อผู้รับ" }); if (occurredAt.isNotBlank()) Text(occurredAt, style = MaterialTheme.typography.bodySmall, color = Color.Gray); Text("แตะเพื่อดูสลิป", color = Color(0xFFF27D6B), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            if (bitmap != null) androidx.compose.foundation.Image(bitmap!!.asImageBitmap(), "รูปย่อสลิป", Modifier.size(92.dp), contentScale = ContentScale.Crop) else Surface(Modifier.size(92.dp), color = Color(0xFFCEE5DF), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.ImageSearch, null, Modifier.padding(28.dp), tint = Color(0xFF0B5D5B)) }
        }
    }
}

@Composable
private fun FullScreenSlipDialog(uri: String, close: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var unavailable by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream) }.getOrNull() }
        unavailable = bitmap == null
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                bitmap?.let { androidx.compose.foundation.Image(it.asImageBitmap(), "รูปสลิปต้นฉบับ", Modifier.fillMaxSize().padding(16.dp), contentScale = ContentScale.Fit) }
                if (bitmap == null && !unavailable) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                if (unavailable) Text("เปิดรูปต้นฉบับไม่ได้\nรูปอาจถูกลบหรือย้ายออกจากเครื่อง", color = Color.White, modifier = Modifier.align(Alignment.Center), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = close, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) { Text("ปิด", color = Color.White) }
            }
        }
    }
}

@Composable
private fun DebtPlannerScreen(onClose: () -> Unit) {
    val context = LocalContext.current; val repository = remember { DebtRepository() }
    var debts by remember { mutableStateOf(emptyList<Debt>()) }; var create by remember { mutableStateOf(false) }; var selected by remember { mutableStateOf<Debt?>(null) }; var target by remember { mutableStateOf<Debt?>(null) }; var draft by remember { mutableStateOf<DraftTransaction?>(null) }; var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { repository.observe { updated -> debts = updated; selected = selected?.let { current -> updated.firstOrNull { it.id == current.id } } } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromFilePath(context, uri)).addOnSuccessListener { result ->
                val parsed = SlipParser.parse(result.text, "debt_slip", imageDate(context, uri)).copy(slipUri = uri.toString())
                if (parsed.amount.toDoubleOrNull()?.let { it > 0 } == true) draft = parsed else message = "อ่านยอดจากสลิปไม่พบ กรุณาเลือกสลิปที่ชัดเจน"
                recognizer.close()
            }.addOnFailureListener { message = "อ่านสลิปไม่สำเร็จ"; recognizer.close() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Surface(color = Color(0xFF0B5D5B)) { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { if (selected != null) selected = null else onClose() }) { Icon(Icons.Default.ArrowBack, "กลับ", tint = Color.White) }; Text(selected?.name ?: "รายการหนี้", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); if (selected == null) IconButton(onClick = { create = true }) { Icon(Icons.Default.Add, "เพิ่มหนี้", tint = Color.White) } } }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (selected == null) {
                item { Text("เลือกหนี้เพื่อดูรายละเอียดและประวัติชำระ", style = MaterialTheme.typography.titleMedium); Text("รองรับหนี้หลายก้อนและแยกประวัติของแต่ละรายการ", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
                if (debts.isEmpty()) item { Card { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("ยังไม่มีรายการหนี้"); TextButton(onClick = { create = true }) { Text("+ เพิ่มหนี้ก้อนแรก") } } } }
                items(debts, key = { it.id }) { DebtListCard(it) { selected = it } }
            } else item { DebtDetailCard(selected!!, repository, { target = selected; picker.launch(arrayOf("image/*")) }) { debt -> repository.delete(debt) { error -> message = error ?: "ลบรายการหนี้แล้ว"; if (error == null) selected = null } } }
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

@Composable private fun DebtDetailCard(debt: Debt, repository: DebtRepository, pay: () -> Unit, delete: (Debt) -> Unit) {
    val progress = if (debt.originalBalance > 0) (1 - debt.remainingBalance / debt.originalBalance).toFloat().coerceIn(0f, 1f) else 0f
    var payments by remember(debt.id) { mutableStateOf(emptyList<DebtPayment>()) }
    var confirmDelete by remember(debt.id) { mutableStateOf(false) }
    DisposableEffect(debt.id) { val registration = repository.observePayments(debt.id) { payments = it }; onDispose { registration.remove() } }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row { Text(debt.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(money(debt.remainingBalance), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }; LinearProgressIndicator({ progress }, Modifier.fillMaxWidth()); Text("ชำระแล้ว ${(progress * 100).toInt()}% • ล่าสุด ${money(debt.latestPayment)}"); Text(debt.estimatedMonths()?.let { if (it == 0) "ปิดหนี้แล้ว" else "คาดว่าจะหมดในประมาณ $it เดือน" } ?: "ยอดล่าสุดยังไม่พอคำนวณระยะเวลา", fontWeight = FontWeight.SemiBold); Text(debt.encouragement(), color = Color(0xFF0B9B73)); Button(onClick = pay, modifier = Modifier.fillMaxWidth()) { Text("เลือกสลิปเพื่อตัดยอด") }; if (payments.isNotEmpty()) { HorizontalDivider(); Text("ประวัติชำระ", fontWeight = FontWeight.Bold); payments.forEach { DebtPaymentRow(it) } }; TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Default.Delete, null); Text("ลบหนี้ก้อนนี้", color = MaterialTheme.colorScheme.error) } } }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("ลบหนี้ก้อนนี้?") }, text = { Text("ยอดหนี้และประวัติการชำระทั้งหมดของรายการนี้จะถูกลบ") }, confirmButton = { Button(onClick = { delete(debt); confirmDelete = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("ลบ") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ยกเลิก") } })
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
    val activity = LocalContext.current as Activity
    var phone by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var otp by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val finishCredential: (PhoneAuthCredential) -> Unit = { credential -> busy = true; repository.signInWithPhoneCredential(credential, name) { busy = false; error = it } }
    Surface(Modifier.fillMaxSize(), color = RubInk) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(280.dp).background(Brush.verticalGradient(listOf(RubCoral, RubCream))), contentAlignment = Alignment.BottomCenter) {
                androidx.compose.foundation.Image(painterResource(R.drawable.rubjai_mascot), null, Modifier.size(190.dp), contentScale = ContentScale.Fit)
                Text(
                    text = "เวอร์ชัน ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
                    color = RubInk.copy(alpha = .72f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Text("น้องรับจ่าย", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = RubCream)
            Text("เริ่มดูแลรายการของคุณอย่างเป็นส่วนตัว", color = RubMuted)
            Spacer(Modifier.height(24.dp))
            Card(colors = CardDefaults.cardColors(containerColor = RubPanel), shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Crossfade(verificationId == null, animationSpec = tween(360), label = "otp-step") { phoneStep ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (phoneStep) "สมัครหรือเข้าสู่ระบบ" else "ใส่ OTP 6 หลัก", color = RubCream, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (phoneStep) {
                            OutlinedTextField(name, { name = it.take(100) }, label = { Text("ชื่อที่แสดง") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(10) }, label = { Text("เบอร์มือถือ 10 หลัก") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Text("ระบบจะส่งรหัสทาง SMS เพื่อยืนยันว่าเป็นเบอร์ของคุณ", style = MaterialTheme.typography.bodySmall, color = RubMuted)
                            Button(enabled = !busy && name.isNotBlank() && phone.length == 10, colors = ButtonDefaults.buttonColors(containerColor = RubCoral, contentColor = RubInk), modifier = Modifier.fillMaxWidth().height(54.dp), onClick = {
                                busy = true; error = null
                                repository.requestPhoneOtp(activity, phone, { id, _ -> verificationId = id; busy = false }, finishCredential, { error = it; busy = false })
                            }) { Text(if (busy) "กำลังส่ง…" else "รับรหัส OTP") }
                            OutlinedButton(
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                onClick = {
                                    busy = true; error = null
                                    repository.startTrial { failure -> busy = false; error = failure }
                                },
                            ) { Text("ทดลองใช้งานก่อน (ไม่ใช้ OTP)") }
                            Text("โหมดทดลองใช้บัญชีชั่วคราวแยกจากบัญชีที่ยืนยันด้วยเบอร์โทร", style = MaterialTheme.typography.bodySmall, color = RubMuted)
                    } else {
                            Text("ส่งรหัสไปที่ $phone แล้ว", color = RubMuted)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(6) { index -> Surface(Modifier.weight(1f).aspectRatio(1f), RoundedCornerShape(12.dp), color = RubInk) { Box(contentAlignment = Alignment.Center) { Text(otp.getOrNull(index)?.toString().orEmpty(), color = RubMint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) } } } }
                            OutlinedTextField(otp, { otp = it.filter(Char::isDigit).take(6) }, label = { Text("รหัส OTP") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Button(enabled = !busy && otp.length == 6, colors = ButtonDefaults.buttonColors(containerColor = RubMint, contentColor = RubInk), modifier = Modifier.fillMaxWidth().height(54.dp), onClick = { busy = true; repository.confirmPhoneOtp(verificationId!!, otp, name) { busy = false; error = it } }) { Text(if (busy) "กำลังตรวจ…" else "ยืนยันและเข้าใช้งาน") }
                            TextButton(onClick = { verificationId = null; otp = ""; error = null }) { Text("เปลี่ยนเบอร์มือถือ") }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }}
                }
            }
            }
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
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var message by remember { mutableStateOf<String?>(null) }; var confirmReset by remember { mutableStateOf(false) }; var confirmDelete by remember { mutableStateOf(false) }; var deleting by remember { mutableStateOf(false) }; var manageCategories by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { repository.loadProfile { savedName, savedPhone -> name = savedName; phone = savedPhone } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("โปรไฟล์") }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(repository.auth.currentUser?.phoneNumber ?: repository.auth.currentUser?.email.orEmpty(), color = Color.Gray)
        OutlinedTextField(name, { name = it }, label = { Text("ชื่อที่แสดง") }, singleLine = true)
        OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == '-' } }, label = { Text("เบอร์โทร (ไม่บังคับ)") }, singleLine = true)
        message?.let { Text(it) }
        TextButton(onClick = { manageCategories = true }) { Text("จัดการหมวดรายรับ/รายจ่าย") }
        TextButton(onClick = { repository.signOut(); onDismiss() }) { Text("ออกจากระบบ", color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { confirmReset = true }) { Text("รีเซ็ตข้อมูลของฉัน", color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { confirmDelete = true }) { Text("ลบบัญชีถาวร", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
    } }, confirmButton = { Button(onClick = { repository.updateProfile(name, phone) { message = it ?: "บันทึกแล้ว" } }) { Text("บันทึก") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("ปิด") } })
    if (manageCategories) CategoryManagerDialog { manageCategories = false }
    if (confirmReset) AlertDialog(onDismissRequest = { if (!deleting) confirmReset = false }, title = { Text("รีเซ็ตข้อมูลของฉัน?") }, text = { Text("รายรับ รายจ่าย หนี้ ประวัติชำระ โปรไฟล์ หมวดส่วนตัว และคิวสลิปรอตรวจของบัญชีนี้จะถูกลบ บัญชีและเบอร์มือถือยังคงอยู่") }, confirmButton = { Button(enabled = !deleting, onClick = { deleting = true; repository.clearUsageData { error -> deleting = false; if (error == null) { PendingSlipStore.clearUsage(context); LocalSlipLinkStore.clear(context); CategoryStore.clear(context); onDismiss() } else message = error; confirmReset = false } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(if (deleting) "กำลังรีเซ็ต…" else "ยืนยันรีเซ็ต") } }, dismissButton = { TextButton(enabled = !deleting, onClick = { confirmReset = false }) { Text("ยกเลิก") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!deleting) confirmDelete = false }, title = { Text("ลบบัญชีถาวร?") }, text = { Text("ข้อมูลทั้งหมดและบัญชีเข้าสู่ระบบนี้จะถูกลบถาวร หลังจากนั้นสามารถสมัครใหม่ด้วยเบอร์เดิมและเริ่มตั้งแต่ต้นได้") }, confirmButton = { Button(enabled = !deleting, onClick = { deleting = true; repository.deleteOwnAccount { error -> deleting = false; if (error == null) { PendingSlipStore.clearUsage(context); LocalSlipLinkStore.clear(context); CategoryStore.clear(context); onDismiss() } else message = error; confirmDelete = false } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(if (deleting) "กำลังลบ…" else "ลบบัญชีถาวร") } }, dismissButton = { TextButton(enabled = !deleting, onClick = { confirmDelete = false }) { Text("ยกเลิก") } })
}

@Composable
private fun CategoryManagerDialog(close: () -> Unit) {
    val context = LocalContext.current
    var income by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    val categories = remember(income, refresh) { CategoryStore.all(context, income) }
    val defaults = if (income) CategoryStore.incomeDefaults else CategoryStore.expenseDefaults
    AlertDialog(onDismissRequest = close, title = { Text("จัดการหมวด") }, text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!income, { income = false }, { Text("รายจ่าย") }); FilterChip(income, { income = true }, { Text("รายรับ") }) }
        Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(newName, { newName = it.take(50) }, label = { Text("เพิ่มหมวดใหม่") }, modifier = Modifier.weight(1f), singleLine = true); IconButton(enabled = newName.isNotBlank(), onClick = { CategoryStore.add(context, income, newName); newName = ""; refresh++ }) { Icon(Icons.Default.Add, "เพิ่มหมวด") } }
        categories.forEach { category -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(category, Modifier.weight(1f)); if (category !in defaults) IconButton(onClick = { CategoryStore.remove(context, income, category); refresh++ }) { Icon(Icons.Default.Delete, "ลบ $category", tint = MaterialTheme.colorScheme.error) } } }
    } }, confirmButton = { Button(onClick = close) { Text("เสร็จ") } })
}

@Composable
private fun SummaryCard(entries: List<MoneyTransaction>) {
    val income = entries.filter { it.type == "INCOME" }.sumOf { it.amount }
    val expense = entries.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = RubPanel)) {
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Text("คงเหลือ", color = RubMuted); Text(money(income - expense), color = RubCream, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp)); Row { Text("รับ  ${money(income)}", color = RubMint); Spacer(Modifier.weight(1f)); Text("จ่าย  ${money(expense)}", color = RubCoral) }
        }
    }
}

@Composable private fun PendingSlipDialog(items: List<PendingSlip>, onClose: () -> Unit, onApprove: (PendingSlip) -> Unit, onReject: (PendingSlip) -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("สลิปธนาคาร/วอลเล็ตที่รออนุมัติ") }, text = {
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
    val groups = entries.groupBy { it.category.ifBlank { "ใช้จ่ายทั่วไป" } }.mapValues { it.value.sumOf(MoneyTransaction::amount) }.entries.sortedByDescending { it.value }
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

@Composable private fun EntryRow(item: MoneyTransaction, open: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = open), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(42.dp), shape = RoundedCornerShape(14.dp), color = if (item.type == "INCOME") Color(0xFFE1F7ED) else Color(0xFFFFE8E5)) { Icon(if (item.type == "INCOME") Icons.Default.Add else Icons.Default.ReceiptLong, null, Modifier.padding(10.dp), tint = if (item.type == "INCOME") Color(0xFF0B9B73) else Color(0xFFD84A3A)) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.title.ifBlank { item.category.ifBlank { "ไม่ระบุรายการ" } }, fontWeight = FontWeight.SemiBold); Text(listOf(item.category, item.occurredAt).filter(String::isNotBlank).joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; Text((if (item.type == "INCOME") "+" else "-") + money(item.amount), color = if (item.type == "INCOME") Color(0xFF0B9B73) else Color(0xFFD84A3A), fontWeight = FontWeight.Bold) } } }

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
            Text("หมวด: ยังไม่จัดหมวด (แตะรายการหลังบันทึกเพื่อใส่เอง)")
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
