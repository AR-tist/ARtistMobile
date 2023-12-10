package com.example.myapplication.ui

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.example.myapplication.WebSocketServerManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.delay
import java.net.BindException
import java.net.NetworkInterface
import java.util.*


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
    Stopped, Starting, Running;

    companion object {
        @JvmField
        var value: ServerStatus =  Stopped
    }
}


@Composable
@ExperimentalGetImage
private fun MainContent(webSocketServerManager: WebSocketServerManager, navController: NavController) {
    var qrCodeValue by remember { mutableStateOf("") }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current


    val serverStatus = remember { mutableStateOf(ServerStatus.Stopped) }

    val localIpAddress = getLocalIpAddress()
    Column(modifier = Modifier.padding(16.dp)) {
        Text("나의 로컬 네트워크 아이피: $localIpAddress", fontSize = 18.sp)

        Button(onClick = {
            serverStatus.value = ServerStatus.Starting
            try {
                webSocketServerManager.startServer()
                serverStatus.value = ServerStatus.Running
            } catch (e: BindException) {
                println("Error: Port is already in use. Please choose a different port.")
                serverStatus.value = ServerStatus.Stopped
            } catch (e: Exception) {
                e.printStackTrace()
                serverStatus.value = ServerStatus.Stopped
            }
        }) {
            Text("웹소켓 서버 시작")
        }

        Button(onClick = {
            navController.navigate("connected_screen")
        }) {
            Text(text = "연결 된 화면으로 강제이동")
        }

        when (serverStatus.value) {
            ServerStatus.Starting -> Text("서버 시작 중...")
            ServerStatus.Running -> Text("서버 실행 중...")
            ServerStatus.Stopped -> Text("서버 중지됨")
        }

    }
}
@Composable
fun ConnectedScreen(webSocketServerManager: WebSocketServerManager) {
    Text("연결이 확인 됐습니다")

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ButtonWithBroadcast(webSocketServerManager, "도")
        Spacer(modifier = Modifier.height(8.dp))
        ButtonWithBroadcast(webSocketServerManager, "레")
        Spacer(modifier = Modifier.height(8.dp))
        ButtonWithBroadcast(webSocketServerManager, "미")
        Spacer(modifier = Modifier.height(8.dp))
        ButtonWithBroadcast(webSocketServerManager, "파")
        Spacer(modifier = Modifier.height(8.dp))
        ButtonWithBroadcast(webSocketServerManager, "솔")
    }
}
@Composable
fun ButtonWithBroadcast(webSocketServerManager: WebSocketServerManager, note: String) {
    Button(onClick = { webSocketServerManager.broadcast(note) }) {
        Text(text = note)
    }
}

@ExperimentalGetImage
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(serverManager: WebSocketServerManager, navController: NavController) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(key1 = true) {
        cameraPermissionState.launchPermissionRequest()
    }

    LaunchedEffect(key1 = Unit) {
        while (true) {
            if (serverManager.isReadyReceived()) {
                navController.navigate("connected_screen")
                break
            }
            Log.d("isReady", "isReady? = ${serverManager.isReadyReceived()}")
            delay(1000) // 1초마다 확인
        }
    }


    when {
        cameraPermissionState.status.isGranted -> {
            MainContent(serverManager, navController)
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