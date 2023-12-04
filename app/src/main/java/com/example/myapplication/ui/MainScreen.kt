package com.example.myapplication.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.camera.CameraScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

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
        Button(onClick = { navController.navigate("mainScreen") }) {
            Text("QRcode ", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            // Request camera permission
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }) {
            Text("손 인식 기능 시작", fontSize = 18.sp)
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
