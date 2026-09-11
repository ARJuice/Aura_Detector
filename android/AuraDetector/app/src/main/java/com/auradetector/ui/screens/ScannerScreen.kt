package com.auradetector.ui.screens

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.auradetector.data.ServerConfig
import com.auradetector.transport.AuraWebSocket
import com.auradetector.transport.TransportStatus
import com.auradetector.transport.VisionFrameState
import com.auradetector.transport.VisionPoint
import com.auradetector.transport.VisionProfile
import com.auradetector.transport.VisionLinkState
import com.auradetector.ui.theme.AuraWhiteHot
import com.auradetector.ui.theme.CyanAccent
import com.auradetector.ui.theme.DarkNavy
import com.auradetector.ui.theme.ErrorRed
import com.auradetector.ui.theme.NeonGreen
import com.auradetector.ui.theme.OrangeWarning
import com.auradetector.ui.theme.paletteColor
import com.auradetector.ui.theme.resultColor
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ScannerScreen(config: ServerConfig, onExit: () -> Unit) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCameraPermission) {
        PermissionRequired(onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        return
    }

    val transport = remember(config) { AuraWebSocket(config) }
    val status by transport.status.collectAsState()
    val frameState by transport.frameState.collectAsState()
    var selectedSubjectId by remember { mutableStateOf<Long?>(null) }
    var liveReading by remember { mutableStateOf<String?>(null) }
    val latestFrameState = rememberUpdatedState(frameState)
    val auraGenerators = remember { mutableMapOf<Long, AuraValueGenerator>() }
    var scanningSubjectId by remember { mutableStateOf<Long?>(null) }
    var scanProgress by remember { mutableStateOf(0f) }
    var scanResult by remember { mutableStateOf<AuraScanResult?>(null) }
    val scanGenerators = remember { mutableMapOf<Long, AuraScanGenerator>() }
    var soundEnabled by remember { mutableStateOf(true) }
    val latestSoundEnabled = rememberUpdatedState(soundEnabled)
    val feedback = remember(context) { ScanFeedback(context) }

    val particleSystem = remember { ParticleSystem() }
    var particleTick by remember { mutableIntStateOf(0) }
    val scanPulseProgress = remember { Animatable(0f) }
    val transition = rememberInfiniteTransition(label = "breathe")
    val breatheAlpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheAlpha"
    )
    val scanPulseColor = scanResult?.classification?.let { resultColor(it) } ?: NeonGreen

    DisposableEffect(feedback) {
        onDispose { feedback.release() }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            particleSystem.update(0.05f)
            particleTick++
            delay(50)
        }
    }

    LaunchedEffect(selectedSubjectId, soundEnabled) {
        if (selectedSubjectId == null || !soundEnabled) return@LaunchedEffect
        while (isActive) {
            val currentReading = liveReading
            if (currentReading != null) {
                val aurStr = currentReading.replace(",", "").replace(" AUR/s", "").trim()
                val aurVal = aurStr.toLongOrNull() ?: 0L
                val delayMs = when {
                    aurVal == 0L -> -1L
                    aurVal <= 100 -> 700L
                    aurVal <= 500 -> 400L
                    aurVal <= 5000 -> 200L
                    aurVal <= 50000 -> 100L
                    else -> 50L
                }
                if (delayMs > 0) {
                    feedback.geigerTick(soundEnabled)
                    delay(delayMs)
                } else {
                    delay(100)
                }
            } else {
                delay(100)
            }
        }
    }

    LaunchedEffect(frameState) {
        if (selectedSubjectId != null && frameState?.subjects?.none { it.id == selectedSubjectId } != false) {
            selectedSubjectId = null
            scanningSubjectId = null
            scanResult = null
        }
    }
    LaunchedEffect(selectedSubjectId) {
        liveReading = null
        val subjectId = selectedSubjectId ?: return@LaunchedEffect
        val generator = auraGenerators.getOrPut(subjectId) { AuraValueGenerator(subjectId) }
        try {
            while (isActive) {
                val subject = latestFrameState.value?.subjects?.firstOrNull { it.id == subjectId }
                val profile = subject?.profile
                if (profile == null) {
                    liveReading = null
                } else {
                    liveReading = generator.next(profile)
                }
                delay(160)
            }
        } catch (_: CancellationException) {
        } finally {
            liveReading = null
        }
    }
    LaunchedEffect(scanningSubjectId) {
        val subjectId = scanningSubjectId ?: run {
            scanProgress = 0f
            return@LaunchedEffect
        }
        feedback.scanStarted(latestSoundEnabled.value)
        scanResult = null
        val startedAt = System.currentTimeMillis()
        try {
            while (isActive) {
                scanProgress = ((System.currentTimeMillis() - startedAt) / 1_000f).coerceIn(0f, 1f)
                if (scanProgress >= 1f) break
                delay(80)
            }
            scanResult = scanGenerators.getOrPut(subjectId) { AuraScanGenerator(subjectId) }.next()
            delay(4_500)
        } finally {
            if (scanningSubjectId == subjectId) {
                scanningSubjectId = null
                scanProgress = 0f
                scanResult = null
            }
        }
    }
    LaunchedEffect(scanResult) {
        if (scanResult != null) {
            feedback.scanCompleted(latestSoundEnabled.value)
            
            val projected = frameState?.let { projectSubjects(it) }?.firstOrNull { it.subject.id == scanningSubjectId }
            val cx = projected?.box?.center?.x ?: 500f
            val cy = projected?.box?.center?.y ?: 500f
            val color = scanResult?.classification?.let { resultColor(it) } ?: NeonGreen
            particleSystem.burst(cx, cy, 25, color)

            scanPulseProgress.snapTo(0f)
            scanPulseProgress.animateTo(1f, tween(900))
        } else {
            scanPulseProgress.snapTo(0f)
        }
    }
    DisposableEffect(transport) {
        transport.connect()
        onDispose { transport.close() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraTransportPreview(transport)
        SubjectOverlay(
            frameState = frameState,
            selectedSubjectId = selectedSubjectId,
            liveReading = liveReading,
            breatheAlpha = breatheAlpha,
            particleSystem = particleSystem,
            particleTick = particleTick,
            scanPulseProgress = scanPulseProgress.value,
            scanPulseColor = scanPulseColor,
            scanningSubjectId = scanningSubjectId,
            onSelectSubject = {
                if (selectedSubjectId != it) {
                    scanningSubjectId = null
                    scanResult = null
                }
                selectedSubjectId = it
            },
            onScanSubject = { subjectId ->
                if (selectedSubjectId == subjectId && scanningSubjectId == null) {
                    scanningSubjectId = subjectId
                }
            }
        )
        ScanOverlay(
            subjectId = scanningSubjectId,
            progress = scanProgress,
            result = scanResult
        )
        
        val subjectCount = frameState?.subjects?.size ?: 0
        val selectedSubjectProfile = frameState?.subjects?.firstOrNull { it.id == selectedSubjectId }?.profile
        val hudSubjectColor = selectedSubjectProfile?.palette?.let { paletteColor(it) } ?: NeonGreen

        ScannerHud(
            status = status,
            selectedSubjectId = selectedSubjectId,
            liveReading = liveReading,
            scanStatus = when {
                scanningSubjectId != null && scanResult == null ->
                    "SCANNING #${scanningSubjectId} ${(scanProgress * 100).toInt()}%"
                scanResult != null -> "SCAN READY #${scanningSubjectId}"
                else -> null
            },
            soundEnabled = soundEnabled,
            subjectCount = subjectCount,
            subjectColor = hudSubjectColor,
            onToggleSound = { soundEnabled = !soundEnabled },
            onExit = onExit
        )
    }
    BackHandler(onBack = onExit)
}

