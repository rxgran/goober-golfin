package com.example.goobergolfin

import android.Manifest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.goobergolfin.ui.theme.GooberGolfinTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

enum class SwingView {
    FACE_ON, DOWN_THE_LINE
}

class MainActivity : ComponentActivity() {

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermission.launch(Manifest.permission.CAMERA)
        enableEdgeToEdge()

        setContent {
            GooberGolfinTheme {
                var swingView by remember { mutableStateOf(SwingView.FACE_ON) }
                var showGuide by remember { mutableStateOf(true) }
                val sessionHistory = remember { mutableStateListOf<LoggedSwing>() }
                var currentState by remember { mutableStateOf(SwingState.SCANNING) }
                
                // User Management
                var userList by remember { mutableStateOf(listOf("Ranier", "Cara")) }
                var currentUser by remember { mutableStateOf(userList.first()) }
                var showAddUserDialog by remember { mutableStateOf(false) }
                
                val context = LocalContext.current
                val logger = remember { DataLogHelper(context) }
                var activeLogItem by remember { mutableStateOf<LoggedSwing?>(null) }
                var lastClubUsed by remember { mutableStateOf("") }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        Column {
                            UserSelector(
                                currentUser = currentUser,
                                userList = userList,
                                onUserSelected = { currentUser = it },
                                onAddUserClick = { showAddUserDialog = true }
                            )
                            ViewToggleBar(
                                currentView = swingView,
                                onViewSelected = { swingView = it }
                            )
                        }
                    }
                ) { innerPadding ->
                    CameraPreview(
                        swingView = swingView,
                        showGuide = showGuide,
                        history = sessionHistory.toList(),
                        swingState = currentState,
                        onToggleGuide = { showGuide = !showGuide },
                        onExport = { logger.exportToDownloads() },
                        onSwingDetected = { metrics ->
                            val newEntry = LoggedSwing(
                                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
                                user = currentUser,
                                club = lastClubUsed,
                                metrics = metrics,
                                carry = "", total = "", apex = "", ballSpeed = "",
                                swingSpeed = "", spin = "", accSide = "", accDist = "",
                                shotShape = "Straight", contact = "Solid"
                            )
                            activeLogItem = newEntry
                        },
                        onStateChanged = { currentState = it },
                        onResetHistory = { sessionHistory.clear(); logger.saveSession(emptyList()) },
                        onEditItem = { activeLogItem = it },
                        modifier = Modifier.padding(innerPadding)
                    )

                    // New User Dialog
                    if (showAddUserDialog) {
                        var newUserName by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showAddUserDialog = false },
                            title = { Text("Add New User") },
                            text = {
                                OutlinedTextField(
                                    value = newUserName,
                                    onValueChange = { newUserName = it },
                                    label = { Text("Name") },
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                Button(onClick = {
                                    if (newUserName.isNotBlank()) {
                                        userList = userList + newUserName
                                        currentUser = newUserName
                                    }
                                    showAddUserDialog = false
                                }) { Text("Add") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAddUserDialog = false }) { Text("Cancel") }
                            }
                        )
                    }

                    activeLogItem?.let { entry ->
                        LogDialog(
                            initialEntry = entry,
                            onSave = { updatedEntry ->
                                val index = sessionHistory.indexOfFirst { it.metrics.id == updatedEntry.metrics.id }
                                if (index != -1) {
                                    sessionHistory[index] = updatedEntry
                                } else {
                                    sessionHistory.add(0, updatedEntry)
                                    lastClubUsed = updatedEntry.club
                                }
                                logger.saveSession(sessionHistory.toList())
                                activeLogItem = null
                            },
                            onDelete = {
                                sessionHistory.removeAll { it.metrics.id == entry.metrics.id }
                                logger.saveSession(sessionHistory.toList())
                                activeLogItem = null
                            },
                            onDismiss = { activeLogItem = null }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSelector(
    currentUser: String, 
    userList: List<String>,
    onUserSelected: (String) -> Unit,
    onAddUserClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.widthIn(min = 120.dp)
            ) {
                OutlinedTextField(
                    value = currentUser,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("User", fontSize = 10.sp) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    userList.forEach { user ->
                        DropdownMenuItem(
                            text = { Text(user) },
                            onClick = {
                                onUserSelected(user)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onAddUserClick) {
                Icon(Icons.Default.Add, contentDescription = "Add User", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ShotShapeDiagram(
    selectedShape: String,
    onShapeSelected: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerPoint = androidx.compose.ui.geometry.Offset(w / 2, h - 20f)

            drawLine(Color.Red, centerPoint, androidx.compose.ui.geometry.Offset(w / 2, 40f), strokeWidth = 5f)
            drawPath(createShotPath(centerPoint, w * 0.38f, 40f, -40f), Color.Blue, style = Stroke(5f)) // Fade
            drawPath(createShotPath(centerPoint, w * 0.22f, 40f, -10f), Color.Red, style = Stroke(5f)) // Pull
            drawPath(createShotPath(centerPoint, w * 0.08f, h * 0.45f, -120f), Color.Black, style = Stroke(5f)) // Hook
            drawPath(createShotPath(centerPoint, 10f, h * 0.75f, -180f), Color(0xFF4CAF50), style = Stroke(5f)) // Duck Hook
            drawPath(createShotPath(centerPoint, w * 0.62f, 40f, 40f), Color.Blue, style = Stroke(5f)) // Draw
            drawPath(createShotPath(centerPoint, w * 0.78f, 40f, 10f), Color.Red, style = Stroke(5f)) // Push
            drawPath(createShotPath(centerPoint, w * 0.92f, h * 0.45f, 120f), Color.Black, style = Stroke(5f)) // Slice
            drawPath(createShotPath(centerPoint, w - 10f, h * 0.75f, 180f), Color(0xFF4CAF50), style = Stroke(5f)) // Shank
        }

        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter), horizontalArrangement = Arrangement.SpaceBetween) {
            ShotLabel("PULL", selectedShape == "Pull") { onShapeSelected("Pull") }
            ShotLabel("FADE", selectedShape == "Fade") { onShapeSelected("Fade") }
            ShotLabel("STRAIGHT", selectedShape == "Straight") { onShapeSelected("Straight") }
            ShotLabel("DRAW", selectedShape == "Draw") { onShapeSelected("Draw") }
            ShotLabel("PUSH", selectedShape == "Push") { onShapeSelected("Push") }
        }

        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.CenterStart).padding(top = 20.dp)) {
                ShotLabel("HOOK", selectedShape == "Hook") { onShapeSelected("Hook") }
            }
            Box(Modifier.align(Alignment.CenterEnd).padding(top = 20.dp)) {
                ShotLabel("SLICE", selectedShape == "Slice") { onShapeSelected("Slice") }
            }
        }

        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.BottomStart).padding(bottom = 10.dp)) {
                ShotLabel("DUCK HOOK", selectedShape == "Duck Hook") { onShapeSelected("Duck Hook") }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(bottom = 10.dp)) {
                ShotLabel("SHANK", selectedShape == "Shank") { onShapeSelected("Shank") }
            }
        }
    }
}

