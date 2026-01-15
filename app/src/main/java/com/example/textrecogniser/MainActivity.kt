package com.example.textrecogniser

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.textrecogniser.ui.theme.TextRecogniserTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.math.min
import kotlin.math.sqrt

enum class AppState {
    CAMERA, CROP, RESULT
}

enum class DragHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
}

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        enableEdgeToEdge()
        setContent {
            TextRecogniserTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        TextRecognizerApp()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextRecognizerApp() {
    var appState by remember { mutableStateOf(AppState.CAMERA) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var recognizedText by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("Latin") }
    var expanded by remember { mutableStateOf(false) }

    val languages = listOf("Latin", "Devanagari", "Chinese", "Japanese", "Korean")

    val recognizer = remember(selectedLanguage) {
        when (selectedLanguage) {
            "Devanagari" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            "Chinese" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            "Japanese" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            "Korean" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (appState == AppState.CAMERA) {
            Box(modifier = Modifier.padding(16.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedLanguage,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        languages.forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language) },
                                onClick = {
                                    selectedLanguage = language
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (appState) {
                AppState.CAMERA -> {
                    CameraView(onImageCaptured = { bitmap ->
                        capturedBitmap = bitmap
                        appState = AppState.CROP
                    })
                }
                AppState.CROP -> {
                    capturedBitmap?.let { bitmap ->
                        CropView(
                            bitmap = bitmap,
                            onCropDone = { croppedBitmap ->
                                processImage(croppedBitmap, recognizer) { text ->
                                    recognizedText = text
                                    appState = AppState.RESULT
                                }
                            },
                            onCancel = { appState = AppState.CAMERA }
                        )
                    }
                }
                AppState.RESULT -> {
                    ResultView(
                        text = recognizedText,
                        onRetake = {
                            recognizedText = ""
                            appState = AppState.CAMERA
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CameraView(onImageCaptured: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(Unit) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = androidx.camera.core.Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            Log.e("CameraView", "Binding failed", e)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView({ previewView }, modifier = Modifier.fillMaxSize())

        FloatingActionButton(
            onClick = {
                val file = File(context.cacheDir, "${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            var bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            val exif = ExifInterface(file.absolutePath)
                            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                            val matrix = Matrix()
                            when (orientation) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                            }
                            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            onImageCaptured(bitmap)
                        }
                        override fun onError(exception: ImageCaptureException) {
                            Log.e("CameraView", "Capture failed", exception)
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .size(80.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Capture", modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
fun CropView(
    bitmap: Bitmap,
    onCropDone: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var rect by remember { mutableStateOf(Rect(200f, 200f, 600f, 600f)) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

    val handleRadius = 40f // Detection radius in pixels

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { containerSize = it.size }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        activeHandle = when {
                            distance(offset, Offset(rect.left, rect.top)) < handleRadius -> DragHandle.TOP_LEFT
                            distance(offset, Offset(rect.right, rect.top)) < handleRadius -> DragHandle.TOP_RIGHT
                            distance(offset, Offset(rect.left, rect.bottom)) < handleRadius -> DragHandle.BOTTOM_LEFT
                            distance(offset, Offset(rect.right, rect.bottom)) < handleRadius -> DragHandle.BOTTOM_RIGHT
                            rect.contains(offset) -> DragHandle.CENTER
                            else -> DragHandle.NONE
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val minSize = 100f
                        when (activeHandle) {
                            DragHandle.TOP_LEFT -> {
                                val newLeft = (rect.left + dragAmount.x).coerceIn(0f, rect.right - minSize)
                                val newTop = (rect.top + dragAmount.y).coerceIn(0f, rect.bottom - minSize)
                                rect = Rect(newLeft, newTop, rect.right, rect.bottom)
                            }
                            DragHandle.TOP_RIGHT -> {
                                val newRight = (rect.right + dragAmount.x).coerceIn(rect.left + minSize, containerSize.width.toFloat())
                                val newTop = (rect.top + dragAmount.y).coerceIn(0f, rect.bottom - minSize)
                                rect = Rect(rect.left, newTop, newRight, rect.bottom)
                            }
                            DragHandle.BOTTOM_LEFT -> {
                                val newLeft = (rect.left + dragAmount.x).coerceIn(0f, rect.right - minSize)
                                val newBottom = (rect.bottom + dragAmount.y).coerceIn(rect.top + minSize, containerSize.height.toFloat())
                                rect = Rect(newLeft, rect.top, rect.right, newBottom)
                            }
                            DragHandle.BOTTOM_RIGHT -> {
                                val newRight = (rect.right + dragAmount.x).coerceIn(rect.left + minSize, containerSize.width.toFloat())
                                val newBottom = (rect.bottom + dragAmount.y).coerceIn(rect.top + minSize, containerSize.height.toFloat())
                                rect = Rect(rect.left, rect.top, newRight, newBottom)
                            }
                            DragHandle.CENTER -> {
                                val dx = dragAmount.x
                                val dy = dragAmount.y
                                val newLeft = (rect.left + dx).coerceIn(0f, containerSize.width.toFloat() - rect.width)
                                val newTop = (rect.top + dy).coerceIn(0f, containerSize.height.toFloat() - rect.height)
                                rect = Rect(newLeft, newTop, newLeft + rect.width, newTop + rect.height)
                            }
                            DragHandle.NONE -> {}
                        }
                    },
                    onDragEnd = { activeHandle = DragHandle.NONE }
                )
            }
        ) {
            // Draw dim overlay
            drawRect(Color.Black.copy(alpha = 0.6f), size = Size(containerSize.width.toFloat(), rect.top))
            drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, rect.bottom), size = Size(containerSize.width.toFloat(), containerSize.height - rect.bottom))
            drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, rect.top), size = Size(rect.left, rect.height))
            drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(rect.right, rect.top), size = Size(containerSize.width - rect.right, rect.height))

            // Draw crop rectangle
            drawRect(
                color = Color.Cyan,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                style = Stroke(width = 2.dp.toPx())
            )

            // Draw handles
            val handleSize = 10.dp.toPx()
            val handleColor = Color.Cyan
            drawCircle(handleColor, radius = handleSize / 2, center = Offset(rect.left, rect.top))
            drawCircle(handleColor, radius = handleSize / 2, center = Offset(rect.right, rect.top))
            drawCircle(handleColor, radius = handleSize / 2, center = Offset(rect.left, rect.bottom))
            drawCircle(handleColor, radius = handleSize / 2, center = Offset(rect.right, rect.bottom))
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Cancel")
            }
            Button(onClick = {
                val cropped = performCrop(bitmap, rect, containerSize)
                onCropDone(cropped)
            }) {
                Icon(Icons.Default.Crop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan")
            }
        }
    }
}

private fun distance(o1: Offset, o2: Offset): Float {
    return sqrt((o1.x - o2.x) * (o1.x - o2.x) + (o1.y - o2.y) * (o1.y - o2.y))
}

private fun performCrop(bitmap: Bitmap, rect: Rect, containerSize: IntSize): Bitmap {
    if (containerSize.width == 0 || containerSize.height == 0) return bitmap
    val bitmapWidth = bitmap.width.toFloat()
    val bitmapHeight = bitmap.height.toFloat()
    val scale = min(containerSize.width / bitmapWidth, containerSize.height / bitmapHeight)
    val displayedWidth = bitmapWidth * scale
    val displayedHeight = bitmapHeight * scale
    val offsetX = (containerSize.width - displayedWidth) / 2
    val offsetY = (containerSize.height - displayedHeight) / 2
    val left = ((rect.left - offsetX) / scale).coerceIn(0f, bitmapWidth)
    val top = ((rect.top - offsetY) / scale).coerceIn(0f, bitmapHeight)
    val right = ((rect.right - offsetX) / scale).coerceIn(0f, bitmapWidth)
    val bottom = ((rect.bottom - offsetY) / scale).coerceIn(0f, bitmapHeight)
    val w = (right - left).toInt().coerceAtLeast(1)
    val h = (bottom - top).toInt().coerceAtLeast(1)
    return Bitmap.createBitmap(bitmap, left.toInt(), top.toInt(), w, h)
}

@Composable
fun ResultView(text: String, onRetake: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Recognized Text", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 16.dp))
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)) {
                SelectionContainer {
                    Text(
                        text = if (text.isBlank()) "No text detected." else text,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp, fontSize = 18.sp)
                    )
                }
                if (text.isNotBlank()) {
                    FilledIconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Recognized Text", text))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetake, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("Retake Photo", fontSize = 18.sp)
        }
    }
}

private fun processImage(bitmap: Bitmap, recognizer: com.google.mlkit.vision.text.TextRecognizer, onResult: (String) -> Unit) {
    recognizer.process(InputImage.fromBitmap(bitmap, 0))
        .addOnSuccessListener { onResult(it.text) }
        .addOnFailureListener { onResult("Error: ${it.message}") }
}