@Composable
private fun CameraTransportPreview(transport: AuraWebSocket) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewOutputTransform = remember { AtomicReference<OutputTransform?>(null) }
    val imageTransformFactory = remember {
        ImageProxyTransformFactory().apply {
            setUsingCropRect(true)
            setUsingRotationDegrees(true)
        }
    }

    DisposableEffect(lifecycleOwner, transport) {
        var disposed = false
        val updatePreviewTransform = {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                previewOutputTransform.set(previewView.outputTransform)
            } else {
                previewView.post {
                    if (!disposed) previewOutputTransform.set(previewView.outputTransform)
                }
            }
        }
        val layoutListener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updatePreviewTransform()
        }
        val streamObserver = Observer<PreviewView.StreamState> {
            updatePreviewTransform()
        }
        previewView.addOnLayoutChangeListener(layoutListener)
        previewView.previewStreamState.observe(lifecycleOwner, streamObserver)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        fun bindCamera() {
            if (disposed) return
            val viewPort = previewView.viewPort
            if (viewPort == null) {
                previewView.post(::bindCamera)
                return
            }
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(analyzerExecutor) { image ->
                        try {
                            image.sourceToPreviewMatrix(
                                previewOutputTransform.get(),
                                imageTransformFactory
                            )?.let { transform ->
                                val frame = image.toJpeg()
                                transport.sendFrame(
                                    jpeg = frame.bytes,
                                    width = frame.width,
                                    height = frame.height,
                                    sourceWidth = frame.width,
                                    sourceHeight = frame.height,
                                    sourceToPreview = transform
                                )
                            }
                        } catch (error: Exception) {
                            Log.w("AuraDetector", "Skipping unavailable camera frame", error)
                        } finally {
                            image.close()
                        }
                    }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                UseCaseGroup.Builder()
                    .setViewPort(viewPort)
                    .addUseCase(preview)
                    .addUseCase(analysis)
                    .build()
            )
        }
        previewView.post {
            cameraProviderFuture.addListener(::bindCamera, ContextCompat.getMainExecutor(context))
        }

        onDispose {
            disposed = true
            previewView.removeOnLayoutChangeListener(layoutListener)
            previewView.previewStreamState.removeObserver(streamObserver)
            previewOutputTransform.set(null)
            if (cameraProviderFuture.isDone) cameraProviderFuture.get().unbindAll()
            analyzerExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun SubjectOverlay(
    frameState: VisionFrameState?,
    selectedSubjectId: Long?,
    liveReading: String?,
    breatheAlpha: Float,
    particleSystem: ParticleSystem,
    particleTick: Int,
    scanPulseProgress: Float,
    scanPulseColor: Color,
    scanningSubjectId: Long?,
    onSelectSubject: (Long?) -> Unit,
    onScanSubject: (Long) -> Unit
) {
    val projectedSubjects = remember(frameState) {
        frameState?.let(::projectSubjects).orEmpty()
    }
    val latestSubjects = rememberUpdatedState(projectedSubjects)
    val latestSelectionHandler = rememberUpdatedState(onSelectSubject)
    val latestScanHandler = rememberUpdatedState(onScanSubject)
    val latestSelectedSubjectId = rememberUpdatedState(selectedSubjectId)
    if (projectedSubjects.isEmpty() && particleSystem.particles.isEmpty()) return

    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }
    val backplatePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DarkNavy.copy(alpha = 0.75f).toArgb()
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { point ->
                        val hit = findHitSubject(latestSubjects.value, point)
                        if (hit == null) {
                            latestSelectionHandler.value(null)
                        } else if (hit.subject.id == latestSelectedSubjectId.value) {
                            latestScanHandler.value(hit.subject.id)
                        } else {
                            latestSelectionHandler.value(hit.subject.id)
                        }
                    },
                    onTap = { point ->
                        findHitSubject(latestSubjects.value, point)
                            ?.subject
                            ?.id
                            .let(latestSelectionHandler.value)
                    }
                )
            }
    ) {
        val _tick = particleTick

        projectedSubjects.forEach { projected ->
            val subject = projected.subject
            val left = projected.box.left
            val top = projected.box.top
            val right = projected.box.right
            val bottom = projected.box.bottom
            val selected = subject.id == selectedSubjectId
            val baseColor = paletteColor(subject.profile?.palette)
            val color = if (selected) baseColor else baseColor.copy(alpha = 0.7f)
            val contour = projected.contour

            val layers = listOf(
                28f to 0.05f * breatheAlpha,
                18f to 0.10f * breatheAlpha,
                9f to 0.18f * breatheAlpha,
                3f to 0.30f * breatheAlpha
            )

            val alphaMult = if (selected) 1.8f else 1.0f

            if (contour.size >= 3) {
                val cx = contour.map { it.x }.average().toFloat()
                val cy = contour.map { it.y }.average().toFloat()
                
                for ((expand, alpha) in layers) {
                    val finalAlpha = (alpha * alphaMult).coerceAtMost(0.5f)
                    val path = Path().apply {
                        val first = contour.first()
                        val dx = first.x - cx
                        val dy = first.y - cy
                        val dist = kotlin.math.hypot(dx, dy)
                        val scale = if (dist > 0) (dist + expand) / dist else 1f
                        moveTo(cx + dx * scale, cy + dy * scale)
                        contour.drop(1).forEach { pt ->
                            val pdx = pt.x - cx
                            val pdy = pt.y - cy
                            val pdist = kotlin.math.hypot(pdx, pdy)
                            val pscale = if (pdist > 0) (pdist + expand) / pdist else 1f
                            lineTo(cx + pdx * pscale, cy + pdy * pscale)
                        }
                        close()
                    }
                    drawPath(path, color.copy(alpha = finalAlpha), style = Fill)
                }
                
                val crispPath = Path().apply {
                    moveTo(contour.first().x, contour.first().y)
                    contour.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(crispPath, baseColor, style = Stroke(2.5.dp.toPx()))

            } else {
                for ((expand, alpha) in layers) {
                    val finalAlpha = (alpha * alphaMult).coerceAtMost(0.5f)
                    drawRoundRect(
                        color = color.copy(alpha = finalAlpha),
                        topLeft = Offset(left - expand, top - expand),
                        size = Size(right - left + expand * 2, bottom - top + expand * 2),
                        cornerRadius = CornerRadius(20.dp.toPx()),
                        style = Fill
                    )
                }
                drawRoundRect(
                    color = baseColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(2.5.dp.toPx())
                )
            }

            if (selected) {
                val boxWidth = right - left
                val boxHeight = bottom - top
                val bracketLen = (minOf(boxWidth, boxHeight) * 0.2f).coerceAtLeast(16f)
                val strokeW = 2.5.dp.toPx()
                val reticleColor = NeonGreen

                drawLine(reticleColor, Offset(left, top), Offset(left + bracketLen, top), strokeWidth = strokeW)
                drawLine(reticleColor, Offset(left, top), Offset(left, top + bracketLen), strokeWidth = strokeW)
                drawLine(reticleColor, Offset(right, top), Offset(right - bracketLen, top), strokeWidth = strokeW)
                drawLine(reticleColor, Offset(right, top), Offset(right, top + bracketLen), strokeWidth = strokeW)
                drawLine(reticleColor, Offset(left, bottom), Offset(left + bracketLen, bottom), strokeWidth = strokeW)
                drawLine(reticleColor, Offset(left, bottom), Offset(left, bottom - bracketLen), strokeWidth = strokeW)
                drawLine(reticleColor, Offset(right, bottom), Offset(right - bracketLen, bottom), strokeWidth = strokeW)
                drawLine(reticleColor, Offset(right, bottom), Offset(right, bottom - bracketLen), strokeWidth = strokeW)

                val cx = left + boxWidth / 2f
                val cy = top + boxHeight / 2f
                val crossLenX = boxWidth * 0.3f
                val crossLenY = boxHeight * 0.3f
                drawLine(reticleColor.copy(alpha = 0.25f), Offset(cx - crossLenX / 2f, cy), Offset(cx + crossLenX / 2f, cy), strokeWidth = strokeW)
                drawLine(reticleColor.copy(alpha = 0.25f), Offset(cx, cy - crossLenY / 2f), Offset(cx, cy + crossLenY / 2f), strokeWidth = strokeW)
            }

            drawIntoCanvas { canvas ->
                labelPaint.color = baseColor.toArgb()
                labelPaint.textSize = 14.dp.toPx()
                val labelText = "SUBJECT #${subject.id}"
                
                val labelX = (left + 2.dp.toPx()).coerceIn(0f, (size.width - labelPaint.measureText(labelText)).coerceAtLeast(0f))
                val labelY = (top + labelPaint.textSize + 2.dp.toPx())
                    .coerceIn(labelPaint.textSize, (size.height - labelPaint.textSize * 2f).coerceAtLeast(labelPaint.textSize))

                val textWidth = labelPaint.measureText(labelText)
                canvas.nativeCanvas.drawRoundRect(
                    labelX - 4.dp.toPx(), labelY - labelPaint.textSize - 2.dp.toPx(),
                    labelX + textWidth + 4.dp.toPx(), labelY + 3.dp.toPx(),
                    4.dp.toPx(), 4.dp.toPx(),
                    backplatePaint
                )
                canvas.nativeCanvas.drawText(labelText, labelX, labelY, labelPaint)

                if (selected && liveReading != null) {
                    val readingWidth = labelPaint.measureText(liveReading)
                    val readingY = (labelY + labelPaint.textSize + 4.dp.toPx())
                        .coerceAtMost(size.height - 2.dp.toPx())
                    
                    canvas.nativeCanvas.drawRoundRect(
                        labelX - 4.dp.toPx(), readingY - labelPaint.textSize - 2.dp.toPx(),
                        labelX + readingWidth + 4.dp.toPx(), readingY + 3.dp.toPx(),
                        4.dp.toPx(), 4.dp.toPx(),
                        backplatePaint
                    )
                    canvas.nativeCanvas.drawText(
                        liveReading,
                        labelX,
                        readingY,
                        labelPaint
                    )
                }
            }

            if (scanPulseProgress > 0f && scanningSubjectId == subject.id) {
                val subjectCenter = projected.box.center
                for (ring in 0..2) {
                    val ringDelay = ring * 0.12f
                    val ringProgress = ((scanPulseProgress - ringDelay) / (1f - ringDelay)).coerceIn(0f, 1f)
                    if (ringProgress > 0f) {
                        val maxRadius = maxOf(size.width, size.height) * 0.4f
                        val radius = ringProgress * maxRadius
                        val alpha = (1f - ringProgress) * 0.5f
                        drawCircle(
                            color = scanPulseColor.copy(alpha = alpha),
                            radius = radius,
                            center = subjectCenter,
                            style = Stroke(2.5f.dp.toPx() * (1f - ringProgress * 0.5f))
                        )
                    }
                }
            }
        }

        particleSystem.spawnAmbient(projectedSubjects)
        with(particleSystem) {
            drawParticles()
        }
    }
}

