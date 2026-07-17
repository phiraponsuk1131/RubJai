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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
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
    val category: String = "เนเธเนเธเนเธฒเธขเธ—เธฑเนเธงเนเธ",
    val remark: String = "",
    val occurredAt: String = "",
    val slipFingerprint: String = "",
    val slipUri: String = "",
)

enum class TransactionType { INCOME, EXPENSE }
private enum class EntryPeriod(val label: String) { TODAY("เธงเธฑเธเธเธตเน"), WEEK("เธชเธฑเธเธ”เธฒเธซเนเธเธตเน"), MONTH("เน€เธ”เธทเธญเธเธเธตเน"), ALL("เธ—เธฑเนเธเธซเธกเธ”") }
private enum class EntryKind(val label: String) { ALL("เธ—เธฑเนเธเธซเธกเธ”"), INCOME("เธฃเธฒเธขเธฃเธฑเธ"), EXPENSE("เธฃเธฒเธขเธเนเธฒเธข") }

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
                    androidx.compose.foundation.Image(painterResource(R.drawable.rubjai_mascot), null, Modifier.size(132.dp))
                    if (migrationError == null) { CircularProgressIndicator(color = RubMint); Text("เธเธณเธฅเธฑเธเน€เธ•เธฃเธตเธขเธกเธเนเธญเธเธฃเธฑเธเธเนเธฒเธข 2.0", color = RubCream) }
                    else { Text(migrationError.orEmpty(), color = RubCoral); Button(onClick = { migrationError = null; authRepository.ensureCleanStartV2 { error -> migrationError = error; migrationReady = error == null } }) { Text("เธฅเธญเธเธญเธตเธเธเธฃเธฑเนเธ") } }
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
                androidx.work.WorkInfo.State.RUNNING -> { syncing = true; syncScanned = info.progress.getInt("scanned", 0) }
                androidx.work.WorkInfo.State.SUCCEEDED -> { syncing = false; pendingSlips = PendingSlipStore.load(context); val found = info.outputData.getInt("found", 0); message = if (found > 0) "เธชเนเธเธเน€เธชเธฃเนเธ เธเธ $found เธฃเธฒเธขเธเธฒเธฃ เธเธฃเธธเธ“เธฒเธ•เธฃเธงเธเนเธฅเธฐเธญเธเธธเธกเธฑเธ•เธด" else "เธชเนเธเธเน€เธชเธฃเนเธ เนเธกเนเธเธเธชเธฅเธดเธเธเธเธฒเธเธฒเธฃเธซเธฃเธทเธญเธงเธญเธฅเน€เธฅเนเธ•เนเธซเธกเนเธเธญเธเธงเธฑเธเธเธตเน"; syncWorkId = null; break }
                androidx.work.WorkInfo.State.FAILED, androidx.work.WorkInfo.State.CANCELLED -> { syncing = false; message = "เธเธดเธเนเธกเนเธชเธณเน€เธฃเนเธ เธเธฃเธธเธ“เธฒเธ•เธฃเธงเธเธชเธดเธ—เธเธดเนเธฃเธนเธเธ เธฒเธเนเธฅเนเธงเธฅเธญเธเนเธซเธกเน"; syncWorkId = null; break }
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
                    if (parsed.amount.toDoubleOrNull()?.let { it > 0 } == true) draft = parsed.copy(slipUri = uri.toString()) else message = "เธญเนเธฒเธเธขเธญเธ”เธเธฒเธเธฃเธนเธเนเธกเนเธเธ เธเธฃเธธเธ“เธฒเน€เธฅเธทเธญเธเธ เธฒเธเธชเธฅเธดเธเธ—เธตเนเธเธฑเธ”เน€เธเธ"
                    busy = false
                    recognizer.close()
                }
                .addOnFailureListener { error -> message = "เธญเนเธฒเธเธชเธฅเธดเธเนเธกเนเธชเธณเน€เธฃเนเธ: ${error.localizedMessage}"; busy = false; recognizer.close() }
        }
    }
    val scanPermission = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    val startKPlusSync = { syncing = true; syncScanned = 0; syncWorkId = KPlusSyncManager.syncNow(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startKPlusSync()
        else message = "เธ•เนเธญเธเธญเธเธธเธเธฒเธ•เธเธฒเธฃเน€เธเนเธฒเธ–เธถเธเธฃเธนเธเธเธถเธเธเธฐเธชเนเธเธเธชเธฅเธดเธเธเธเธฒเธเธฒเธฃเนเธฅเธฐเธงเธญเธฅเน€เธฅเนเธ•เธเธญเธเธงเธฑเธเธเธตเนเนเธ”เน"
    }
    LaunchedEffect(syncConsent) {
        if (syncConsent && !syncing && ContextCompat.checkSelfPermission(context, scanPermission) == PackageManager.PERMISSION_GRANTED) {
            startKPlusSync()
        }
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = RubInk,
            topBar = { if (mainTab == 0) RubJaiTopBar(authRepository.auth.currentUser?.displayName.orEmpty()) { mainTab = 1 } },
            bottomBar = {
                NavigationBar(containerColor = RubCream) {
                    NavigationBarItem(mainTab == 0, { mainTab = 0 }, { Icon(Icons.Default.Home, null) }, label = { Text("เธซเธเนเธฒเธซเธฅเธฑเธ") })
                    NavigationBarItem(mainTab == 1, { mainTab = 1 }, { Icon(Icons.Default.AccountCircle, null) }, label = { Text("เธเธฑเธเธเธตเธเธญเธเธเธฑเธ") })
                }
            },
            floatingActionButton = { if (mainTab == 0) FloatingActionButton(containerColor = Color(0xFFF27D6B), onClick = { draft = DraftTransaction(type = TransactionType.INCOME, source = "manual_income", category = "เธฃเธฒเธขเธฃเธฑเธ") }) { Icon(Icons.Default.Add, "เน€เธเธดเนเธกเธฃเธฒเธขเธฃเธฑเธ", tint = Color.White) } }
        ) { padding ->
            val visibleEntries = remember(entries, entryPeriod, entryKind) { entries.filterFor(entryPeriod, entryKind) }
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (mainTab == 0) {
                    item { SummaryCard(entries) }
                    item { HomeActions(busy, syncing, pendingSlips.size, { imagePicker.launch(arrayOf("image/*")) }, { if (!syncConsent) showSyncConsent = true else if (ContextCompat.checkSelfPermission(context, scanPermission) == PackageManager.PERMISSION_GRANTED) startKPlusSync() else permissionLauncher.launch(scanPermission) }, { pendingSlips = PendingSlipStore.load(context); showPending = true }, { draft = DraftTransaction(type = TransactionType.INCOME, source = "manual_income", category = "เธฃเธฒเธขเธฃเธฑเธ") }) }
                    item { KPlusSyncStatus(syncing, syncScanned, syncConsent) { KPlusSyncManager.revoke(context); syncConsent = false } }
                    item { Text("เธฃเธฒเธขเธเธฒเธฃเธเธญเธเธเธธเธ“", style = MaterialTheme.typography.titleLarge, color = RubCream, fontWeight = FontWeight.Bold); EntryFilters(entryPeriod, entryKind, { entryPeriod = it }, { entryKind = it }) }
                    item { SpendingOverview(entries.filterFor(entryPeriod, EntryKind.EXPENSE), entryPeriod) }
                    if (visibleEntries.isEmpty()) item { Card(colors = CardDefaults.cardColors(containerColor = RubPanel)) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("เธขเธฑเธเนเธกเนเธกเธตเธฃเธฒเธขเธเธฒเธฃเนเธเธเนเธงเธเธเธตเน", color = RubCream, fontWeight = FontWeight.SemiBold); Text("เน€เธเธดเนเธกเธฃเธฒเธขเธฃเธฑเธเธซเธฃเธทเธญเน€เธฅเธทเธญเธเธชเธฅเธดเธเธฃเธฒเธขเธเนเธฒเธข", color = RubMuted, style = MaterialTheme.typography.bodySmall) } } }
                    items(visibleEntries, key = { it.id }) { EntryRow(it) { selectedEntry = it } }
                } else item { UserHub(authRepository.auth.currentUser?.displayName.orEmpty(), entries.size, { showProfile = true }, { showDebts = true }, { mainTab = 0 }, { showSyncConsent = true }) }
            }
        }
        draft?.let { current -> TransactionDialog(current, onDismiss = { draft = null }, onSave = { saved -> busy = true; repository.add(saved, onDone = { error -> busy = false; if (error == null && saved.slipUri.isNotBlank()) LocalSlipLinkStore.put(context, TransactionRepository.documentIdFor(saved), saved.slipUri); message = error ?: "เธเธฑเธเธ—เธถเธเนเธฅเนเธง" }); draft = null }) }
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
        message?.let { AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("เธ•เธเธฅเธ") } }, text = { Text(it) }) }
        if (showSyncConsent) AlertDialog(onDismissRequest = { showSyncConsent = false }, title = { Text("เธขเธดเธเธขเธญเธกเธชเนเธเธเธชเธฅเธดเธเธงเธฑเธเธเธตเน?") }, text = { Text("RubJai เธเธฐเธญเนเธฒเธเธฃเธนเธเธ—เธตเนเน€เธเธดเนเธกเนเธเธงเธฑเธเธเธตเนเน€เธเธทเนเธญเธซเธฒเธชเธฅเธดเธเธเธเธฒเธเธฒเธฃเนเธฅเธฐเธงเธญเธฅเน€เธฅเนเธ•เธ”เนเธงเธข OCR เธเธเน€เธเธฃเธทเนเธญเธ เธเธฅเธเธฐเธญเธขเธนเนเนเธเธเธดเธงเธฃเธญเธญเธเธธเธกเธฑเธ•เธดเนเธฅเธฐเธขเธฑเธเนเธกเนเธเธฑเธเธ—เธถเธเธเธเธเธงเนเธฒเธเธธเธ“เธเธฐเธขเธทเธเธขเธฑเธ") }, confirmButton = { Button(onClick = { KPlusSyncManager.setConsent(context, true); syncConsent = true; showSyncConsent = false; if (ContextCompat.checkSelfPermission(context, scanPermission) == PackageManager.PERMISSION_GRANTED) startKPlusSync() else permissionLauncher.launch(scanPermission) }) { Text("เธขเธดเธเธขเธญเธกเนเธฅเธฐเธชเนเธเธ") } }, dismissButton = { TextButton(onClick = { showSyncConsent = false }) { Text("เธขเธฑเธเนเธกเนเธขเธดเธเธขเธญเธก") } })
        if (showPending) PendingSlipDialog(pendingSlips, onClose = { showPending = false }, onReview = { pendingToReview = it }, onReject = { pending -> PendingSlipStore.remove(context, pending.id); pendingSlips = PendingSlipStore.load(context); if (pendingSlips.isEmpty()) showPending = false })
        selectedEntry?.let { item -> TransactionDetailDialog(item, onDismiss = { selectedEntry = null }, onUpdate = { draftValue -> repository.update(item, draftValue) { error -> message = error ?: "เนเธเนเนเธเนเธฅเนเธง"; if (error == null) selectedEntry = null } }, onDelete = { repository.delete(item) { error -> message = error ?: "เธฅเธเธฃเธฒเธขเธเธฒเธฃเนเธฅเนเธง"; if (error == null) { LocalSlipLinkStore.remove(context, item.id); selectedEntry = null } } }) }
        availableUpdate?.let { update ->
            AlertDialog(
                onDismissRequest = { if (updateProgress == null) availableUpdate = null },
                title = { Text("RubJai ${update.version} เธเธฃเนเธญเธกเธญเธฑเธเน€เธ”เธ•") },
                text = {
                    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        Text(update.releaseNotes)
                        updateProgress?.let { progress ->
                            Spacer(Modifier.height(16.dp)); LinearProgressIndicator({ progress }, Modifier.fillMaxWidth()); Text("เธเธณเธฅเธฑเธเธ”เธฒเธงเธเนเนเธซเธฅเธ” ${(progress * 100).toInt()}%")
                        }
                        updateMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = { Button(enabled = updateProgress == null, onClick = {
                    if (!updateManager.canRequestInstall(context)) { updateMessage = "เธญเธเธธเธเธฒเธ•เธ•เธดเธ”เธ•เธฑเนเธเธเธฒเธเนเธซเธฅเนเธเธเธตเน เนเธฅเนเธงเธเธฅเธฑเธเธกเธฒเธเธ”เธ”เธฒเธงเธเนเนเธซเธฅเธ”เธญเธตเธเธเธฃเธฑเนเธ"; updateManager.openInstallPermission(context) }
                    else { updateProgress = 0f; updateManager.download(context, update, { updateProgress = it }) { uri -> updateProgress = null; if (uri != null) updateManager.launchInstaller(context, uri) else updateMessage = "เธ”เธฒเธงเธเนเนเธซเธฅเธ”เนเธกเนเธชเธณเน€เธฃเนเธ เธเธฃเธธเธ“เธฒเธฅเธญเธเนเธซเธกเน" } }
                }) { Text("เธ”เธฒเธงเธเนเนเธซเธฅเธ”เธญเธฑเธเน€เธ”เธ•") } },
                dismissButton = { if (updateProgress == null) TextButton(onClick = { availableUpdate = null }) { Text("เธ เธฒเธขเธซเธฅเธฑเธ") } },
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
        Triple("เธชเธงเธฑเธชเธ”เธต เน€เธฃเธฒเธเธทเธญเธเนเธญเธเธฃเธฑเธเธเนเธฒเธข", "เน€เธฃเธดเนเธกเธ”เธนเนเธฅเน€เธเธดเธเธ—เธธเธเธงเธฑเธเนเธซเนเน€เธเนเธฒเนเธเธเนเธฒเธขเธเธถเนเธ เนเธ”เธขเนเธกเนเธ•เนเธญเธเธเธณเธ•เธฑเธงเน€เธฅเธเธ—เธฑเนเธเธซเธกเธ”เน€เธญเธ", "เธ—เธณเธเธงเธฒเธกเธฃเธนเนเธเธฑเธเธเธฑเธ"),
        Triple("เธญเนเธฒเธเธชเธฅเธดเธ เนเธฅเนเธงเธชเธฃเนเธฒเธเธฃเธฒเธขเธเธฒเธฃเนเธซเน", "เธฃเธญเธเธฃเธฑเธเธชเธฅเธดเธเธเธเธฒเธเธฒเธฃเนเธฅเธฐเธงเธญเธฅเน€เธฅเนเธ•เธซเธฅเธฒเธขเธฃเธนเธเนเธเธ เธเธฃเนเธญเธกเธเธฑเธเธชเธฅเธดเธเธเนเธณเธเนเธญเธเธเธฑเธเธ—เธถเธ", "เธ•เนเธญเนเธ"),
        Triple("เน€เธซเนเธเธเธฑเธ”เธงเนเธฒเน€เธเธดเธเนเธเธ—เธฒเธเนเธซเธ", "เธ”เธนเธฃเธฒเธขเธฃเธฑเธ เธฃเธฒเธขเธเนเธฒเธข เนเธฅเธฐเธ เธฒเธเธฃเธงเธกเธ•เธฒเธกเธเนเธงเธเน€เธงเธฅเธฒ เนเธฅเนเธงเน€เธฅเธทเธญเธเธซเธกเธงเธ”เนเธซเนเธ•เธฃเธเธเธฑเธเธเธตเธงเธดเธ•เธเธญเธเธเธธเธ“", "เธ•เนเธญเนเธ"),
        Triple("เธเนเธญเธ•เธเธฅเธเธ—เธตเนเธญเนเธฒเธเน€เธเนเธฒเนเธเนเธ”เน", "เธเนเธญเธเธฃเธฑเธเธเนเธฒเธขเธเนเธงเธขเธญเนเธฒเธเธเนเธญเธเธงเธฒเธกเธเธฒเธเธชเธฅเธดเธ เธเธฅเธญเธฒเธเธเธฅเธฒเธ”เน€เธเธฅเธทเนเธญเธเนเธ”เน เธเธธเธ“เธเธถเธเธ•เธฃเธงเธเนเธฅเธฐเนเธเนเนเธเธเนเธญเธกเธนเธฅเธเนเธญเธเธเธฑเธเธ—เธถเธเนเธ”เนเน€เธชเธกเธญ", "เธขเธญเธกเธฃเธฑเธเนเธฅเธฐเธ•เนเธญเนเธ"),
        Triple("เธเนเธญเธกเธนเธฅเธเธญเธเธเธธเธ“เน€เธเนเธเธเธญเธเธเธธเธ“", "เธฃเธนเธเธ–เธนเธเธญเนเธฒเธเธเธเน€เธเธฃเธทเนเธญเธเนเธฅเธฐเนเธกเนเธญเธฑเธเนเธซเธฅเธ”เนเธเน€เธเนเธเนเธ Firebase เน€เธฃเธฒเน€เธเนเธเน€เธเธเธฒเธฐเธเนเธญเธกเธนเธฅเธฃเธฒเธขเธเธฒเธฃเธ—เธตเนเธเธธเธ“เธขเธทเธเธขเธฑเธ", "เธขเธญเธกเธฃเธฑเธเนเธฅเธฐเธ•เนเธญเนเธ"),
        Triple("เธญเธเธธเธเธฒเธ•เธญเนเธฒเธเธฃเธนเธเน€เธกเธทเนเธญเธเธธเธ“เธชเธฑเนเธ", "เธชเธดเธ—เธเธดเนเธฃเธนเธเนเธเนเธ•เธญเธเน€เธฅเธทเธญเธเธชเธฅเธดเธเธซเธฃเธทเธญเธเนเธเธซเธฒเธฃเธนเธเนเธซเธกเนเน€เธ—เนเธฒเธเธฑเนเธ เธเธธเธ“เธขเธเน€เธฅเธดเธเธชเธดเธ—เธเธดเนเธ เธฒเธขเธซเธฅเธฑเธเนเธ”เน", "เธ•เนเธญเนเธ"),
        Triple("เธเธฃเนเธญเธกเน€เธฃเธดเนเธกเนเธฅเนเธง", "เธชเธกเธฑเธเธฃเธ”เนเธงเธขเน€เธเธญเธฃเนเธกเธทเธญเธ–เธทเธญเนเธฅเธฐ OTP เนเธฅเนเธงเธเนเธญเธเธฃเธฑเธเธเนเธฒเธขเธเธฐเธชเธฃเนเธฒเธเธเธทเนเธเธ—เธตเนเธเนเธญเธกเธนเธฅเธชเนเธงเธเธ•เธฑเธงเนเธซเนเธเธธเธ“", "เธชเธกเธฑเธเธฃเธซเธฃเธทเธญเน€เธเนเธฒเธชเธนเนเธฃเธฐเธเธ"),
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
                        if (animatedPage > 0) IconButton(onClick = { page-- }, Modifier.align(Alignment.TopStart).padding(12.dp)) { Icon(Icons.Default.ArrowBack, "เธขเนเธญเธเธเธฅเธฑเธ", tint = RubInk) }
                    }
                    Column(Modifier.fillMaxWidth().weight(.54f).padding(horizontal = 28.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(info.first, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = RubCream, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(info.second, style = MaterialTheme.typography.bodyLarge, color = RubMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        when (animatedPage) {
                            3 -> Row(Modifier.fillMaxWidth().padding(top = 18.dp).clickable { termsAccepted = !termsAccepted }, verticalAlignment = Alignment.CenterVertically) { Checkbox(termsAccepted, { termsAccepted = it }); Text("เธเธฑเธเธญเนเธฒเธเนเธฅเธฐเธขเธญเธกเธฃเธฑเธเธเนเธญเธ•เธเธฅเธ", color = RubCream) }
                            4 -> Row(Modifier.fillMaxWidth().padding(top = 18.dp).clickable { privacyAccepted = !privacyAccepted }, verticalAlignment = Alignment.CenterVertically) { Checkbox(privacyAccepted, { privacyAccepted = it }); Text("เธเธฑเธเน€เธเนเธฒเนเธเนเธฅเธฐเธขเธญเธกเธฃเธฑเธเธเธฒเธฃเธเธฑเธ”เธเธฒเธฃเธเนเธญเธกเธนเธฅ", color = RubCream) }
                            5 -> OutlinedButton(onClick = { permissionLauncher.launch(photoPermission) }, Modifier.fillMaxWidth().padding(top = 18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = RubMint)) { Icon(Icons.Default.ImageSearch, null); Spacer(Modifier.width(8.dp)); Text(if (photoGranted) "เธญเธเธธเธเธฒเธ•เธชเธดเธ—เธเธดเนเธฃเธนเธเนเธฅเนเธง" else "เน€เธฅเธทเธญเธเธชเธดเธ—เธเธดเนเธเธฒเธฃเน€เธเนเธฒเธ–เธถเธเธฃเธนเธ") }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(pages.size) { index -> Surface(Modifier.size(if (index == page) 28.dp else 8.dp, 8.dp), RoundedCornerShape(50), color = if (index == page) RubCoral else RubMuted.copy(alpha = .45f)) {} } }
                    }
                }
            }
            Surface(color = RubCream) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (page > 0) TextButton(onClick = { page-- }, Modifier.weight(.35f)) { Text("เธขเนเธญเธเธเธฅเธฑเธ", color = RubInk, fontWeight = FontWeight.Bold) }
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
                    else -> androidx.compose.foundation.Image(painterResource(R.drawable.rubjai_mascot), "เธเนเธญเธเธฃเธฑเธเธเนเธฒเธข", Modifier.size(160.dp), contentScale = ContentScale.Fit)
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
            Column { Text("เธเนเธญเธเธฃเธฑเธเธเนเธฒเธข", color = RubInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text(if (name.isBlank()) "เธเธฃเนเธญเธกเธเนเธงเธขเธ”เธนเนเธฅเน€เธเธดเธเนเธฅเนเธง" else "เธชเธงเธฑเธชเธ”เธต $name", style = MaterialTheme.typography.bodySmall, color = RubInk.copy(alpha = .75f)) }
            Spacer(Modifier.weight(1f)); IconButton(onClick = profile) { Icon(Icons.Default.AccountCircle, "เธเธฑเธเธเธตเธเธญเธเธเธฑเธ", tint = RubInk) }
        }
    }
}

@Composable
private fun UserHub(name: String, entryCount: Int, editProfile: () -> Unit, debts: () -> Unit, home: () -> Unit, permission: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(250.dp).background(Brush.verticalGradient(listOf(RubCoral, RubCream)), RoundedCornerShape(28.dp))) {
            Column(Modifier.padding(24.dp)) {
                Text("เธชเธงเธฑเธชเธ”เธต ${name.ifBlank { "เธเธธเธ“" }}", color = RubInk, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("เธเนเธญเธกเธนเธฅเนเธฅเธฐเธเธฒเธฃเธ•เธฑเนเธเธเนเธฒเธเธญเธเธเธธเธ“", color = RubInk.copy(alpha = .72f))
            }
            androidx.compose.foundation.Image(painterResource(R.drawable.rubjai_mascot), null, Modifier.align(Alignment.BottomEnd).padding(12.dp).size(150.dp))
        }
        Card(colors = CardDefaults.cardColors(containerColor = RubPanel), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("เธ เธฒเธเธฃเธงเธกเธเธฑเธเธเธต", color = RubCream, fontWeight = FontWeight.Bold)
                Text("$entryCount เธฃเธฒเธขเธเธฒเธฃเธ—เธตเนเธเธฑเธเธ—เธถเธเนเธงเน", color = RubMint, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
        }
        UserHubRow(Icons.Default.AccountCircle, "เธเนเธญเธกเธนเธฅเธชเนเธงเธเธ•เธฑเธงเนเธฅเธฐเธเธฑเธเธเธต", editProfile)
        UserHubRow(Icons.Default.ImageSearch, "เธชเธดเธ—เธเธดเนเธญเนเธฒเธเธฃเธนเธเนเธฅเธฐเธเธฒเธฃเธชเนเธเธ", permission)
        UserHubRow(Icons.Default.CreditCard, "เนเธเธเธเธฅเธ”เธซเธเธตเน", debts)
        UserHubRow(Icons.Default.Home, "เธเธฅเธฑเธเนเธเธ”เธนเธฃเธฒเธขเธเธฒเธฃ", home)
    }
}

@Composable
private fun UserHubRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, action: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = action), colors = CardDefaults.cardColors(containerColor = RubPanel), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = RubMint, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(16.dp)); Text(title, Modifier.weight(1f), color = RubCream, fontWeight = FontWeight.SemiBold); Text("โ€บ", color = RubCoral, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun HomeActions(busy: Boolean, syncing: Boolean, pending: Int, scan: () -> Unit, sync: () -> Unit, review: () -> Unit, income: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(Modifier.fillMaxWidth(), color = RubEntryNavy, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, RubBlue.copy(alpha = 0.55f))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(48.dp), color = RubEntryCard, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, RubBlue.copy(alpha = 0.35f))) {
                        Icon(Icons.Default.ReceiptLong, null, tint = RubBlue, modifier = Modifier.padding(11.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("โหลดสลิปวันนี้", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                syncing -> "กำลังซิงค์รูปวันนี้"
                                pending > 0 -> "พบ $pending รายการ รอตรวจ"
                                else -> "ซิงค์อัตโนมัติเมื่อเปิดแอพถ้าอนุญาตแล้ว"
                            },
                            color = RubEntryMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (pending > 0) Surface(color = RubRed, shape = RoundedCornerShape(14.dp)) { Text("$pending", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Color.White, fontWeight = FontWeight.Black) }
                }
                if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = RubBlue, trackColor = Color.White.copy(alpha = 0.15f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = scan, enabled = !busy, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = RubBlue, contentColor = Color.White)) { Icon(Icons.Default.ImageSearch, null); Spacer(Modifier.width(6.dp)); Text(if (busy) "กำลังอ่าน" else "เลือกสลิป") }
                    OutlinedButton(onClick = sync, enabled = !syncing, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, RubBlue)) { Text(if (syncing) "กำลังซิงค์" else "ซิงค์ใหม่", color = RubBlue, fontWeight = FontWeight.Bold) }
                }
                if (pending > 0) Button(onClick = review, colors = ButtonDefaults.buttonColors(containerColor = RubEntryYellow, contentColor = RubEntryNavy), modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(8.dp)) { Text("ตรวจรายการรอบันทึก", fontWeight = FontWeight.Black) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = income, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, RubMint)) { Icon(Icons.Default.Add, null, tint = RubMint); Spacer(Modifier.width(4.dp)); Text("เพิ่มรายรับ", color = RubMint) }
            OutlinedButton(onClick = sync, enabled = !syncing, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, RubCoral)) { Icon(Icons.Default.ArrowUpward, null, tint = RubCoral); Spacer(Modifier.width(4.dp)); Text("เพิ่มรายจ่าย", color = RubCoral) }
        }
    }
}

