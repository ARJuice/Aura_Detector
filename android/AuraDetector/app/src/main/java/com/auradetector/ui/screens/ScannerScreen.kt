package com.auradetector.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.auradetector.data.ServerConfig
import com.auradetector.transport.AuraWebSocket
import com.auradetector.transport.TransportStatus
import com.auradetector.transport.VisionLinkState
import com.auradetector.ui.theme.CyanAccent
import com.auradetector.ui.theme.DarkNavy
import com.auradetector.ui.theme.ErrorRed
import com.auradetector.ui.theme.NeonGreen
import com.auradetector.ui.theme.OrangeWarning
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

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
    DisposableEffect(transport) {
        transport.connect()
        onDispose { transport.close() }
    }

    CameraTransportPreview(transport)
    ScannerHud(status = status, onExit = onExit)
    BackHandler(onBack = onExit)
}

@Composable
private fun CameraTransportPreview(transport: AuraWebSocket) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner, transport) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val bindCamera = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(Size(640, 360))
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(analyzerExecutor) { image ->
                        try {
                            val frame = image.toJpeg()
                            transport.sendFrame(frame.bytes, frame.width, frame.height)
                        } finally {
                            image.close()
                        }
                    }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        }
        cameraProviderFuture.addListener(bindCamera, ContextCompat.getMainExecutor(context))

        onDispose {
            if (cameraProviderFuture.isDone) cameraProviderFuture.get().unbindAll()
            analyzerExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun ScannerHud(status: TransportStatus, onExit: () -> Unit) {
    val color = when (status.state) {
        VisionLinkState.READY -> NeonGreen
        VisionLinkState.CONNECTING -> OrangeWarning
        VisionLinkState.DEGRADED -> OrangeWarning
        VisionLinkState.OFFLINE -> ErrorRed
    }
    val text = buildString {
        append(status.detail)
        status.lastFrameId?.let { append("  FRAME: $it") }
        status.latencyMs?.let { append("  ${it}ms") }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .background(DarkNavy.copy(alpha = 0.86f))
                .border(1.dp, color)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("AURA RADIATION MONITOR", color = CyanAccent)
            Text(text, color = color)
        }
        Button(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkNavy)
        ) {
            Text("DISCONNECT", color = CyanAccent)
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
    val sourceLongEdge = maxOf(width, height)
    val targetWidth = if (sourceLongEdge <= maxLongEdge) width else {
        maxOf(2, (width.toLong() * maxLongEdge / sourceLongEdge).toInt() and 1.inv())
    }
    val targetHeight = if (sourceLongEdge <= maxLongEdge) height else {
        maxOf(2, (height.toLong() * maxLongEdge / sourceLongEdge).toInt() and 1.inv())
    }
    val nv21 = ByteArray(targetWidth * targetHeight * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8)
    copyPlane(planes[0], width, height, targetWidth, targetHeight, nv21, 0)
    copyChromaPlanes(
        planes[1], planes[2], width, height, targetWidth, targetHeight, nv21, targetWidth * targetHeight
    )

    val jpeg = ByteArrayOutputStream().use { stream ->
        YuvImage(nv21, ImageFormat.NV21, targetWidth, targetHeight, null)
            .compressToJpeg(Rect(0, 0, targetWidth, targetHeight), quality, stream)
        stream.toByteArray()
    }
    return JpegFrame(jpeg, targetWidth, targetHeight)
}

private fun copyPlane(
    plane: ImageProxy.PlaneProxy,
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
        val sourceRow = targetRow * sourceHeight / targetHeight
        val rowStart = start + sourceRow * plane.rowStride
        for (targetColumn in 0 until targetWidth) {
            val sourceColumn = targetColumn * sourceWidth / targetWidth
            output[outputOffset + targetRow * targetWidth + targetColumn] =
                buffer.get(rowStart + sourceColumn * plane.pixelStride)
        }
    }
}

private fun copyChromaPlanes(
    uPlane: ImageProxy.PlaneProxy,
    vPlane: ImageProxy.PlaneProxy,
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
        val sourceRow = targetRow * sourceHeight / targetHeight
        for (targetColumn in 0 until targetWidth / 2) {
            val sourceColumn = targetColumn * sourceWidth / targetWidth
            output[outputIndex++] = vBuffer.get(
                vStart + sourceRow * vPlane.rowStride + sourceColumn * vPlane.pixelStride
            )
            output[outputIndex++] = uBuffer.get(
                uStart + sourceRow * uPlane.rowStride + sourceColumn * uPlane.pixelStride
            )
        }
    }
}