private class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, var maxLife: Float,
    var size: Float, var color: Color
)

private class ParticleSystem {
    val particles = mutableListOf<Particle>()
    private val maxParticles = 150

    fun update(dt: Float) {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 0.98f
            p.vy *= 0.98f
            p.life -= dt / p.maxLife
            if (p.life <= 0f) iter.remove()
        }
    }

    fun spawnAmbient(subjects: List<ProjectedSubject>) {
        if (particles.size >= maxParticles) return
        for (projected in subjects) {
            if (particles.size >= maxParticles) break
            val box = projected.box
            val color = paletteColor(projected.subject.profile?.palette)
            repeat(if (Random.nextFloat() < 0.3f) 1 else 0) {
                particles.add(Particle(
                    x = box.left + Random.nextFloat() * box.width,
                    y = box.bottom - Random.nextFloat() * box.height * 0.3f,
                    vx = Random.nextFloat() * 20f - 10f,
                    vy = -(Random.nextFloat() * 40f + 15f),
                    life = 1f,
                    maxLife = Random.nextFloat() * 1.2f + 0.6f,
                    size = Random.nextFloat() * 3.5f + 1.5f,
                    color = color
                ))
            }
        }
    }

    fun burst(centerX: Float, centerY: Float, count: Int, color: Color) {
        repeat(count.coerceAtMost(maxParticles - particles.size)) {
            val angle = Random.nextFloat() * 2f * kotlin.math.PI.toFloat()
            val speed = Random.nextFloat() * 180f + 60f
            particles.add(Particle(
                x = centerX + Random.nextFloat() * 12f - 6f,
                y = centerY + Random.nextFloat() * 12f - 6f,
                vx = kotlin.math.cos(angle) * speed,
                vy = kotlin.math.sin(angle) * speed,
                life = 1f,
                maxLife = Random.nextFloat() * 0.7f + 0.3f,
                size = Random.nextFloat() * 5f + 2f,
                color = color
            ))
        }
    }

    fun DrawScope.drawParticles() {
        for (p in particles) {
            val alpha = (p.life * 0.65f).coerceIn(0f, 1f)
            drawCircle(
                color = p.color.copy(alpha = alpha),
                radius = p.size * p.life.coerceIn(0.3f, 1f),
                center = Offset(p.x, p.y)
            )
        }
    }
}