@Composable
private fun KPlusSyncStatus(syncing: Boolean, scanned: Int, consented: Boolean, revoke: () -> Unit) {
    if (!syncing && !consented) return
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F1FF))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (syncing) { Text("เธเนเธญเธเธฃเธฑเธเธเนเธฒเธขเธเธณเธฅเธฑเธเธซเธฒเธชเธฅเธดเธเธเธเธฒเธเธฒเธฃ/เธงเธญเธฅเน€เธฅเนเธ•", fontWeight = FontWeight.Bold); LinearProgressIndicator(Modifier.fillMaxWidth()); Text(if (scanned == 0) "เธเธณเธฅเธฑเธเน€เธ•เธฃเธตเธขเธกเธฃเธนเธโ€ฆ" else "เธ•เธฃเธงเธเนเธฅเนเธง $scanned เธฃเธนเธ โ€ข เธขเธฑเธเนเธกเนเธเธฑเธเธ—เธถเธเธฃเธฒเธขเธเธฒเธฃ", style = MaterialTheme.typography.bodySmall) }
            else Row(verticalAlignment = Alignment.CenterVertically) { Text("เธญเธเธธเธเธฒเธ•เธชเนเธเธเน€เธกเธทเนเธญเธเธธเธ“เธเธ”เน€เธ—เนเธฒเธเธฑเนเธ", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); TextButton(onClick = revoke) { Text("เธขเธเน€เธฅเธดเธเธชเธดเธ—เธเธดเน") } }
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
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "เธเธฅเธฑเธ", tint = Color(0xFF0B5D5B)) }
                    Text("เธฃเธฒเธขเธฅเธฐเน€เธญเธตเธขเธ”เธฃเธฒเธขเธเธฒเธฃ", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Color(0xFF0B5D5B), fontWeight = FontWeight.Black)
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "เธฅเธเธฃเธฒเธขเธเธฒเธฃ", tint = MaterialTheme.colorScheme.error) }
                } }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(item.type == "EXPENSE", {}, { Text("เธฃเธฒเธขเธเนเธฒเธข") }, modifier = Modifier.weight(1f))
                    FilterChip(item.type == "INCOME", {}, { Text("เธฃเธฒเธขเธฃเธฑเธ") }, modifier = Modifier.weight(1f))
                    FilterChip(false, {}, { Text("เธขเนเธฒเธขเน€เธเธดเธ") }, enabled = false, modifier = Modifier.weight(1f))
                }
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { OutlinedTextField(occurredAt, { occurredAt = it.take(50) }, label = { Text("เธงเธฑเธเธ—เธตเน/เน€เธงเธฅเธฒ") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.ReceiptLong, null) }) }
                    item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B5D5B)), shape = RoundedCornerShape(24.dp)) { OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("เธเธณเธเธงเธเน€เธเธดเธ") }, suffix = { Text("เธเธฒเธ—") }, textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), singleLine = true, modifier = Modifier.fillMaxWidth().padding(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = Color(0xFF76C7B7), unfocusedLabelColor = Color(0xFFB6DAD3), focusedBorderColor = Color(0xFF76C7B7), unfocusedBorderColor = Color(0xFF76C7B7), focusedSuffixColor = Color.White, unfocusedSuffixColor = Color.White)) } }
                    item { OutlinedTextField(title, { title = it.take(200) }, label = { Text("เธเธทเนเธญเธเธนเนเธฃเธฑเธ/เธเธทเนเธญเธฃเธฒเธขเธเธฒเธฃ") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("เน€เธฅเธทเธญเธเธซเธกเธงเธ” / เนเธ—เนเธ", color = Color(0xFF0B5D5B), fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { categories.forEach { FilterChip(category == it, { category = it }, { Text(it) }) } }; Text("เน€เธเธดเนเธกเธซเธฃเธทเธญเธฅเธเธซเธกเธงเธ”เนเธ”เนเธเธฒเธเธซเธเนเธฒเนเธเธฃเนเธเธฅเน", style = MaterialTheme.typography.bodySmall, color = Color.Gray) } } }
                    item { OutlinedTextField(remark, { remark = it.take(500) }, label = { Text("เน€เธเธดเนเธกเนเธเนเธ•") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
                    if (slipUri.isNotBlank()) item { SlipSourceCard(slipUri, title, occurredAt) { showSlip = true } }
                    if (item.slipFingerprint.isNotBlank()) item { Text("เธฃเธนเธเธชเธฅเธดเธเธขเธฑเธเธญเธขเธนเนเนเธเน€เธเธฃเธทเนเธญเธเน€เธ”เธดเธกเนเธฅเธฐเนเธกเนเธ–เธนเธเธญเธฑเธเนเธซเธฅเธ” เธซเธฒเธเธฅเธเธซเธฃเธทเธญเธขเนเธฒเธขเธฃเธนเธ เนเธญเธเธเธฐเน€เธเธดเธ”เธ”เธนเนเธกเนเนเธ”เน", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                }
                Surface(color = Color.White, tonalElevation = 4.dp) { Button(enabled = amount.toDoubleOrNull()?.let { it > 0 } == true, onClick = { onUpdate(DraftTransaction(amount, title, TransactionType.valueOf(item.type), item.source, category = category, remark = remark, occurredAt = occurredAt, slipFingerprint = item.slipFingerprint)) }, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(54.dp), shape = RoundedCornerShape(20.dp)) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("เธเธฑเธเธ—เธถเธเธเธฒเธฃเนเธเนเนเธ", fontWeight = FontWeight.Bold) } }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("เธฅเธเธฃเธฒเธขเธเธฒเธฃเธเธตเน?") }, text = { Text("เธขเธญเธ”เนเธฅเธฐเธเนเธญเธกเธนเธฅเธฃเธฒเธขเธเธฒเธฃเธเธฐเธ–เธนเธเธฅเธเธเธฒเธเธเธฑเธเธเธตเธเธญเธเธเธธเธ“") }, confirmButton = { Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("เธฅเธ") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("เธขเธเน€เธฅเธดเธ") } })
    if (showSlip) FullScreenSlipDialog(slipUri) { showSlip = false }
}

