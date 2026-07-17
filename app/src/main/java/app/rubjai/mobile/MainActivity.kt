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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
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
private val RubEntryYellow = Color(0xFFFFDA4D)
private val RubEntryNavy = Color(0xFF00243D)
private val RubEntryCard = Color(0xFF001C31)
private val RubEntryTab = Color(0xFFFFF0A7)
private val RubBlue = Color(0xFF1E95FF)
private val RubRed = Color(0xFFFF4B5C)
private val RubEntryMuted = Color(0xFF8FA9BF)

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
                    androidx.compose.foundation.Image(painterResource(R.drawable.rubjai_mark), null, Modifier.size(132.dp))
                    if (migrationError == null) { CircularProgressIndicator(color = RubMint); Text("กำลังเตรียม RubJai 3", color = RubCream) }
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
    var syncStatusText by remember { mutableStateOf("") }
    var pendingSlips by remember { mutableStateOf(PendingSlipStore.load(context)) }
    var showPending by remember { mutableStateOf(false) }
    var pendingToReview by remember { mutableStateOf<PendingSlip?>(null) }
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
                androidx.work.WorkInfo.State.RUNNING -> { syncing = true; syncScanned = info.progress.getInt("scanned", 0); syncStatusText = if (syncScanned > 0) "ตรวจแล้ว $syncScanned รูป" else "กำลังค้นหาสลิปย้อนหลัง 1 เดือน" }
                androidx.work.WorkInfo.State.SUCCEEDED -> { syncing = false; pendingSlips = PendingSlipStore.load(context); val found = info.outputData.getInt("found", 0); syncStatusText = if (found > 0) "พบ $found รายการ รอตรวจ" else "ซิงค์แล้ว ไม่พบสลิปใหม่"; syncWorkId = null; break }
                androidx.work.WorkInfo.State.FAILED, androidx.work.WorkInfo.State.CANCELLED -> { syncing = false; syncStatusText = "ซิงค์ไม่สำเร็จ แตะเพื่อสแกนใหม่"; syncWorkId = null; break }
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
                    val qr = runCatching { SlipQrReader.scanBlocking(context, uri) }.getOrDefault(SlipQrResult())
                    val text = SlipQrReader.appendToOcrText(result.text, qr)
                    val parsed = SlipParser.parse(text, if (qr.rawValues.isNotEmpty()) "slip_qr_ocr" else "slip_ocr", imageDate(context, uri))
                    val fingerprint = SlipQrReader.fingerprint(qr)
                    if (parsed.amount.toDoubleOrNull()?.let { it > 0 } == true || fingerprint.isNotBlank()) draft = parsed.copy(slipUri = uri.toString(), slipFingerprint = fingerprint) else message = "อ่านยอดจากรูปไม่พบ กรุณาเลือกภาพสลิปที่ชัดเจน"
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
    LaunchedEffect(syncConsent) {
        if (syncConsent && !syncing && ContextCompat.checkSelfPermission(context, scanPermission) == PackageManager.PERMISSION_GRANTED) {
            startKPlusSync()
        }
    }
    DisposableEffect(syncConsent, scanPermission) {
        if (syncConsent && ContextCompat.checkSelfPermission(context, scanPermission) == PackageManager.PERMISSION_GRANTED) {
            KPlusSyncManager.startRealtime(context) { id ->
                syncWorkId = id
                syncing = true
                syncScanned = 0
            }
        }
        onDispose { KPlusSyncManager.stopRealtime(context) }
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = RubInk,
            topBar = {},
            bottomBar = {
                NavigationBar(containerColor = RubCream) {
                    NavigationBarItem(mainTab == 0, { mainTab = 0 }, { Icon(Icons.Default.Home, null) }, label = { Text("หน้าหลัก") })
                    NavigationBarItem(mainTab == 1, { mainTab = 1 }, { Icon(Icons.Default.AccountCircle, null) }, label = { Text("บัญชีของฉัน") })
                }
            },
            floatingActionButton = {
                if (mainTab == 0) ExtendedFloatingActionButton(
                    containerColor = RubBlue,
                    contentColor = Color.White,
                    onClick = { draft = DraftTransaction(type = TransactionType.EXPENSE, source = "manual_expense", category = "ยังไม่จัดหมวด") },
                    icon = { Icon(Icons.Default.Edit, "จดเพิ่ม") },
                    text = { Text("จดเพิ่ม", fontWeight = FontWeight.Black) },
                )
            }
        ) { padding ->
            val visibleEntries = remember(entries, entryPeriod, entryKind) { entries.filterFor(entryPeriod, entryKind) }
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(top = 18.dp, bottom = 116.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (mainTab == 0) {
                    item {
                        HomeReferenceScreen(
                            entries = visibleEntries,
                            allEntries = entries,
                            syncing = syncing,
                            syncScanned = syncScanned,
                            syncStatus = syncStatusText,
                            pending = pendingSlips.size,
                            busy = busy,
                            onScan = { imagePicker.launch(arrayOf("image/*")) },
                            onSync = { if (!syncConsent) showSyncConsent = true else if (ContextCompat.checkSelfPermission(context, scanPermission) == PackageManager.PERMISSION_GRANTED) startKPlusSync() else permissionLauncher.launch(scanPermission) },
                            onReview = { pendingSlips = PendingSlipStore.load(context); showPending = true },
                            onOpen = { selectedEntry = it },
                            onAdd = { draft = DraftTransaction(type = TransactionType.EXPENSE, source = "manual_expense", category = "ยังไม่จัดหมวด") },
                        )
                    }
                } else item { Box(Modifier.padding(horizontal = 16.dp)) { UserHub(authRepository.auth.currentUser?.displayName.orEmpty(), entries.size, { showProfile = true }, { showDebts = true }, { mainTab = 0 }, { showSyncConsent = true }) } }
            }
        }
        draft?.let { current -> TransactionDialog(current, onDismiss = { draft = null }, onSave = { saved -> busy = true; repository.add(saved, onDone = { error -> busy = false; if (error == null && saved.slipUri.isNotBlank()) LocalSlipLinkStore.put(context, TransactionRepository.documentIdFor(saved), saved.slipUri); message = error ?: "บันทึกแล้ว" }); draft = null }) }
        pendingToReview?.let { pending ->
            TransactionDialog(
                pending.draft,
                onDismiss = { pendingToReview = null },
                onSave = { saved ->
                    busy = true
                    repository.add(saved) { error ->
                        busy = false
                        if (error == null) {
                            if (saved.slipUri.isNotBlank()) LocalSlipLinkStore.put(context, TransactionRepository.documentIdFor(saved), saved.slipUri)
                            PendingSlipStore.remove(context, pending.id)
                            pendingSlips = PendingSlipStore.load(context)
                            pendingToReview = null
                            if (pendingSlips.isEmpty()) showPending = false
                        }
                        message = error ?: "บันทึกรายจ่ายแล้ว"
                    }
                },
            )
        }
        message?.let { AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("ตกลง") } }, text = { Text(it) }) }
        if (showSyncConsent) AlertDialog(onDismissRequest = { showSyncConsent = false }, title = { Text("ยินยอมสแกนสลิปวันนี้?") }, text = { Text("RubJai จะอ่านรูปที่เพิ่มในวันนี้เพื่อหาสลิปธนาคารและวอลเล็ตด้วย OCR บนเครื่อง ผลจะอยู่ในคิวรออนุมัติและยังไม่บันทึกจนกว่าคุณจะยืนยัน") }, confirmButton = { Button(onClick = { KPlusSyncManager.setConsent(context, true); syncConsent = true; showSyncConsent = false; if (ContextCompat.checkSelfPermission(context, scanPermission) == PackageManager.PERMISSION_GRANTED) startKPlusSync() else permissionLauncher.launch(scanPermission) }) { Text("ยินยอมและสแกน") } }, dismissButton = { TextButton(onClick = { showSyncConsent = false }) { Text("ยังไม่ยินยอม") } })
        if (showPending) PendingSlipDialog(pendingSlips, onClose = { showPending = false }, onReview = { pendingToReview = it }, onReject = { pending -> PendingSlipStore.remove(context, pending.id); pendingSlips = PendingSlipStore.load(context); if (pendingSlips.isEmpty()) showPending = false })
        selectedEntry?.let { item ->
            TransactionDialog(
                initial = item.toDraft(LocalSlipLinkStore.get(context, item.id)),
                onDismiss = { selectedEntry = null },
                onSave = { draftValue ->
                    repository.update(item, draftValue) { error ->
                        if (error == null && draftValue.slipUri.isNotBlank()) LocalSlipLinkStore.put(context, item.id, draftValue.slipUri)
                        message = error ?: "แก้ไขแล้ว"
                        if (error == null) selectedEntry = null
                    }
                },
            )
        }
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
                    else -> androidx.compose.foundation.Image(painterResource(R.drawable.rubjai_mark), "น้องรับจ่าย", Modifier.size(160.dp), contentScale = ContentScale.Fit)
                }
            }
        }
        if (page != 0) androidx.compose.foundation.Image(painterResource(R.drawable.rubjai_mark), null, Modifier.align(Alignment.BottomEnd).size(108.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun UserHub(name: String, entryCount: Int, editProfile: () -> Unit, debts: () -> Unit, home: () -> Unit, permission: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(RubEntryNavy).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(Modifier.fillMaxWidth().heightIn(min = 210.dp), color = RubEntryYellow, shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.padding(start = 28.dp, top = 28.dp, end = 140.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("บัญชีของฉัน", color = RubEntryNavy, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("สวัสดี ${name.ifBlank { "คุณ" }}", color = RubEntryNavy.copy(alpha = .82f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("$entryCount รายการที่บันทึกไว้", color = RubBlue, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                }
                androidx.compose.foundation.Image(painterResource(R.drawable.rubjai_mark), null, Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp).size(138.dp))
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
    Card(Modifier.fillMaxWidth().clickable(onClick = action), colors = CardDefaults.cardColors(containerColor = RubEntryCard), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = RubBlue, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(16.dp)); Text(title, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.SemiBold); Text("›", color = RubEntryYellow, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun HomeReferenceScreen(
    entries: List<MoneyTransaction>,
    allEntries: List<MoneyTransaction>,
    syncing: Boolean,
    syncScanned: Int,
    syncStatus: String,
    pending: Int,
    busy: Boolean,
    onScan: () -> Unit,
    onSync: () -> Unit,
    onReview: () -> Unit,
    onOpen: (MoneyTransaction) -> Unit,
    onAdd: () -> Unit,
) {
    val expenseEntries = allEntries.filter { it.type == "EXPENSE" }
    val monthExpense = expenseEntries.sumOf { it.amount }
    val latest = allEntries.maxByOrNull { it.createdAt?.time ?: 0L }
    val latestTime = latest?.createdAt?.let { SimpleDateFormat("HH:mm", Locale("th", "TH")).format(it) } ?: SimpleDateFormat("HH:mm", Locale("th", "TH")).format(Date())
    val groups = entries.groupBy { homeDayMeta(it) }

    BoxWithConstraints(Modifier.fillMaxWidth().background(RubEntryNavy)) {
        val railWidth = if (maxWidth < 420.dp) 76.dp else 92.dp
        val sidePadding = if (maxWidth < 420.dp) 16.dp else 24.dp
        val summaryPadding = if (maxWidth < 420.dp) 22.dp else 32.dp
        val mascotSize = if (maxWidth < 420.dp) 82.dp else 96.dp
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = railWidth + sidePadding, end = sidePadding, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, null, tint = RubEntryMuted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("จดล่าสุดวันนี้ $latestTime", color = RubEntryMuted, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }

            Box(Modifier.fillMaxWidth().padding(start = railWidth)) {
                Column(Modifier.fillMaxWidth()) {
                    Surface(
                        Modifier.fillMaxWidth().heightIn(min = 190.dp),
                        color = RubEntryYellow,
                        shape = RoundedCornerShape(topStart = 18.dp),
                    ) {
                        Column(Modifier.padding(start = summaryPadding, end = sidePadding, top = 24.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.KeyboardArrowLeft, null, tint = RubBlue, modifier = Modifier.size(34.dp))
                                Icon(Icons.Default.CalendarMonth, null, tint = RubBlue, modifier = Modifier.size(30.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(homeMonthLabel(), color = RubBlue, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 1)
                                Icon(Icons.Default.KeyboardArrowRight, null, tint = RubBlue, modifier = Modifier.size(34.dp))
                            }
                            Text("ยอดใช้จ่าย", color = RubEntryNavy, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(moneyPlain(monthExpense), color = RubEntryNavy, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, maxLines = 1, modifier = Modifier.weight(1f))
                                Button(
                                    onClick = {},
                                    colors = ButtonDefaults.buttonColors(containerColor = RubBlue, contentColor = Color.White),
                                    shape = RoundedCornerShape(28.dp),
                                    modifier = Modifier.height(54.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                ) {
                                    Icon(Icons.Default.PieChart, null, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("ดูสรุป", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1)
                                }
                            }
                        }
                    }
                    HomeSlipSyncBand(syncing, syncScanned, syncStatus, pending, busy, onScan, onSync, onReview)
                }
                MascotBadge(
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = sidePadding).offset(y = (-42).dp).size(mascotSize)
                )
            }

            if (groups.isEmpty()) {
                HomeDaySection(HomeDayMeta("วันนี้", Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()), emptyList(), railWidth, onOpen)
            } else {
                groups.forEach { (day, dayEntries) -> HomeDaySection(day, dayEntries, railWidth, onOpen) }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = RubBlue, contentColor = Color.White),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.align(Alignment.End).padding(end = sidePadding, bottom = 24.dp).height(58.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text("จดเพิ่ม", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MascotBadge(modifier: Modifier = Modifier) {
    Surface(modifier, color = RubEntryNavy, shape = CircleShape, shadowElevation = 6.dp) {
        Box(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                painterResource(R.drawable.rubjai_mark),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
@Composable
private fun HomeSlipSyncBand(syncing: Boolean, scanned: Int, status: String, pending: Int, busy: Boolean, onScan: () -> Unit, onSync: () -> Unit, onReview: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = RubEntryTab) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(48.dp), color = RubBlue.copy(alpha = 0.16f), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.ReceiptLong, null, tint = RubBlue, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            pending > 0 -> "สลิปรอตรวจ $pending รายการ"
                            syncing -> "กำลังซิงค์สลิป"
                            else -> "ซิงค์สลิปอัตโนมัติ"
                        },
                        color = RubEntryNavy,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Text(
                        when {
                            syncing && scanned > 0 -> "ตรวจแล้ว $scanned รูป"
                            status.isNotBlank() -> status
                        else -> "เปิดแอพแล้วตรวจย้อนหลัง 1 เดือน และเฝ้าดูรูปใหม่"
                        },
                        color = RubBlue,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                }
            }
            if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = RubBlue, trackColor = RubBlue.copy(alpha = 0.16f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                OutlinedButton(
                    onClick = onScan,
                    enabled = !busy,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.5.dp, RubEntryNavy.copy(alpha = 0.28f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("เลือกสลิป", color = RubEntryNavy, fontWeight = FontWeight.Bold, maxLines = 1) }
                Button(
                    onClick = if (pending > 0) onReview else onSync,
                    enabled = !busy,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RubBlue, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) { Text(if (pending > 0) "ตรวจ" else "ซิงค์", fontWeight = FontWeight.Black, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun HomeDaySection(day: HomeDayMeta, entries: List<MoneyTransaction>, railWidth: androidx.compose.ui.unit.Dp, onOpen: (MoneyTransaction) -> Unit) {
    val expense = entries.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(railWidth).fillMaxHeight().background(RubEntryNavy), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.padding(top = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day.label, color = if (day.label == "วันนี้") RubEntryYellow else Color.White, style = MaterialTheme.typography.titleLarge)
                Text(day.number, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            }
        }
        Column(Modifier.weight(1f)) {
            Surface(Modifier.fillMaxWidth().height(104.dp), color = RubPanel) {
                Row(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowUpward, null, tint = RubEntryMuted, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("รายจ่าย", color = RubEntryMuted, style = MaterialTheme.typography.titleLarge)
                        }
                        Text(moneyPlain(expense), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                        if (entries.isNotEmpty()) Text("${entries.size} รายการ", color = RubEntryMuted, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(160.dp).background(RubEntryCard), contentAlignment = Alignment.CenterEnd) {
                    Text("ไม่มีรายการ", Modifier.padding(end = 28.dp), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                }
            } else {
                entries.forEach { HomeTimelineRow(it, onOpen) }
            }
        }
    }
}

@Composable
private fun HomeTimelineRow(item: MoneyTransaction, onOpen: (MoneyTransaction) -> Unit) {
    val visual = categoryVisual(item.category)
    Row(
        Modifier.fillMaxWidth().background(RubEntryCard).clickable { onOpen(item) }.padding(horizontal = 28.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(Modifier.size(62.dp), color = Color.Transparent, shape = RoundedCornerShape(31.dp)) {
            Icon(if (item.type == "INCOME") Icons.Default.ArrowDownward else visual.icon, null, tint = if (item.type == "INCOME") RubMint else visual.tint, modifier = Modifier.padding(10.dp))
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(item.category.ifBlank { "ยังไม่จัดหมวด" }, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(item.title.ifBlank { "ไม่ระบุรายการ" }, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        Text(moneyPlain(item.amount), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
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
            if (bitmap != null) androidx.compose.foundation.Image(bitmap!!.asImageBitmap(), "รูปย่อสลิป", Modifier.size(92.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) else Surface(Modifier.size(92.dp), color = Color(0xFFCEE5DF), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.ImageSearch, null, Modifier.padding(28.dp), tint = Color(0xFF0B5D5B)) }
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
                val qr = runCatching { SlipQrReader.scanBlocking(context, uri) }.getOrDefault(SlipQrResult())
                val text = SlipQrReader.appendToOcrText(result.text, qr)
                val parsed = SlipParser.parse(text, if (qr.rawValues.isNotEmpty()) "debt_slip_qr_ocr" else "debt_slip", imageDate(context, uri)).copy(slipUri = uri.toString(), slipFingerprint = SlipQrReader.fingerprint(qr))
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
    val context = LocalContext.current
    val activity = context as Activity
    val updateManager = remember { InAppUpdateManager() }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateProgress by remember { mutableStateOf<Float?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var phone by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var otp by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        updateManager.checkForUpdate(version) { availableUpdate = it }
    }
    val finishCredential: (PhoneAuthCredential) -> Unit = { credential -> busy = true; repository.signInWithPhoneCredential(credential, name) { busy = false; error = it } }
    Surface(Modifier.fillMaxSize(), color = RubInk) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(280.dp).background(Brush.verticalGradient(listOf(RubCoral, RubCream))), contentAlignment = Alignment.BottomCenter) {
                MascotBadge(Modifier.size(156.dp))
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

@Composable private fun PendingSlipDialog(items: List<PendingSlip>, onClose: () -> Unit, onReview: (PendingSlip) -> Unit, onReject: (PendingSlip) -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("สลิปรอตรวจ") }, text = {
        if (items.isEmpty()) Text("ไม่มีรายการรอตรวจ") else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { pending ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F7FA)), modifier = Modifier.fillMaxWidth().clickable { onReview(pending) }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (pending.draft.slipUri.isNotBlank()) SlipSourceCard(pending.draft.slipUri, pending.draft.title, pending.draft.occurredAt) { onReview(pending) }
                        Text(pending.draft.title.ifBlank { "ไม่พบชื่อร้าน/ผู้รับ" }, fontWeight = FontWeight.Bold)
                        Text("${pending.draft.amount} บาท", color = RubBlue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(listOf(pending.draft.occurredAt, pending.draft.category).filter(String::isNotBlank).joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onReject(pending) }) { Text("ลบออก") }
                            Button(onClick = { onReview(pending) }) { Text("ตรวจ / เลือกหมวด") }
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

@Composable
private fun TransactionDialog(initial: DraftTransaction, onDismiss: () -> Unit, onSave: (DraftTransaction) -> Unit) {
    val context = LocalContext.current
    val isIncomeStart = initial.type == TransactionType.INCOME || initial.source == "manual_income"
    var mode by remember(initial) { mutableStateOf(if (isIncomeStart) TransactionType.INCOME else TransactionType.EXPENSE) }
    var amount by remember(initial) { mutableStateOf(initial.amount) }
    var title by remember(initial) { mutableStateOf(initial.title) }
    var category by remember(initial) { mutableStateOf(initial.category.ifBlank { if (isIncomeStart) "รายรับ" else "ยังไม่จัดหมวด" }) }
    var remark by remember(initial) { mutableStateOf(initial.remark) }
    var showCategories by remember { mutableStateOf(false) }
    var showSlip by remember { mutableStateOf(false) }
    val categories = remember(context, mode) { CategoryStore.all(context, mode == TransactionType.INCOME) }
    val accent = if (mode == TransactionType.INCOME) RubBlue else RubRed
    val selectedIcon = categoryVisual(category).icon

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(RubEntryNavy)) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().background(RubEntryYellow).padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Close, null, tint = RubEntryNavy, modifier = Modifier.size(34.dp)) }
                    EntryModeTabs(mode, { mode = it }, Modifier.weight(1f))
                    IconButton(onClick = {}, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.MoreVert, null, tint = RubEntryNavy, modifier = Modifier.size(34.dp)) }
                }

                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = RubBlue, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(initial.occurredAt.ifBlank { thaiTodayLabel() }, color = RubBlue, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    }

                    Surface(Modifier.fillMaxWidth().height(148.dp), color = RubEntryCard, shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (mode == TransactionType.INCOME) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, tint = accent, modifier = Modifier.size(56.dp))
                            Spacer(Modifier.width(22.dp))
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' }.take(12) },
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.displayMedium.copy(color = Color.White, fontWeight = FontWeight.Light),
                                singleLine = true,
                                placeholder = { Text("0", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.displayMedium) },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, cursorColor = RubBlue, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                            )
                            Text("฿", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    EntryField(icon = Icons.Default.Edit, text = title, placeholder = if (mode == TransactionType.INCOME) "ชื่อรายรับ" else "ชื่อร้าน / ผู้รับ", onChange = { title = it.take(80) })
                    EntrySelectField(icon = selectedIcon, text = category.ifBlank { "เลือกหมวดหมู่ / แท็ก" }, onClick = { showCategories = true })
                    EntryField(icon = Icons.Default.ReceiptLong, text = remark, placeholder = "โน้ต / รายละเอียดเพิ่มเติม", onChange = { remark = it.take(160) })

                    if (initial.rawText.isNotBlank()) Text("ตรวจจากสลิปแล้ว: แก้ชื่อ ยอด และหมวดได้ก่อนบันทึก", color = RubEntryMuted, style = MaterialTheme.typography.bodyMedium)
                    if (initial.slipUri.isNotBlank()) SlipSourceCard(initial.slipUri, title.ifBlank { category }, initial.occurredAt) { showSlip = true }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = amount.toDoubleOrNull()?.let { it > 0.0 } == true,
                        onClick = {
                            onSave(initial.copy(amount = amount, title = title.ifBlank { category }, type = mode, category = category, remark = remark))
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RubBlue, contentColor = Color.White),
                    ) { Text(if (mode == TransactionType.INCOME) "บันทึกรายรับ" else "บันทึกรายจ่าย", fontWeight = FontWeight.Bold) }
                }
            }

            if (showCategories) CategoryPickerSheet(
                categories = categories,
                income = mode == TransactionType.INCOME,
                selected = category,
                onSelect = { category = it; showCategories = false },
                onClose = { showCategories = false },
            )
        }
    }
    if (showSlip) FullScreenSlipDialog(initial.slipUri) { showSlip = false }
}

@Composable
private fun EntryModeTabs(mode: TransactionType, change: (TransactionType) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.height(72.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
        EntryModeTab(Icons.Default.ArrowUpward, "รายจ่าย", mode == TransactionType.EXPENSE) { change(TransactionType.EXPENSE) }
        EntryModeTab(Icons.Default.ArrowDownward, "รายรับ", mode == TransactionType.INCOME) { change(TransactionType.INCOME) }
        EntryModeTab(Icons.Default.SwapHoriz, "ย้ายเงิน", false) { }
    }
}

@Composable
private fun EntryModeTab(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) RubEntryNavy else RubEntryTab
    val fg = if (selected) Color.White else RubEntryNavy
    Surface(
        Modifier.width(84.dp).fillMaxHeight().clickable(onClick = onClick),
        color = bg,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(30.dp))
            Text(label, color = fg, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EntryField(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
        leadingIcon = { Icon(icon, null, tint = RubBlue, modifier = Modifier.size(32.dp)) },
        placeholder = { Text(placeholder, color = RubBlue, fontWeight = FontWeight.Bold) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.Bold),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RubBlue, unfocusedBorderColor = RubBlue, cursorColor = RubBlue, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
    )
}

@Composable
private fun EntrySelectField(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().height(72.dp).clickable(onClick = onClick),
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.5.dp, RubBlue),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = RubBlue, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(20.dp))
            Text(text, color = RubBlue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CategoryPickerSheet(categories: List<String>, income: Boolean, selected: String, onSelect: (String) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Surface(
            Modifier.fillMaxWidth().heightIn(min = 430.dp, max = 620.dp).clickable(onClick = {}),
            color = Color.White,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        ) {
            Column {
                Row(Modifier.fillMaxWidth().background(Color(0xFFE8EEF5)).padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("เลือกหมวดหมู่ / แท็ก", color = Color.Black, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Color(0xFF4B4C55), modifier = Modifier.size(36.dp)) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(48.dp), color = Color(0xFFE5F7FF), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.GridView, null, tint = RubBlue, modifier = Modifier.padding(10.dp)) }
                    Spacer(Modifier.width(18.dp))
                    OutlinedButton(onClick = {}, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.5.dp, RubBlue)) { Icon(Icons.Default.Add, null, tint = RubBlue); Spacer(Modifier.width(6.dp)); Text("เพิ่มแท็ก", color = RubBlue, fontWeight = FontWeight.Bold) }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(categories) { category -> CategoryGridItem(category, category == selected, income, onSelect) }
                }
            }
        }
    }
}

@Composable
private fun CategoryGridItem(category: String, selected: Boolean, income: Boolean, onSelect: (String) -> Unit) {
    val visual = categoryVisual(category)
    Column(Modifier.fillMaxWidth().clickable { onSelect(category) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            Modifier.size(72.dp),
            color = Color.White,
            shape = RoundedCornerShape(36.dp),
            shadowElevation = 5.dp,
            border = if (selected) BorderStroke(2.dp, RubBlue) else null,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(Modifier.size(48.dp), color = visual.background, shape = RoundedCornerShape(24.dp)) {
                    Icon(if (income) Icons.Default.ArrowDownward else visual.icon, null, tint = visual.tint, modifier = Modifier.padding(9.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(categoryDisplayName(category), color = Color(0xFF1B1D28), style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Black else FontWeight.Normal, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

private data class CategoryVisual(val icon: androidx.compose.ui.graphics.vector.ImageVector, val tint: Color, val background: Color)

private fun categoryVisual(category: String): CategoryVisual {
    val value = category.lowercase(Locale.getDefault())
    return when {
        value.contains("อาหาร") || value.contains("food") -> CategoryVisual(Icons.Default.Restaurant, Color(0xFFFFB21A), Color(0xFFFFF4D7))
        value.contains("เดินทาง") || value.contains("รถ") || value.contains("travel") -> CategoryVisual(Icons.Default.DirectionsCar, Color(0xFF16BFD2), Color(0xFFE1FAFD))
        value.contains("ของใช้") || value.contains("จำเป็น") -> CategoryVisual(Icons.Default.ReceiptLong, Color(0xFFE7D91A), Color(0xFFFFFBE1))
        value.contains("ช้อ") || value.contains("shop") -> CategoryVisual(Icons.Default.ShoppingBag, Color(0xFFFF20D7), Color(0xFFFFE5FA))
        value.contains("บันเทิง") || value.contains("movie") -> CategoryVisual(Icons.Default.Movie, Color(0xFFFF4A19), Color(0xFFFFE7DE))
        value.contains("บ้าน") || value.contains("ที่พัก") || value.contains("สาธาร") -> CategoryVisual(Icons.Default.House, Color(0xFF1A63FF), Color(0xFFE5EEFF))
        value.contains("สุขภาพ") || value.contains("health") -> CategoryVisual(Icons.Default.LocalHospital, Color(0xFF20B99D), Color(0xFFE1FBF4))
        value.contains("ครอบ") || value.contains("สัตว์") -> CategoryVisual(Icons.Default.FamilyRestroom, Color(0xFFE868DC), Color(0xFFFFE8FC))
        value.contains("ให้") || value.contains("gift") -> CategoryVisual(Icons.Default.CardGiftcard, Color(0xFF9D27FF), Color(0xFFF2E5FF))
        value.contains("เที่ยว") || value.contains("beach") -> CategoryVisual(Icons.Default.BeachAccess, Color(0xFF2183D8), Color(0xFFE2F2FF))
        value.contains("ศึกษา") || value.contains("school") -> CategoryVisual(Icons.Default.School, Color(0xFF5C4CF6), Color(0xFFEDEAFF))
        value.contains("งาน") || value.contains("ธุรกิจ") || value.contains("work") -> CategoryVisual(Icons.Default.Work, Color(0xFFE08D00), Color(0xFFFFF1D8))
        else -> CategoryVisual(Icons.Default.GridView, RubBlue, Color(0xFFE5F7FF))
    }
}

private fun categoryDisplayName(category: String): String = category

private fun thaiTodayLabel(): String {
    val now = Calendar.getInstance(Locale("th", "TH"))
    val dayName = SimpleDateFormat("EEE", Locale("th", "TH")).format(now.time)
    val day = now.get(Calendar.DAY_OF_MONTH)
    val month = SimpleDateFormat("MMM", Locale("th", "TH")).format(now.time)
    val year = (now.get(Calendar.YEAR) + 543).toString().takeLast(2)
    return "$dayName $day $month $year"
}

private data class HomeDayMeta(val label: String, val number: String)

private fun homeMonthLabel(date: Date = Date()): String {
    val calendar = Calendar.getInstance(Locale("th", "TH")).apply { time = date }
    val month = SimpleDateFormat("MMM", Locale("th", "TH")).format(calendar.time)
    val year = ((calendar.get(Calendar.YEAR) + 543) % 100).toString().padStart(2, '0')
    return "$month $year"
}

private fun homeDayMeta(item: MoneyTransaction): HomeDayMeta {
    val calendar = Calendar.getInstance(Locale("th", "TH"))
    val now = Calendar.getInstance(Locale("th", "TH"))
    item.createdAt?.let { calendar.time = it }
    val dayNumber = Regex("(?<![0-9])([0-3]?[0-9])\\s+[^0-9\\n]{1,12}\\s+(?:[0-9]{2}|[0-9]{4})").find(item.occurredAt)?.groupValues?.getOrNull(1)
        ?: Regex("(?<![0-9])([0-3]?[0-9])[/.-][01]?[0-9][/.-](?:[0-9]{2}|[0-9]{4})").find(item.occurredAt)?.groupValues?.getOrNull(1)
        ?: calendar.get(Calendar.DAY_OF_MONTH).toString()
    val sameDay = now.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR)
    val label = if (sameDay) "วันนี้" else SimpleDateFormat("EEE", Locale("th", "TH")).format(calendar.time)
    return HomeDayMeta(label, dayNumber.trimStart('0').ifBlank { dayNumber })
}

private fun MoneyTransaction.toDraft(localSlipUri: String = "") = DraftTransaction(
    amount = amount.toString(),
    title = title,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
    source = source,
    rawText = rawText,
    category = category.ifBlank { "ยังไม่จัดหมวด" },
    remark = remark,
    occurredAt = occurredAt,
    slipFingerprint = slipFingerprint,
    slipUri = localSlipUri,
)

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
private fun moneyPlain(value: Double) = NumberFormat.getNumberInstance(Locale("th", "TH")).apply {
    minimumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
    maximumFractionDigits = 2
}.format(value)