private fun findHitSubject(subjects: List<ProjectedSubject>, point: Offset): ProjectedSubject? =
    subjects.asReversed().firstOrNull { it.contains(point) }

private data class ProjectedSubject(
    val subject: com.auradetector.transport.VisionSubject,
    val box: ComposeRect,
    val contour: List<Offset>
) {
    fun contains(point: Offset): Boolean =
        if (contour.size >= 3) pointInPolygon(point, contour) else box.contains(point)
}

private fun projectSubjects(frameState: VisionFrameState): List<ProjectedSubject> {
    fun project(point: VisionPoint): Offset {
        val sourceX = point.x * frameState.sourceWidth
        val sourceY = point.y * frameState.sourceHeight
        val transform = frameState.sourceToPreview
        val denominator = transform[6] * sourceX + transform[7] * sourceY + transform[8]
        return Offset(
            (transform[0] * sourceX + transform[1] * sourceY + transform[2]) / denominator,
            (transform[3] * sourceX + transform[4] * sourceY + transform[5]) / denominator
        )
    }

    return frameState.subjects.map { subject ->
        val boxCorners = listOf(
            VisionPoint(subject.x, subject.y),
            VisionPoint(subject.x + subject.width, subject.y),
            VisionPoint(subject.x + subject.width, subject.y + subject.height),
            VisionPoint(subject.x, subject.y + subject.height)
        ).map(::project)
        val box = ComposeRect(
            left = boxCorners.minOf { it.x },
            top = boxCorners.minOf { it.y },
            right = boxCorners.maxOf { it.x },
            bottom = boxCorners.maxOf { it.y }
        )
        ProjectedSubject(subject, box, subject.contour.map(::project))
    }
}