@Composable
private fun SlipSourceCard(uri: String, title: String, occurredAt: String, open: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) { bitmap = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream) }.getOrNull() } }
    Card(Modifier.fillMaxWidth().clickable(onClick = open), colors = CardDefaults.cardColors(containerColor = Color(0xFFE5F2EF))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("เธเนเธญเธกเธนเธฅเธเธฒเธเธชเธฅเธดเธ", fontWeight = FontWeight.Bold, color = Color(0xFF0B5D5B)); Text(title.ifBlank { "เนเธกเนเธเธเธเธทเนเธญเธเธนเนเธฃเธฑเธ" }); if (occurredAt.isNotBlank()) Text(occurredAt, style = MaterialTheme.typography.bodySmall, color = Color.Gray); Text("เนเธ•เธฐเน€เธเธทเนเธญเธ”เธนเธชเธฅเธดเธ", color = Color(0xFFF27D6B), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            if (bitmap != null) androidx.compose.foundation.Image(bitmap!!.asImageBitmap(), "เธฃเธนเธเธขเนเธญเธชเธฅเธดเธ", Modifier.size(92.dp), contentScale = ContentScale.Crop) else Surface(Modifier.size(92.dp), color = Color(0xFFCEE5DF), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.ImageSearch, null, Modifier.padding(28.dp), tint = Color(0xFF0B5D5B)) }
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
                bitmap?.let { androidx.compose.foundation.Image(it.asImageBitmap(), "เธฃเธนเธเธชเธฅเธดเธเธ•เนเธเธเธเธฑเธ", Modifier.fillMaxSize().padding(16.dp), contentScale = ContentScale.Fit) }
                if (bitmap == null && !unavailable) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                if (unavailable) Text("เน€เธเธดเธ”เธฃเธนเธเธ•เนเธเธเธเธฑเธเนเธกเนเนเธ”เน\nเธฃเธนเธเธญเธฒเธเธ–เธนเธเธฅเธเธซเธฃเธทเธญเธขเนเธฒเธขเธญเธญเธเธเธฒเธเน€เธเธฃเธทเนเธญเธ", color = Color.White, modifier = Modifier.align(Alignment.Center), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = close, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) { Text("เธเธดเธ”", color = Color.White) }
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
                if (parsed.amount.toDoubleOrNull()?.let { it > 0 } == true) draft = parsed else message = "เธญเนเธฒเธเธขเธญเธ”เธเธฒเธเธชเธฅเธดเธเนเธกเนเธเธ เธเธฃเธธเธ“เธฒเน€เธฅเธทเธญเธเธชเธฅเธดเธเธ—เธตเนเธเธฑเธ”เน€เธเธ"
                recognizer.close()
            }.addOnFailureListener { message = "เธญเนเธฒเธเธชเธฅเธดเธเนเธกเนเธชเธณเน€เธฃเนเธ"; recognizer.close() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Surface(color = Color(0xFF0B5D5B)) { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { if (selected != null) selected = null else onClose() }) { Icon(Icons.Default.ArrowBack, "เธเธฅเธฑเธ", tint = Color.White) }; Text(selected?.name ?: "เธฃเธฒเธขเธเธฒเธฃเธซเธเธตเน", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); if (selected == null) IconButton(onClick = { create = true }) { Icon(Icons.Default.Add, "เน€เธเธดเนเธกเธซเธเธตเน", tint = Color.White) } } }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (selected == null) {
                item { Text("เน€เธฅเธทเธญเธเธซเธเธตเนเน€เธเธทเนเธญเธ”เธนเธฃเธฒเธขเธฅเธฐเน€เธญเธตเธขเธ”เนเธฅเธฐเธเธฃเธฐเธงเธฑเธ•เธดเธเธณเธฃเธฐ", style = MaterialTheme.typography.titleMedium); Text("เธฃเธญเธเธฃเธฑเธเธซเธเธตเนเธซเธฅเธฒเธขเธเนเธญเธเนเธฅเธฐเนเธขเธเธเธฃเธฐเธงเธฑเธ•เธดเธเธญเธเนเธ•เนเธฅเธฐเธฃเธฒเธขเธเธฒเธฃ", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
                if (debts.isEmpty()) item { Card { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("เธขเธฑเธเนเธกเนเธกเธตเธฃเธฒเธขเธเธฒเธฃเธซเธเธตเน"); TextButton(onClick = { create = true }) { Text("+ เน€เธเธดเนเธกเธซเธเธตเนเธเนเธญเธเนเธฃเธ") } } } }
                items(debts, key = { it.id }) { DebtListCard(it) { selected = it } }
            } else item { DebtDetailCard(selected!!, repository, { target = selected; picker.launch(arrayOf("image/*")) }) { debt -> repository.delete(debt) { error -> message = error ?: "เธฅเธเธฃเธฒเธขเธเธฒเธฃเธซเธเธตเนเนเธฅเนเธง"; if (error == null) selected = null } } }
        }
    }
    if (create) CreateDebtDialog({ create = false }) { name, balance, interest -> repository.add(name, balance, interest) { message = it ?: "เน€เธเธดเนเธกเธซเธเธตเนเนเธฅเนเธง" }; create = false }
    draft?.let { parsed -> AlertDialog(onDismissRequest = { draft = null }, title = { Text("เธขเธทเธเธขเธฑเธเธ•เธฑเธ”เธขเธญเธ”เธซเธเธตเน") }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { Text(target?.name.orEmpty(), fontWeight = FontWeight.Bold); Text("เธฃเนเธฒเธ/เธเธนเนเธฃเธฑเธ: ${parsed.title.ifBlank { "เนเธกเนเธฃเธฐเธเธธ" }}"); Text("เธขเธญเธ” ${parsed.amount} เธเธฒเธ—", fontWeight = FontWeight.Bold); if (parsed.occurredAt.isNotBlank()) Text("เน€เธงเธฅเธฒ: ${parsed.occurredAt}"); Text("เธฃเธฐเธเธเธเธฐเธเธฑเธเธชเธฅเธดเธเธเนเธณเธเธฒเธเธเนเธญเธกเธนเธฅเนเธเธชเธฅเธดเธ") } }, confirmButton = { Button(onClick = { target?.let { repository.applySlip(it, parsed) { error -> message = error ?: "เธ•เธฑเธ”เธขเธญเธ”เธซเธเธตเนเนเธฅเนเธง" } }; draft = null }) { Text("เธขเธทเธเธขเธฑเธเธ•เธฑเธ”เธขเธญเธ”") } }, dismissButton = { TextButton(onClick = { draft = null }) { Text("เธขเธเน€เธฅเธดเธ") } }) }
    message?.let { AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("เธ•เธเธฅเธ") } }, text = { Text(it) }) }
}

