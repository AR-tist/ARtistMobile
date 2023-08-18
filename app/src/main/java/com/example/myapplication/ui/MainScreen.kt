package com.example.myapplication.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.myapplication.ui.camera.CameraScreen
import com.example.myapplication.ui.no_permission.NoPermissionScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted

import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen() {

    val cameraPermissionState: PermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    MainContent(
        hasPermission = cameraPermissionState.status.isGranted,
        onRequestPermission = cameraPermissionState::launchPermissionRequest
    )
}

@Composable
private fun MainContent(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {

    if (hasPermission) {
        CameraScreen()
    } else {
        NoPermissionScreen(onRequestPermission)
    }
}

@Preview
@Composable
private fun Preview_MainContent() {
    MainContent(
        hasPermission = true,
        onRequestPermission = {}
    )
}