package com.example.goobergolfin

import android.Manifest
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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.goobergolfin.ui.theme.GooberGolfinTheme
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
                var lastTempo by remember { mutableStateOf(0f) }
                var currentState by remember { mutableStateOf(SwingState.IDLE) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        ViewToggleBar(
                            currentView = swingView,
                            onViewSelected = { swingView = it }
                        )
                    }
                ) { innerPadding ->
                    CameraPreview(
                        swingView = swingView,
                        showGuide = showGuide,
                        tempo = lastTempo,
                        swingState = currentState,
                        onToggleGuide = { showGuide = !showGuide },
                        onTempoCalculated = { lastTempo = it },
                        onStateChanged = { currentState = it },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
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
    tempo: Float,
    swingState: SwingState,
    onToggleGuide: () -> Unit,
    onTempoCalculated: (Float) -> Unit,
    onStateChanged: (SwingState) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var overlayView: OverlayView? by remember { mutableStateOf(null) }

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
                    it.onSwingDetected = { t -> onTempoCalculated(t) }
                    it.onStateChanged = { s -> onStateChanged(s) }
                } 
            },
            update = { view -> 
                view.showGuide = showGuide
                view.currentSwingView = swingView
            }
        )

        // Indicator & Tempo Display
        Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Badge(containerColor = Color.Black.copy(alpha = 0.6f), contentColor = Color.White) {
                Text(if (swingView == SwingView.FACE_ON) "MODE: FACE-ON" else "MODE: DOWN-THE-LINE", modifier = Modifier.padding(8.dp))
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Real-time State Label
            AssistChip(
                onClick = {},
                label = { Text(swingState.name) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = Color.White,
                    containerColor = when(swingState) {
                        SwingState.IDLE -> Color.Gray.copy(alpha = 0.6f)
                        SwingState.TAKEAWAY -> Color.Blue.copy(alpha = 0.6f)
                        SwingState.DOWNSWING -> Color.Red.copy(alpha = 0.6f)
                        SwingState.FINISH -> Color.Green.copy(alpha = 0.6f)
                    }
                )
            )

            if (showGuide) {
                Text("Align your body within the guide box", color = Color.White, modifier = Modifier.padding(8.dp))
            }
            
            if (tempo > 0) {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TEMPO", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(String.format(Locale.US, "%.1f:1", tempo), fontSize = 32.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        // Action Buttons
        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 100.dp)) {
            FloatingActionButton(onClick = onToggleGuide, containerColor = if (showGuide) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary) {
                Icon(Icons.Default.Build, contentDescription = "Toggle Guide")
            }
            Spacer(Modifier.height(16.dp))
            FloatingActionButton(onClick = { 
                overlayView?.clear()
                onTempoCalculated(0f)
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
            }
        }
    }
}