@Composable private fun DebtListCard(debt: Debt, open: () -> Unit) {
    val progress = if (debt.originalBalance > 0) (1 - debt.remainingBalance / debt.originalBalance).toFloat().coerceIn(0f, 1f) else 0f
    Card(Modifier.fillMaxWidth().clickable(onClick = open), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row { Text(debt.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(money(debt.remainingBalance), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }; LinearProgressIndicator({ progress }, Modifier.fillMaxWidth()); Text("เธเธณเธฃเธฐเนเธฅเนเธง ${debt.paymentsMade} เธเธฃเธฑเนเธ โ€ข เนเธ•เธฐเน€เธเธทเนเธญเธ”เธนเธฃเธฒเธขเธฅเธฐเน€เธญเธตเธขเธ”", color = Color.Gray, style = MaterialTheme.typography.bodySmall) } }
}

@Composable private fun DebtDetailCard(debt: Debt, repository: DebtRepository, pay: () -> Unit, delete: (Debt) -> Unit) {
    val progress = if (debt.originalBalance > 0) (1 - debt.remainingBalance / debt.originalBalance).toFloat().coerceIn(0f, 1f) else 0f
    var payments by remember(debt.id) { mutableStateOf(emptyList<DebtPayment>()) }
    var confirmDelete by remember(debt.id) { mutableStateOf(false) }
    DisposableEffect(debt.id) { val registration = repository.observePayments(debt.id) { payments = it }; onDispose { registration.remove() } }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row { Text(debt.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(money(debt.remainingBalance), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }; LinearProgressIndicator({ progress }, Modifier.fillMaxWidth()); Text("เธเธณเธฃเธฐเนเธฅเนเธง ${(progress * 100).toInt()}% โ€ข เธฅเนเธฒเธชเธธเธ” ${money(debt.latestPayment)}"); Text(debt.estimatedMonths()?.let { if (it == 0) "เธเธดเธ”เธซเธเธตเนเนเธฅเนเธง" else "เธเธฒเธ”เธงเนเธฒเธเธฐเธซเธกเธ”เนเธเธเธฃเธฐเธกเธฒเธ“ $it เน€เธ”เธทเธญเธ" } ?: "เธขเธญเธ”เธฅเนเธฒเธชเธธเธ”เธขเธฑเธเนเธกเนเธเธญเธเธณเธเธงเธ“เธฃเธฐเธขเธฐเน€เธงเธฅเธฒ", fontWeight = FontWeight.SemiBold); Text(debt.encouragement(), color = Color(0xFF0B9B73)); Button(onClick = pay, modifier = Modifier.fillMaxWidth()) { Text("เน€เธฅเธทเธญเธเธชเธฅเธดเธเน€เธเธทเนเธญเธ•เธฑเธ”เธขเธญเธ”") }; if (payments.isNotEmpty()) { HorizontalDivider(); Text("เธเธฃเธฐเธงเธฑเธ•เธดเธเธณเธฃเธฐ", fontWeight = FontWeight.Bold); payments.forEach { DebtPaymentRow(it) } }; TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Default.Delete, null); Text("เธฅเธเธซเธเธตเนเธเนเธญเธเธเธตเน", color = MaterialTheme.colorScheme.error) } } }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("เธฅเธเธซเธเธตเนเธเนเธญเธเธเธตเน?") }, text = { Text("เธขเธญเธ”เธซเธเธตเนเนเธฅเธฐเธเธฃเธฐเธงเธฑเธ•เธดเธเธฒเธฃเธเธณเธฃเธฐเธ—เธฑเนเธเธซเธกเธ”เธเธญเธเธฃเธฒเธขเธเธฒเธฃเธเธตเนเธเธฐเธ–เธนเธเธฅเธ") }, confirmButton = { Button(onClick = { delete(debt); confirmDelete = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("เธฅเธ") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("เธขเธเน€เธฅเธดเธ") } })
}

