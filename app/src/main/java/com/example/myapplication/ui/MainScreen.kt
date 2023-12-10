package com.example.myapplication.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

enum class ServerStatus {
    Stopped, Starting, Running
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(serverManager: WebSocketServerManager) {
    val navController = rememberNavController()
    val cameraPermissionState: PermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    NavHost(navController = navController, startDestination = "mainScreen") {
        composable("mainScreen") {
            MainContent(
                navController = navController,
                hasPermission = cameraPermissionState.status.isGranted,
                onRequestPermission = cameraPermissionState::launchPermissionRequest
            )
        }
        composable("cameraScreen") {
            CameraScreen(
                navController = navController
            )
        }
    }
}

@Composable
private fun MainContent(
    navController: NavController,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    var qrCodeValue by remember { mutableStateOf("") }
    val serverStatus = remember { mutableStateOf(ServerStatus.Stopped) }
    val serverManager = remember { WebSocketServerManager("0.0.0.0", 4439) }

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

        Button(onClick = { navController.navigate("cameraScreen") }) {
            Text("손 인식 기능 시작", fontSize = 18.sp)
        }

        when (serverStatus.value) {
            ServerStatus.Starting -> Text("서버 시작 중...")
            ServerStatus.Running -> Text("서버 실행 중...")
            ServerStatus.Stopped -> Text("서버 중지됨")
        }
    }
}

@Preview
@Composable
private fun Preview_MainContent() {
    MainContent(
        navController = rememberNavController(),
        hasPermission = true,
        onRequestPermission = {}
    )
}
