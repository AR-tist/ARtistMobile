package com.example.myapplication.ui

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.compose.runtime.Composable
import com.example.myapplication.ui.camera.CameraScreen
import com.example.myapplication.ui.no_permission.NoPermissionScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted

import com.google.accompanist.permissions.rememberPermissionState

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

import androidx.compose.runtime.*
import com.google.accompanist.permissions.shouldShowRationale
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.camera.core.Preview



@Composable
@ExperimentalGetImage
private fun MainContent() {
    var qrCodeValue by remember { mutableStateOf("") }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    Column(modifier = Modifier.padding(16.dp)) {
        AndroidView(factory = { previewView })
        Button(onClick = {
            startQrCodeScanner(context, lifecycleOwner, previewView) { qrCode ->
                qrCodeValue = qrCode
            }
        }) {
            Text("QR 코드 스캐너 시작", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { /* 손 인식 기능 실행 로직 */ }) {
            Text("손 인식 기능 시작", fontSize = 18.sp)
        }

        if (qrCodeValue.isNotEmpty()) {
            Text("QR 코드 값: $qrCodeValue")
        }
    }
}
@ExperimentalGetImage

fun startQrCodeScanner(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView, onQrCodeDetected: (String) -> Unit) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val imageAnalysis = ImageAnalysis.Builder().build().also {
            it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                val frame = imageProxy.image
                if (frame != null) {
                    val buffer = frame.planes[0].buffer
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)
                    val source = PlanarYUVLuminanceSource(data, frame.width, frame.height, 0, 0, frame.width, frame.height, false)
                    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                    try {
                        val result = MultiFormatReader().apply {
                            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                        }.decode(binaryBitmap)
                        onQrCodeDetected(result.text)
                    } catch (e: Exception) {
                        // QR 코드 인식 실패 처리
                    } finally {
                        imageProxy.close()
                    }
                }
            }
        }
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
        } catch (exc: Exception) {
            Log.e("CameraXApp", "카메라 바인딩에 실패했습니다.", exc)
        }
    }, ContextCompat.getMainExecutor(context))
}

@ExperimentalGetImage
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen() {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    LaunchedEffect(key1 = true) {
        cameraPermissionState.launchPermissionRequest()
    }
    when {
        cameraPermissionState.status.isGranted -> {
            MainContent()
        }
        cameraPermissionState.status.shouldShowRationale -> {
            Text("이 앱은 카메라 접근 권한이 필요합니다.")
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text("권한 요청")
            }
        }
        else -> {
            Text("카메라 권한이 거부되었습니다.")
        }
    }
}