@Composable private fun DebtPaymentRow(payment: DebtPayment) {
    val savedTime = payment.paidAt?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("th", "TH")).format(it) }.orEmpty()
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(payment.merchant.ifBlank { "เธเธณเธฃเธฐเธซเธเธตเน" }, fontWeight = FontWeight.SemiBold); Text(payment.occurredAt.ifBlank { savedTime }.ifBlank { "เนเธกเนเธเธเธงเธฑเธเธ—เธตเน/เน€เธงเธฅเธฒ" }, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; Text("-${money(payment.amount)}", color = Color(0xFFD84A3A), fontWeight = FontWeight.Bold) }
}

@Composable private fun CreateDebtDialog(dismiss: () -> Unit, save: (String, Double, Double) -> Unit) {
    var name by remember { mutableStateOf("") }; var balance by remember { mutableStateOf("") }; var interest by remember { mutableStateOf("0") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("เน€เธเธดเนเธกเธซเธเธตเน") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(name, { name = it }, label = { Text("เธเธทเนเธญเน€เธเนเธฒเธซเธเธตเน/เธฃเธฒเธขเธเธฒเธฃ") }); OutlinedTextField(balance, { balance = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("เธขเธญเธ”เธซเธเธตเนเธ•เธฑเนเธเธ•เนเธ") }); OutlinedTextField(interest, { interest = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("เธ”เธญเธเน€เธเธตเนเธขเธ•เนเธญเธเธต % (เธ–เนเธฒเนเธกเนเธกเธตเนเธชเน 0)") }) } }, confirmButton = { Button(enabled = name.isNotBlank() && balance.toDoubleOrNull()?.let { it > 0 } == true, onClick = { save(name, balance.toDouble(), interest.toDoubleOrNull() ?: 0.0) }) { Text("เธเธฑเธเธ—เธถเธ") } }, dismissButton = { TextButton(onClick = dismiss) { Text("เธขเธเน€เธฅเธดเธ") } })
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
                androidx.compose.foundation.Image(painterResource(R.drawable.rubjai_mascot), null, Modifier.size(190.dp), contentScale = ContentScale.Fit)
                Text(
                    text = "เน€เธงเธญเธฃเนเธเธฑเธ ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
                    color = RubInk.copy(alpha = .72f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Text("เธเนเธญเธเธฃเธฑเธเธเนเธฒเธข", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = RubCream)
            Text("เน€เธฃเธดเนเธกเธ”เธนเนเธฅเธฃเธฒเธขเธเธฒเธฃเธเธญเธเธเธธเธ“เธญเธขเนเธฒเธเน€เธเนเธเธชเนเธงเธเธ•เธฑเธง", color = RubMuted)
            Spacer(Modifier.height(24.dp))
            Card(colors = CardDefaults.cardColors(containerColor = RubPanel), shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Crossfade(verificationId == null, animationSpec = tween(360), label = "otp-step") { phoneStep ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (phoneStep) "เธชเธกเธฑเธเธฃเธซเธฃเธทเธญเน€เธเนเธฒเธชเธนเนเธฃเธฐเธเธ" else "เนเธชเน OTP 6 เธซเธฅเธฑเธ", color = RubCream, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (phoneStep) {
                            OutlinedTextField(name, { name = it.take(100) }, label = { Text("เธเธทเนเธญเธ—เธตเนเนเธชเธ”เธ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(10) }, label = { Text("เน€เธเธญเธฃเนเธกเธทเธญเธ–เธทเธญ 10 เธซเธฅเธฑเธ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Text("เธฃเธฐเธเธเธเธฐเธชเนเธเธฃเธซเธฑเธชเธ—เธฒเธ SMS เน€เธเธทเนเธญเธขเธทเธเธขเธฑเธเธงเนเธฒเน€เธเนเธเน€เธเธญเธฃเนเธเธญเธเธเธธเธ“", style = MaterialTheme.typography.bodySmall, color = RubMuted)
                            Button(enabled = !busy && name.isNotBlank() && phone.length == 10, colors = ButtonDefaults.buttonColors(containerColor = RubCoral, contentColor = RubInk), modifier = Modifier.fillMaxWidth().height(54.dp), onClick = {
                                busy = true; error = null
                                repository.requestPhoneOtp(activity, phone, { id, _ -> verificationId = id; busy = false }, finishCredential, { error = it; busy = false })
                            }) { Text(if (busy) "เธเธณเธฅเธฑเธเธชเนเธโ€ฆ" else "เธฃเธฑเธเธฃเธซเธฑเธช OTP") }
                            OutlinedButton(
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                onClick = {
                                    busy = true; error = null
                                    repository.startTrial { failure -> busy = false; error = failure }
                                },
                            ) { Text("เธ—เธ”เธฅเธญเธเนเธเนเธเธฒเธเธเนเธญเธ (เนเธกเนเนเธเน OTP)") }
                            Text("เนเธซเธกเธ”เธ—เธ”เธฅเธญเธเนเธเนเธเธฑเธเธเธตเธเธฑเนเธงเธเธฃเธฒเธงเนเธขเธเธเธฒเธเธเธฑเธเธเธตเธ—เธตเนเธขเธทเธเธขเธฑเธเธ”เนเธงเธขเน€เธเธญเธฃเนเนเธ—เธฃ", style = MaterialTheme.typography.bodySmall, color = RubMuted)
                    } else {
                            Text("เธชเนเธเธฃเธซเธฑเธชเนเธเธ—เธตเน $phone เนเธฅเนเธง", color = RubMuted)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(6) { index -> Surface(Modifier.weight(1f).aspectRatio(1f), RoundedCornerShape(12.dp), color = RubInk) { Box(contentAlignment = Alignment.Center) { Text(otp.getOrNull(index)?.toString().orEmpty(), color = RubMint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) } } } }
                            OutlinedTextField(otp, { otp = it.filter(Char::isDigit).take(6) }, label = { Text("เธฃเธซเธฑเธช OTP") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Button(enabled = !busy && otp.length == 6, colors = ButtonDefaults.buttonColors(containerColor = RubMint, contentColor = RubInk), modifier = Modifier.fillMaxWidth().height(54.dp), onClick = { busy = true; repository.confirmPhoneOtp(verificationId!!, otp, name) { busy = false; error = it } }) { Text(if (busy) "เธเธณเธฅเธฑเธเธ•เธฃเธงเธโ€ฆ" else "เธขเธทเธเธขเธฑเธเนเธฅเธฐเน€เธเนเธฒเนเธเนเธเธฒเธ") }
                            TextButton(onClick = { verificationId = null; otp = ""; error = null }) { Text("เน€เธเธฅเธตเนเธขเธเน€เธเธญเธฃเนเธกเธทเธญเธ–เธทเธญ") }
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
                title = { Text("RubJai ${update.version} เธเธฃเนเธญเธกเธญเธฑเธเน€เธ”เธ•") },
                text = {
                    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        Text(update.releaseNotes)
                        updateProgress?.let { progress ->
                            Spacer(Modifier.height(16.dp)); LinearProgressIndicator({ progress }, Modifier.fillMaxWidth()); Text("เธเธณเธฅเธฑเธเธ”เธฒเธงเธเนเนเธซเธฅเธ” ${(progress * 100).toInt()}%")
                        }
                        updateMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = { Button(enabled = updateProgress == null, onClick = {
                    if (!updateManager.canRequestInstall(context)) { updateMessage = "เธญเธเธธเธเธฒเธ•เธ•เธดเธ”เธ•เธฑเนเธเธเธฒเธเนเธซเธฅเนเธเธเธตเน เนเธฅเนเธงเธเธฅเธฑเธเธกเธฒเธเธ”เธ”เธฒเธงเธเนเนเธซเธฅเธ”เธญเธตเธเธเธฃเธฑเนเธ"; updateManager.openInstallPermission(context) }
                    else { updateProgress = 0f; updateManager.download(context, update, { updateProgress = it }) { uri -> updateProgress = null; if (uri != null) updateManager.launchInstaller(context, uri) else updateMessage = "เธ”เธฒเธงเธเนเนเธซเธฅเธ”เนเธกเนเธชเธณเน€เธฃเนเธ เธเธฃเธธเธ“เธฒเธฅเธญเธเนเธซเธกเน" } }
                }) { Text("เธ”เธฒเธงเธเนเนเธซเธฅเธ”เธญเธฑเธเน€เธ”เธ•") } },
                dismissButton = { if (updateProgress == null) TextButton(onClick = { availableUpdate = null }) { Text("เธ เธฒเธขเธซเธฅเธฑเธ") } },
            )
        }
    }
}

@Composable
private fun VerifyEmailScreen(repository: AuthRepository, email: String, onVerified: () -> Unit) {
    var message by remember { mutableStateOf("เธชเนเธเธฅเธดเธเธเนเธขเธทเธเธขเธฑเธเนเธเธ—เธตเน $email เนเธฅเนเธง") }; var busy by remember { mutableStateOf(false) }
    LaunchedEffect(email) {
        while (true) {
            delay(2_500)
            repository.refreshUser { verified, _ -> if (verified) onVerified() }
        }
    }
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF4F7FA)) { Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("เธขเธทเธเธขเธฑเธเธญเธตเน€เธกเธฅ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text(message); Spacer(Modifier.height(20.dp)); CircularProgressIndicator(); Spacer(Modifier.height(8.dp)); Text("เธเธณเธฅเธฑเธเธ•เธฃเธงเธเธชเธญเธเธญเธฑเธ•เนเธเธกเธฑเธ•เธดโ€ฆ", color = Color.Gray)
        OutlinedButton(enabled = !busy, onClick = { repository.resendVerification { message = it ?: "เธชเนเธเธฅเธดเธเธเนเธขเธทเธเธขเธฑเธเนเธซเธกเนเนเธฅเนเธง" } }, modifier = Modifier.fillMaxWidth()) { Text("เธชเนเธเธญเธตเน€เธกเธฅเธขเธทเธเธขเธฑเธเธญเธตเธเธเธฃเธฑเนเธ") }
        TextButton(onClick = { repository.signOut() }) { Text("เธเธฅเธฑเธเนเธเธซเธเนเธฒเน€เธเนเธฒเธชเธนเนเธฃเธฐเธเธ") }
    } }
}

@Composable
private fun ProfileDialog(repository: AuthRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var message by remember { mutableStateOf<String?>(null) }; var confirmReset by remember { mutableStateOf(false) }; var confirmDelete by remember { mutableStateOf(false) }; var deleting by remember { mutableStateOf(false) }; var manageCategories by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { repository.loadProfile { savedName, savedPhone -> name = savedName; phone = savedPhone } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("เนเธเธฃเนเธเธฅเน") }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(repository.auth.currentUser?.phoneNumber ?: repository.auth.currentUser?.email.orEmpty(), color = Color.Gray)
        OutlinedTextField(name, { name = it }, label = { Text("เธเธทเนเธญเธ—เธตเนเนเธชเธ”เธ") }, singleLine = true)
        OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == '-' } }, label = { Text("เน€เธเธญเธฃเนเนเธ—เธฃ (เนเธกเนเธเธฑเธเธเธฑเธ)") }, singleLine = true)
        message?.let { Text(it) }
        TextButton(onClick = { manageCategories = true }) { Text("เธเธฑเธ”เธเธฒเธฃเธซเธกเธงเธ”เธฃเธฒเธขเธฃเธฑเธ/เธฃเธฒเธขเธเนเธฒเธข") }
        TextButton(onClick = { repository.signOut(); onDismiss() }) { Text("เธญเธญเธเธเธฒเธเธฃเธฐเธเธ", color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { confirmReset = true }) { Text("เธฃเธตเน€เธเนเธ•เธเนเธญเธกเธนเธฅเธเธญเธเธเธฑเธ", color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { confirmDelete = true }) { Text("เธฅเธเธเธฑเธเธเธตเธ–เธฒเธงเธฃ", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
    } }, confirmButton = { Button(onClick = { repository.updateProfile(name, phone) { message = it ?: "เธเธฑเธเธ—เธถเธเนเธฅเนเธง" } }) { Text("เธเธฑเธเธ—เธถเธ") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("เธเธดเธ”") } })
    if (manageCategories) CategoryManagerDialog { manageCategories = false }
    if (confirmReset) AlertDialog(onDismissRequest = { if (!deleting) confirmReset = false }, title = { Text("เธฃเธตเน€เธเนเธ•เธเนเธญเธกเธนเธฅเธเธญเธเธเธฑเธ?") }, text = { Text("เธฃเธฒเธขเธฃเธฑเธ เธฃเธฒเธขเธเนเธฒเธข เธซเธเธตเน เธเธฃเธฐเธงเธฑเธ•เธดเธเธณเธฃเธฐ เนเธเธฃเนเธเธฅเน เธซเธกเธงเธ”เธชเนเธงเธเธ•เธฑเธง เนเธฅเธฐเธเธดเธงเธชเธฅเธดเธเธฃเธญเธ•เธฃเธงเธเธเธญเธเธเธฑเธเธเธตเธเธตเนเธเธฐเธ–เธนเธเธฅเธ เธเธฑเธเธเธตเนเธฅเธฐเน€เธเธญเธฃเนเธกเธทเธญเธ–เธทเธญเธขเธฑเธเธเธเธญเธขเธนเน") }, confirmButton = { Button(enabled = !deleting, onClick = { deleting = true; repository.clearUsageData { error -> deleting = false; if (error == null) { PendingSlipStore.clearUsage(context); LocalSlipLinkStore.clear(context); CategoryStore.clear(context); onDismiss() } else message = error; confirmReset = false } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(if (deleting) "เธเธณเธฅเธฑเธเธฃเธตเน€เธเนเธ•โ€ฆ" else "เธขเธทเธเธขเธฑเธเธฃเธตเน€เธเนเธ•") } }, dismissButton = { TextButton(enabled = !deleting, onClick = { confirmReset = false }) { Text("เธขเธเน€เธฅเธดเธ") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!deleting) confirmDelete = false }, title = { Text("เธฅเธเธเธฑเธเธเธตเธ–เธฒเธงเธฃ?") }, text = { Text("เธเนเธญเธกเธนเธฅเธ—เธฑเนเธเธซเธกเธ”เนเธฅเธฐเธเธฑเธเธเธตเน€เธเนเธฒเธชเธนเนเธฃเธฐเธเธเธเธตเนเธเธฐเธ–เธนเธเธฅเธเธ–เธฒเธงเธฃ เธซเธฅเธฑเธเธเธฒเธเธเธฑเนเธเธชเธฒเธกเธฒเธฃเธ–เธชเธกเธฑเธเธฃเนเธซเธกเนเธ”เนเธงเธขเน€เธเธญเธฃเนเน€เธ”เธดเธกเนเธฅเธฐเน€เธฃเธดเนเธกเธ•เธฑเนเธเนเธ•เนเธ•เนเธเนเธ”เน") }, confirmButton = { Button(enabled = !deleting, onClick = { deleting = true; repository.deleteOwnAccount { error -> deleting = false; if (error == null) { PendingSlipStore.clearUsage(context); LocalSlipLinkStore.clear(context); CategoryStore.clear(context); onDismiss() } else message = error; confirmDelete = false } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(if (deleting) "เธเธณเธฅเธฑเธเธฅเธโ€ฆ" else "เธฅเธเธเธฑเธเธเธตเธ–เธฒเธงเธฃ") } }, dismissButton = { TextButton(enabled = !deleting, onClick = { confirmDelete = false }) { Text("เธขเธเน€เธฅเธดเธ") } })
}

@Composable
private fun CategoryManagerDialog(close: () -> Unit) {
    val context = LocalContext.current
    var income by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    val categories = remember(income, refresh) { CategoryStore.all(context, income) }
    val defaults = if (income) CategoryStore.incomeDefaults else CategoryStore.expenseDefaults
    AlertDialog(onDismissRequest = close, title = { Text("เธเธฑเธ”เธเธฒเธฃเธซเธกเธงเธ”") }, text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!income, { income = false }, { Text("เธฃเธฒเธขเธเนเธฒเธข") }); FilterChip(income, { income = true }, { Text("เธฃเธฒเธขเธฃเธฑเธ") }) }
        Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(newName, { newName = it.take(50) }, label = { Text("เน€เธเธดเนเธกเธซเธกเธงเธ”เนเธซเธกเน") }, modifier = Modifier.weight(1f), singleLine = true); IconButton(enabled = newName.isNotBlank(), onClick = { CategoryStore.add(context, income, newName); newName = ""; refresh++ }) { Icon(Icons.Default.Add, "เน€เธเธดเนเธกเธซเธกเธงเธ”") } }
        categories.forEach { category -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(category, Modifier.weight(1f)); if (category !in defaults) IconButton(onClick = { CategoryStore.remove(context, income, category); refresh++ }) { Icon(Icons.Default.Delete, "เธฅเธ $category", tint = MaterialTheme.colorScheme.error) } } }
    } }, confirmButton = { Button(onClick = close) { Text("เน€เธชเธฃเนเธ") } })
}