private fun pointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
    var inside = false
    var previous = polygon.lastIndex
    for (index in polygon.indices) {
        val currentPoint = polygon[index]
        val previousPoint = polygon[previous]
        val crosses = (currentPoint.y > point.y) != (previousPoint.y > point.y)
        if (crosses) {
            val intersectionX =
                (previousPoint.x - currentPoint.x) * (point.y - currentPoint.y) /
                    (previousPoint.y - currentPoint.y) + currentPoint.x
            if (point.x < intersectionX) inside = !inside
        }
        previous = index
    }
    return inside
}

private data class AuraScanResult(
    val base: String,
    val modifier: String,
    val final: String,
    val classification: String
)

private class ScanFeedback(context: Context) {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun scanStarted(soundEnabled: Boolean) {
        vibrate(longArrayOf(0, 24), -1)
        if (soundEnabled) toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
    }

    fun scanCompleted(soundEnabled: Boolean) {
        vibrate(longArrayOf(0, 40, 60, 40, 60, 100), -1)
        if (soundEnabled) toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 250)
    }

    fun geigerTick(soundEnabled: Boolean) {
        if (!soundEnabled) return
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 18)
    }

    fun release() = toneGenerator.release()

    private fun vibrate(pattern: LongArray, repeat: Int) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            device.vibrate(VibrationEffect.createWaveform(pattern, repeat))
        } else {
            @Suppress("DEPRECATION")
            device.vibrate(pattern, repeat)
        }
    }
}

