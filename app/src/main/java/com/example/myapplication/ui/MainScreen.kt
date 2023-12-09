package com.example.myapplication.ui

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.WebSocketServerManager
import com.example.myapplication.ui.camera.CameraScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
//import com.google.zxing.*
//import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.delay
import java.net.NetworkInterface
import java.util.*
import java.util.concurrent.Executors

fun getLocalIpAddress(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val iface = interfaces.nextElement()
            val addresses = iface.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (!addr.isLoopbackAddress && addr.hostAddress.indexOf(':') < 0) {
                    return addr.hostAddress
                }
            }
        }
    } catch (ex: Exception) {
        ex.printStackTrace()
    }
    return "Unable to get IP Address"
}

enum class ServerStatus {
    Stopped, Starting, Running
}


@Composable
@ExperimentalGetImage
private fun MainContent(webSocketServerManager: WebSocketServerManager) {
    var qrCodeValue by remember { mutableStateOf("") }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
//    val previewView = remember { PreviewView(context) }

    val serverStatus = remember { mutableStateOf(ServerStatus.Stopped) }
    val serverManager = remember { WebSocketServerManager("0.0.0.0", 4439) } // 예시 IP 및 포트

    val localIpAddress = getLocalIpAddress()
    Column(modifier = Modifier.padding(16.dp)) {
        Text("나의 로컬 네트워크 아이피: 172.16.234.1", fontSize = 18.sp)

        Button(onClick = {
            serverStatus.value = ServerStatus.Starting
            try {
                serverManager.startServer()
                serverStatus.value = ServerStatus.Running
            } catch (e: Exception) {
                e.printStackTrace()
                serverStatus.value = ServerStatus.Stopped
            }
        }) {
            Text("웹소켓 서버 시작")
        }

        when (serverStatus.value) {
            ServerStatus.Starting -> Text("서버 시작 중...")
            ServerStatus.Running -> Text("서버 실행 중...")
            ServerStatus.Stopped -> Text("서버 중지됨")
        }

    }
}
@ExperimentalGetImage

//fun startQrCodeScanner(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView, onQrCodeDetected: (String) -> Unit) {
//    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
//    cameraProviderFuture.addListener({
//        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
//        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//        val preview = Preview.Builder().build().also {
//            it.setSurfaceProvider(previewView.surfaceProvider)
//        }
//        val imageAnalysis = ImageAnalysis.Builder().build().also {
//            it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
//                val frame = imageProxy.image
//                if (frame != null) {
//                    val buffer = frame.planes[0].buffer
//                    val data = ByteArray(buffer.remaining())
//                    buffer.get(data)
//                    val source = PlanarYUVLuminanceSource(data, frame.width, frame.height, 0, 0, frame.width, frame.height, false)
//                    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
//                    try {
//                        val result = MultiFormatReader().apply {
//                            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
//                        }.decode(binaryBitmap)
//                        onQrCodeDetected(result.text)
//                    } catch (e: Exception) {
//                        // QR 코드 인식 실패 처리
//                    } finally {
//                        imageProxy.close()
//                    }
//                }
//            }
//        }
//        try {
//            cameraProvider.unbindAll()
//            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
//        } catch (exc: Exception) {
//            Log.e("CameraXApp", "카메라 바인딩에 실패했습니다.", exc)
//        }
//    }, ContextCompat.getMainExecutor(context))
//}

//@Composable
//fun AppNavigation() {
//    val navController = rememberNavController()
//    NavHost(navController = navController, startDestination = "profile") {
//        composable("connected") { ConnectedScreen(/*...*/) }
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val cameraPermissionState: PermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    NavHost(navController = navController,
        startDestination = "mainScreen"
    ) {
        composable("mainScreen") {
            MainContent(
                navController = navController,
                hasPermission = cameraPermissionState.status.isGranted,
                onRequestPermission = cameraPermissionState::launchPermissionRequest
            )
        }
        composable("cameraScreen") {
            CameraScreen()
        }
    }
}

@Composable
private fun MainContent(navController: NavController,
                        hasPermission: Boolean,
                        onRequestPermission: () -> Unit) {
    val requestPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted, navigate to the camera screen or perform other actions
                navController.navigate("cameraScreen")
            }
        }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { /*TODO*/ }) {
            Text("QRcode ", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { navController.navigate("cameraScreen") }) {
            Text("손 인식 기능 시작", fontSize = 18.sp)
        }
    }
}

@Composable
fun ConnectedScreen() {
    Text("연결이 확인 됐습니다")
}


@ExperimentalGetImage
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(serverManager: WebSocketServerManager) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val navController = rememberNavController()

    LaunchedEffect(key1 = true) {
        cameraPermissionState.launchPermissionRequest()
    }

    LaunchedEffect(key1 = Unit) {
        while (true) {
            if (serverManager.isReadyReceived()) {
                navController.navigate("newScreen")
                break
            }
            delay(1000) // 1초마다 확인
        }
    }


    when {
        cameraPermissionState.status.isGranted -> {
            MainContent(serverManager)
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

@Preview
@Composable
private fun Preview_MainContent() {
    MainContent(navController = rememberNavController(),
        hasPermission = true,
        onRequestPermission = {})
}