@Composable
private fun SummaryCard(entries: List<MoneyTransaction>) {
    val income = entries.filter { it.type == "INCOME" }.sumOf { it.amount }
    val expense = entries.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = RubPanel)) {
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Text("เธเธเน€เธซเธฅเธทเธญ", color = RubMuted); Text(money(income - expense), color = RubCream, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp)); Row { Text("เธฃเธฑเธ  ${money(income)}", color = RubMint); Spacer(Modifier.weight(1f)); Text("เธเนเธฒเธข  ${money(expense)}", color = RubCoral) }
        }
    }
}

@Composable private fun PendingSlipDialog(items: List<PendingSlip>, onClose: () -> Unit, onReview: (PendingSlip) -> Unit, onReject: (PendingSlip) -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("สลิปรอตรวจ") }, text = {
        if (items.isEmpty()) Text("ไม่มีรายการรอตรวจ") else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { pending ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F7FA)), modifier = Modifier.fillMaxWidth().clickable { onReview(pending) }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

@Composable private fun EntryFilters(period: EntryPeriod, kind: EntryKind, setPeriod: (EntryPeriod) -> Unit, setKind: (EntryKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { EntryPeriod.entries.forEach { FilterChip(selected = period == it, onClick = { setPeriod(it) }, label = { Text(it.label) }) } }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { EntryKind.entries.forEach { FilterChip(selected = kind == it, onClick = { setKind(it) }, label = { Text(it.label) }) } }
    }
}