private class AuraScanGenerator(subjectId: Long) {
    private val random = Random(subjectId.toInt() xor 0x51A7C0DE)

    fun next(): AuraScanResult {
        val roll = random.nextDouble()
        if (roll < 0.015) {
            return AuraScanResult(
                base = "+∞",
                modifier = "CURSE −1,000 AUR",
                final = "+∞",
                classification = "AURA OVERFLOW"
            )
        }
        if (roll < 0.03) {
            return AuraScanResult(
                base = "−∞",
                modifier = "BLESSING +184 AUR",
                final = "−∞",
                classification = "NEGATIVE AURA SINGULARITY"
            )
        }

        val baseMagnitude = when {
            roll < 0.58 -> random.nextLong(1, 1_001)
            roll < 0.88 -> random.nextLong(5_000, 100_001)
            roll < 0.99 -> random.nextLong(100_000, 1_000_001)
            else -> 67_696_969L
        }
        val base = if (random.nextBoolean()) baseMagnitude else -baseMagnitude
        val modifierMagnitude = random.nextLong(100, 2_001)
        val modifier = if (random.nextBoolean()) modifierMagnitude else -modifierMagnitude
        val final = base + modifier
        val classification = when {
            kotlin.math.abs(final) >= 1_000_000 -> "EXTREME FIELD"
            baseMagnitude == 67_696_969L -> "MILESTONE SIGNAL"
            final >= 0 -> "RADIANT"
            else -> "VOID-ADJACENT"
        }
        return AuraScanResult(
            base = signed(base),
            modifier = "${if (modifier >= 0) "BLESSING" else "CURSE"} ${signed(modifier)} AUR",
            final = signed(final),
            classification = classification
        )
    }

    private fun signed(value: Long): String =
        "${if (value >= 0) "+" else "−"}${NumberFormat.getIntegerInstance(Locale.US).format(kotlin.math.abs(value))}"
}

@Composable
private fun ScanOverlay(subjectId: Long?, progress: Float, result: AuraScanResult?) {
    if (subjectId == null) return
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val scanPulse = if (animationsEnabled && result == null) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "scanPulse")
        val pulse by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.95f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(420),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "scanPulseAlpha"
        )
        pulse
    } else {
        1f
    }

    val resColor = result?.classification?.let { resultColor(it) } ?: NeonGreen

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 180.dp, bottom = 190.dp)
            .zIndex(10f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .then(
                    if (result != null) {
                        Modifier
                            .border(6.dp, resColor.copy(alpha = 0.15f))
                            .border(4.dp, resColor.copy(alpha = 0.3f))
                    } else Modifier
                )
                .background(DarkNavy.copy(alpha = if (result == null) 0.90f + scanPulse * 0.04f else 0.94f))
                .border(2.dp, (if (result == null) OrangeWarning.copy(alpha = scanPulse) else resColor))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (result == null) {
                Text("SCANNING SUBJECT #$subjectId", color = OrangeWarning)
                Text("CALIBRATING ${(progress * 100).toInt()}%", color = CyanAccent)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(CyanAccent.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(8.dp)
                            .background(OrangeWarning.copy(alpha = scanPulse))
                    )
                }
                Text("NEGOTIATING WITH THE FIELD", color = CyanAccent)
            } else {
                Text("AURA SCAN COMPLETE", color = resColor)
                Text("BASE: ${result.base}", color = CyanAccent)
                Text(result.modifier, color = CyanAccent)
                Text("FINAL: ${result.final}", color = resColor)
                Text(result.classification, color = resColor)
            }
        }
    }
}