fun createShotPath(start: androidx.compose.ui.geometry.Offset, endX: Float, endY: Float, curve: Float): androidx.compose.ui.graphics.Path {
    return androidx.compose.ui.graphics.Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(start.x + curve, start.y / 2, endX, endY)
    }
}

@Composable
fun ShotLabel(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
        modifier = Modifier.padding(2.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LogDialog(
    initialEntry: LoggedSwing,
    onSave: (LoggedSwing) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var club by remember { mutableStateOf(initialEntry.club) }
    var carry by remember { mutableStateOf(initialEntry.carry) }
    var total by remember { mutableStateOf(initialEntry.total) }
    var apex by remember { mutableStateOf(initialEntry.apex) }
    var ballSpeed by remember { mutableStateOf(initialEntry.ballSpeed) }
    var swingSpeed by remember { mutableStateOf(initialEntry.swingSpeed) }
    var spin by remember { mutableStateOf(initialEntry.spin) }
    var accuracySide by remember { mutableStateOf(if(initialEntry.accSide.isEmpty()) "Center" else initialEntry.accSide) }
    var accuracyDist by remember { mutableStateOf(initialEntry.accDist) }
    var selectedShape by remember { mutableStateOf(initialEntry.shotShape) }
    var selectedContact by remember { mutableStateOf(initialEntry.contact) }

    val contacts = listOf("Top", "Sky", "Solid")
    val clubs = listOf("DR", "3W", "5W", "7W", "4H", "5H", "4I", "5I", "6I", "7I", "8I", "9I", "PW", "GW", "SW", "LW", "Put")
    val sides = listOf("Left", "Center", "Right")
    
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Log Simulator Result", Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // ... (AI card remains same)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("AI Swing Analysis:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tempo: ${String.format(Locale.US, "%.1f", initialEntry.metrics.tempo)}", fontSize = 11.sp)
                            Text("Head: ${String.format(Locale.US, "%.0f%%", initialEntry.metrics.headMovement * 100)}", fontSize = 11.sp)
                            val hipLabel = if (initialEntry.metrics.isFaceOn) "Sway" else "Posture"
                            Text("$hipLabel: ${String.format(Locale.US, "%.0f%%", initialEntry.metrics.hipStability * 100)}", fontSize = 11.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Turn: ${String.format(Locale.US, "%.0f%%", initialEntry.metrics.hipTurn * 100)}", fontSize = 11.sp)
                            Text("Foot: ${String.format(Locale.US, "%.0f%%", initialEntry.metrics.footStability * 100)}", fontSize = 11.sp)
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = club,
                        onValueChange = { club = it },
                        readOnly = false,
                        label = { Text("Club") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        clubs.forEach { clubOption ->
                            DropdownMenuItem(
                                text = { Text(clubOption) },
                                onClick = {
                                    club = clubOption
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = carry, onValueChange = { carry = it }, label = { Text("Carry") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = total, onValueChange = { total = it }, label = { Text("Total") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }

                Spacer(Modifier.height(8.dp))
                Column {
                    Text("Accuracy Side:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        sides.forEach { side ->
                            FilterChip(
                                selected = accuracySide == side,
                                onClick = { accuracySide = side },
                                label = { Text(side, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = apex, onValueChange = { apex = it }, label = { Text("Apex") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = accuracyDist,
                        onValueChange = { accuracyDist = it },
                        label = { Text("Offset (Yards)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = accuracySide != "Center"
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = ballSpeed, onValueChange = { ballSpeed = it }, label = { Text("Ball Spd") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = swingSpeed, onValueChange = { swingSpeed = it }, label = { Text("Swing Spd") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                OutlinedTextField(value = spin, onValueChange = { spin = it }, label = { Text("Spin (RPM)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                
                Spacer(Modifier.height(16.dp))
                Text("Shot Shape:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                ShotShapeDiagram(selectedShape = selectedShape, onShapeSelected = { selectedShape = it })
                
                Spacer(Modifier.height(8.dp))
                Text("Contact:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    contacts.forEach { contact ->
                        FilterChip(selected = selectedContact == contact, onClick = { selectedContact = contact }, label = { Text(contact, fontSize = 10.sp) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                onSave(initialEntry.copy(
                    club = club, carry = carry, total = total, apex = apex,
                    ballSpeed = ballSpeed, swingSpeed = swingSpeed, spin = spin,
                    accSide = accuracySide, accDist = accuracyDist,
                    shotShape = selectedShape, contact = selectedContact
                )) 
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


@Composable
fun ViewToggleBar(currentView: SwingView, onViewSelected: (SwingView) -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { onViewSelected(SwingView.FACE_ON) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentView == SwingView.FACE_ON) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                )
            ) { Text("Face-On") }
            Button(
                onClick = { onViewSelected(SwingView.DOWN_THE_LINE) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentView == SwingView.DOWN_THE_LINE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                )
            ) { Text("Down-the-Line") }
        }
    }
}

@Composable
fun CameraPreview(
    swingView: SwingView,
    showGuide: Boolean,
    history: List<LoggedSwing>,
    swingState: SwingState,
    onToggleGuide: () -> Unit,
    onExport: () -> Unit,
    onSwingDetected: (SwingMetrics) -> Unit,
    onStateChanged: (SwingState) -> Unit,
    onResetHistory: () -> Unit,
    onEditItem: (LoggedSwing) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var overlayView: OverlayView? by remember { mutableStateOf(null) }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) }

    val poseLandmarkerHelper = remember {
        PoseLandmarkerHelper(
            context = context,
            poseLandmarkerHelperListener = object : PoseLandmarkerHelper.LandmarkerListener {
                override fun onError(error: String) { Log.e("MainActivity", "Error: $error") }
                override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
                    (context as? ComponentActivity)?.runOnUiThread {
                        resultBundle.results.firstOrNull()?.let { result ->
                            overlayView?.setResults(result, resultBundle.inputImageHeight, resultBundle.inputImageWidth)
                        }
                    }
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            poseLandmarkerHelper.clearPoseLandmarker()
            toneGenerator.release()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                        .also { it.setAnalyzer(executor) { proxy -> poseLandmarkerHelper.detectLiveStream(proxy) } }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer)
                    } catch (e: Exception) { Log.e("MainActivity", "Binding failed", e) }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> 
                OverlayView(ctx, null).also { 
                    overlayView = it
                    it.onSwingDetected = { m -> onSwingDetected(m) }
                    it.onStateChanged = { s -> 
                        onStateChanged(s)
                        if (s == SwingState.READY) toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                        else if (s == SwingState.FINISH) toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 200)
                    }
                } 
            },
            update = { view -> 
                view.showGuide = showGuide
                view.currentSwingView = swingView
            }
        )

        Box(modifier = Modifier.fillMaxHeight().width(130.dp).padding(start = 16.dp, top = 80.dp)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { loggedSwing ->
                    val isLatest = history.firstOrNull()?.metrics?.id == loggedSwing.metrics.id
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLatest) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { onEditItem(loggedSwing) }
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.Start) {
                            Text(if (isLatest) "LATEST" else "PAST", fontSize = 8.sp, color = Color.White)
                            Text(String.format(Locale.US, "Tempo: %.1f", loggedSwing.metrics.tempo), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                            
                            // Keep all physical metrics and add distance
                            Text(String.format(Locale.US, "Dist: %s", if(loggedSwing.carry.isNotEmpty()) loggedSwing.carry else "---"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(String.format(Locale.US, "Head: %.0f%%", loggedSwing.metrics.headMovement * 100), fontSize = 10.sp, color = Color.White)
                            val hipLabel = if (loggedSwing.metrics.isFaceOn) "Sway" else "Post"
                            Text(String.format(Locale.US, "$hipLabel: %.0f%%", loggedSwing.metrics.hipStability * 100), fontSize = 10.sp, color = Color.White)
                            Text(String.format(Locale.US, "Turn: %.0f%%", loggedSwing.metrics.hipTurn * 100), fontSize = 10.sp, color = Color.White)
                            Text(String.format(Locale.US, "Foot: %.0f%%", loggedSwing.metrics.footStability * 100), fontSize = 10.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Badge(containerColor = Color.Black.copy(alpha = 0.6f), contentColor = Color.White) {
                Text(if (swingView == SwingView.FACE_ON) "MODE: FACE-ON" else "MODE: DOWN-THE-LINE", modifier = Modifier.padding(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            AssistChip(
                onClick = {},
                label = { Text(swingState.name, fontWeight = FontWeight.Bold) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = Color.White,
                    containerColor = when(swingState) {
                        SwingState.SCANNING -> Color.Gray.copy(alpha = 0.6f)
                        SwingState.READY -> Color(0xFF4CAF50).copy(alpha = 0.9f)
                        SwingState.TAKEAWAY -> Color.Blue.copy(alpha = 0.6f)
                        SwingState.DOWNSWING -> Color.Red.copy(alpha = 0.6f)
                        SwingState.FINISH -> Color(0xFF4CAF50).copy(alpha = 0.6f)
                    }
                )
            )
            if (swingState == SwingState.READY) {
                Text("READY", color = Color(0xFF4CAF50), fontWeight = FontWeight.Black, fontSize = 18.sp)
            } else if (showGuide) {
                Text("Align your body within the guide box", color = Color.White, modifier = Modifier.padding(8.dp))
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 180.dp)) {
            FloatingActionButton(onClick = onExport, containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                Icon(Icons.Default.Download, contentDescription = "Export to Downloads")
            }
            Spacer(Modifier.height(16.dp))
            FloatingActionButton(onClick = onToggleGuide, containerColor = if (showGuide) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary) {
                Icon(Icons.Default.Build, contentDescription = "Toggle Guide")
            }
            Spacer(Modifier.height(16.dp))
            FloatingActionButton(onClick = { 
                onResetHistory()
                overlayView?.clear()
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
            }
        }
    }
}