@Composable private fun SpendingOverview(entries: List<MoneyTransaction>, period: EntryPeriod) {
    val groups = entries.groupBy { it.category.ifBlank { "เนเธเนเธเนเธฒเธขเธ—เธฑเนเธงเนเธ" } }.mapValues { it.value.sumOf(MoneyTransaction::amount) }.entries.sortedByDescending { it.value }
    val total = groups.sumOf { it.value }
    val colors = listOf(Color(0xFF0B9B73), Color(0xFFFF8A65), Color(0xFF5C6BC0), Color(0xFFFFC107), Color(0xFF26A69A), Color(0xFFAB47BC))
    val description = if (total > 0) "เธ เธฒเธเธฃเธงเธกเธฃเธฒเธขเธเนเธฒเธข${period.label} เธฃเธงเธก ${money(total)} เธเธณเธเธงเธ ${groups.size} เธซเธกเธงเธ”" else "เธขเธฑเธเนเธกเนเธกเธตเธฃเธฒเธขเธเนเธฒเธข${period.label}"
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(18.dp).semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("เธ เธฒเธเธฃเธงเธกเธฃเธฒเธขเธเนเธฒเธข โ€ข ${period.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (total <= 0) Text("เธขเธฑเธเนเธกเนเธกเธตเธฃเธฒเธขเธเนเธฒเธขเนเธเธเนเธงเธเธเธตเน", color = Color.Gray)
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

@Composable private fun EntryRow(item: MoneyTransaction, open: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = open), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(42.dp), shape = RoundedCornerShape(14.dp), color = if (item.type == "INCOME") Color(0xFFE1F7ED) else Color(0xFFFFE8E5)) { Icon(if (item.type == "INCOME") Icons.Default.Add else Icons.Default.ReceiptLong, null, Modifier.padding(10.dp), tint = if (item.type == "INCOME") Color(0xFF0B9B73) else Color(0xFFD84A3A)) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.title.ifBlank { item.category.ifBlank { "เนเธกเนเธฃเธฐเธเธธเธฃเธฒเธขเธเธฒเธฃ" } }, fontWeight = FontWeight.SemiBold); Text(listOf(item.category, item.occurredAt).filter(String::isNotBlank).joinToString(" โ€ข "), style = MaterialTheme.typography.bodySmall, color = Color.Gray) }; Text((if (item.type == "INCOME") "+" else "-") + money(item.amount), color = if (item.type == "INCOME") Color(0xFF0B9B73) else Color(0xFFD84A3A), fontWeight = FontWeight.Bold) } } }