private class AuraValueGenerator(subjectId: Long) {
    private val random = Random(subjectId.toInt() xor 0x5EEDBEEF)
    private var profileKey: String? = null
    private var value: Double? = null

    fun next(profile: VisionProfile): String {
        val key = "${profile.band}|${profile.min}|${profile.max}|${profile.palette}"
        if (key != profileKey) {
            profileKey = key
            value = null
        }

        if (profile.max == "∞") return "∞ AUR/s"
        val minimum = profile.min.toDoubleOrNull() ?: return "— AUR/s"
        val maximum = profile.max.toDoubleOrNull() ?: return "— AUR/s"
        if (maximum <= minimum) return format(minimum)

        val positiveMinimum = maxOf(1.0, minimum)
        val current = value ?: exp(
            ln(positiveMinimum) + random.nextDouble() * (ln(maximum) - ln(positiveMinimum))
        )
        val next = if (maximum / positiveMinimum >= 100.0) {
            current * exp(random.nextDouble(-0.08, 0.08))
        } else {
            current + (maximum - minimum) * random.nextDouble(-0.08, 0.08)
        }
        value = next.coerceIn(minimum, maximum)
        return format(value ?: minimum)
    }

    private fun format(value: Double): String =
        "${NumberFormat.getIntegerInstance(Locale.US).format(value)} AUR/s"
}

private fun ImageProxy.sourceToPreviewMatrix(
    previewOutput: OutputTransform?,
    imageTransformFactory: ImageProxyTransformFactory
): FloatArray? {
    previewOutput ?: return null
    val sourceOutput = imageTransformFactory.getOutputTransform(this)
    return Matrix().apply {
        CoordinateTransform(sourceOutput, previewOutput).transform(this)
    }.let { matrix ->
        FloatArray(9).also(matrix::getValues)
    }
}

@Composable
private fun ScannerHud(
    status: TransportStatus,
    selectedSubjectId: Long?,
    liveReading: String?,
    scanStatus: String?,
    soundEnabled: Boolean,
    subjectCount: Int,
    subjectColor: Color,
    onToggleSound: () -> Unit,
    onExit: () -> Unit
) {
    val statusColor = when (status.state) {
        VisionLinkState.READY -> NeonGreen
        VisionLinkState.CONNECTING -> OrangeWarning
        VisionLinkState.DEGRADED -> OrangeWarning
        VisionLinkState.OFFLINE -> ErrorRed
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(DarkNavy.copy(alpha = 0.9f))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AURA RADIATION MONITOR", color = CyanAccent)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(status.detail, color = statusColor)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val metrics = buildString {
                status.lastFrameId?.let { append("FRAME: $it  ") }
                status.latencyMs?.let { append("${it}ms  ") }
                append("SUBJECTS: $subjectCount")
            }
            Text(metrics, color = CyanAccent.copy(alpha = 0.7f))
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 62.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(statusColor)
        )

        if (selectedSubjectId != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 74.dp, start = 16.dp)
                    .background(DarkNavy.copy(alpha = 0.85f))
                    .border(1.dp, subjectColor)
                    .padding(8.dp)
            ) {
                Text("SUBJECT #$selectedSubjectId", color = subjectColor)
                if (liveReading != null) {
                    Text(liveReading, color = CyanAccent)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onToggleSound,
                colors = ButtonDefaults.buttonColors(containerColor = DarkNavy.copy(alpha = 0.90f)),
                modifier = Modifier.border(1.dp, CyanAccent)
            ) {
                Text(if (soundEnabled) "SOUND ON" else "SOUND OFF", color = CyanAccent)
            }
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = DarkNavy.copy(alpha = 0.90f)),
                modifier = Modifier.border(1.dp, ErrorRed)
            ) {
                Text("DISCONNECT", color = ErrorRed)
            }
        }
    }
}

@Composable
private fun PermissionRequired(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("CAMERA PERMISSION REQUIRED", color = ErrorRed)
        Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 16.dp)) {
            Text("ALLOW CAMERA")
        }
    }
}

