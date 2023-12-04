package com.example.myapplication.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHost
import com.example.myapplication.ui.camera.CameraScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

@OptIn(ExperimentalPermissionsApi::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val cameraPermissionState: PermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    NavHost(navController = NavController, startDestination = "mainScreen") {
        composable("cameraScreen") {
            // Navigate to CameraScreen when needed
            CameraScreen(navController = navController)
        }
    }
    MainContent(
        hasPermission = cameraPermissionState.status.isGranted,
        onRequestPermission = cameraPermissionState::launchPermissionRequest
    )
}

@Composable
private fun MainContent(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    navController = NavController
) {
//    if (hasPermission) {
//        CameraScreen()
//    } else {
//        NoPermissionScreen(onRequestPermission)
//    }
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

@Preview
@Composable
private fun Preview_MainContent() {
    MainContent(
        hasPermission = true,
        onRequestPermission = {}
        navController = rememberNavController()
    )
}