@Composable private fun QuickOverview(entries: List<MoneyTransaction>) {
    val top = entries.groupBy { it.category }.maxByOrNull { it.value.sumOf(MoneyTransaction::amount) }?.key ?: "เธขเธฑเธเนเธกเนเธกเธตเธเนเธญเธกเธนเธฅ"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp)) { Text("เธฃเธฒเธขเธเธฒเธฃเน€เธ”เธทเธญเธเธเธตเน", color = Color.Gray); Text("${entries.size} เธฃเธฒเธขเธเธฒเธฃ", fontWeight = FontWeight.Bold) } }
        Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp)) { Text("เธซเธกเธงเธ”เธซเธฅเธฑเธ", color = Color.Gray); Text(top, fontWeight = FontWeight.Bold, maxLines = 1) } }
    }
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
        ) { Icon(if (income) Icons.Default.ArrowDownward else visual.icon, null, tint = visual.tint, modifier = Modifier.padding(18.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(categoryDisplayName(category), color = Color(0xFF1B1D28), style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Black else FontWeight.Normal, maxLines = 2)
    }
}

private data class CategoryVisual(val icon: androidx.compose.ui.graphics.vector.ImageVector, val tint: Color)

private fun categoryVisual(category: String): CategoryVisual {
    val value = category.lowercase(Locale.getDefault())
    return when {
        value.contains("อาหาร") || value.contains("food") -> CategoryVisual(Icons.Default.Restaurant, Color(0xFFFFB21A))
        value.contains("เดินทาง") || value.contains("รถ") || value.contains("travel") -> CategoryVisual(Icons.Default.DirectionsCar, Color(0xFF16D7E8))
        value.contains("ช้อ") || value.contains("shop") -> CategoryVisual(Icons.Default.ShoppingBag, Color(0xFFFF20D7))
        value.contains("บันเทิง") || value.contains("movie") -> CategoryVisual(Icons.Default.Movie, Color(0xFFFF4A19))
        value.contains("บ้าน") || value.contains("ที่พัก") || value.contains("สาธาร") -> CategoryVisual(Icons.Default.House, Color(0xFF1A63FF))
        value.contains("สุขภาพ") || value.contains("health") -> CategoryVisual(Icons.Default.LocalHospital, Color(0xFF28D6B0))
        value.contains("ครอบ") || value.contains("สัตว์") -> CategoryVisual(Icons.Default.FamilyRestroom, Color(0xFFE868DC))
        value.contains("ให้") || value.contains("gift") -> CategoryVisual(Icons.Default.CardGiftcard, Color(0xFF9D27FF))
        value.contains("เที่ยว") || value.contains("beach") -> CategoryVisual(Icons.Default.BeachAccess, Color(0xFF2183D8))
        value.contains("ศึกษา") || value.contains("school") -> CategoryVisual(Icons.Default.School, Color(0xFF5C4CF6))
        value.contains("งาน") || value.contains("ธุรกิจ") || value.contains("work") -> CategoryVisual(Icons.Default.Work, Color(0xFFE08D00))
        else -> CategoryVisual(Icons.Default.GridView, RubBlue)
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