private data class JpegFrame(val bytes: ByteArray, val width: Int, val height: Int)

private fun ImageProxy.toJpeg(quality: Int = 70, maxLongEdge: Int = 640): JpegFrame {
    require(format == ImageFormat.YUV_420_888) { "Expected YUV_420_888 camera frame" }
    val sourceLeft = cropRect.left and 1.inv()
    val sourceTop = cropRect.top and 1.inv()
    val sourceWidth = cropRect.width() and 1.inv()
    val sourceHeight = cropRect.height() and 1.inv()
    require(sourceWidth >= 2 && sourceHeight >= 2) { "Camera crop is too small" }

    val sourceLongEdge = maxOf(sourceWidth, sourceHeight)
    val targetWidth = if (sourceLongEdge <= maxLongEdge) sourceWidth else {
        maxOf(2, (sourceWidth.toLong() * maxLongEdge / sourceLongEdge).toInt() and 1.inv())
    }
    val targetHeight = if (sourceLongEdge <= maxLongEdge) sourceHeight else {
        maxOf(2, (sourceHeight.toLong() * maxLongEdge / sourceLongEdge).toInt() and 1.inv())
    }
    val nv21 = ByteArray(targetWidth * targetHeight * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8)
    copyPlane(
        planes[0], sourceLeft, sourceTop, sourceWidth, sourceHeight, targetWidth, targetHeight, nv21, 0
    )
    copyChromaPlanes(
        planes[1], planes[2], sourceLeft, sourceTop, sourceWidth, sourceHeight,
        targetWidth, targetHeight, nv21, targetWidth * targetHeight
    )

    val jpeg = ByteArrayOutputStream().use { stream ->
        YuvImage(nv21, ImageFormat.NV21, targetWidth, targetHeight, null)
            .compressToJpeg(Rect(0, 0, targetWidth, targetHeight), quality, stream)
        stream.toByteArray()
    }
    return jpeg.rotate(imageInfo.rotationDegrees, targetWidth, targetHeight, quality)
}

private fun copyPlane(
    plane: ImageProxy.PlaneProxy,
    sourceLeft: Int,
    sourceTop: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    output: ByteArray,
    outputOffset: Int
) {
    val buffer = plane.buffer
    val start = buffer.position()
    for (targetRow in 0 until targetHeight) {
        val sourceRow = sourceTop + targetRow * sourceHeight / targetHeight
        val rowStart = start + sourceRow * plane.rowStride
        for (targetColumn in 0 until targetWidth) {
            val sourceColumn = sourceLeft + targetColumn * sourceWidth / targetWidth
            output[outputOffset + targetRow * targetWidth + targetColumn] =
                buffer.get(rowStart + sourceColumn * plane.pixelStride)
        }
    }
}

private fun copyChromaPlanes(
    uPlane: ImageProxy.PlaneProxy,
    vPlane: ImageProxy.PlaneProxy,
    sourceLeft: Int,
    sourceTop: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    output: ByteArray,
    outputOffset: Int
) {
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    val uStart = uBuffer.position()
    val vStart = vBuffer.position()
    var outputIndex = outputOffset
    for (targetRow in 0 until targetHeight / 2) {
        val sourceRow = sourceTop / 2 + targetRow * sourceHeight / targetHeight
        for (targetColumn in 0 until targetWidth / 2) {
            val sourceColumn = sourceLeft / 2 + targetColumn * sourceWidth / targetWidth
            output[outputIndex++] = vBuffer.get(
                vStart + sourceRow * vPlane.rowStride + sourceColumn * vPlane.pixelStride
            )
            output[outputIndex++] = uBuffer.get(
                uStart + sourceRow * uPlane.rowStride + sourceColumn * uPlane.pixelStride
            )
        }
    }
}

private fun ByteArray.rotate(rotationDegrees: Int, width: Int, height: Int, quality: Int): JpegFrame {
    val rotation = ((rotationDegrees % 360) + 360) % 360
    if (rotation == 0) return JpegFrame(this, width, height)

    val source = BitmapFactory.decodeByteArray(this, 0, size)
        ?: error("Camera JPEG could not be decoded for rotation")
    val rotated = Bitmap.createBitmap(
        source, 0, 0, source.width, source.height, Matrix().apply { postRotate(rotation.toFloat()) }, true
    )
    if (rotated !== source) source.recycle()
    return try {
        val bytes = ByteArrayOutputStream().use { stream ->
            rotated.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }
        JpegFrame(bytes, rotated.width, rotated.height)
    } finally {
        rotated.recycle()
    }
}
