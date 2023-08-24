package com.example.myapplication.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.HandLandmarkerHelper
import com.google.mediapipe.tasks.vision.core.RunningMode

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel()
) {

//    val cameraState: CameraState by viewModel.state.collectAsStateWithLifecycle()

    CameraContent(
        viewModel = viewModel
//        onPhotoCaptured = viewModel::onPhotoCaptured
    )
//
//    cameraState.capturedImage?.let { capturedImage: Bitmap ->
//        CapturedImageBitmapDialog(
//            capturedImage = capturedImage,
//            onDismissRequest = viewModel::onCapturedPhotoConsumed
//        )
//    }
}

@Composable
private fun CapturedImageBitmapDialog(
    capturedImage: Bitmap,
    onDismissRequest: () -> Unit
) {

    val capturedImageBitmap: ImageBitmap = remember { capturedImage.asImageBitmap() }

    Dialog(
        onDismissRequest = onDismissRequest
    ) {
        Image(
            bitmap = capturedImageBitmap,
            contentDescription = "Captured photo"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
private fun CameraContent(
    viewModel: CameraViewModel
//    onPhotoCaptured: (Bitmap) -> Unit
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues: PaddingValues ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            factory = { context ->
                PreviewView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    setBackgroundColor(Color.BLACK)
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_START
                }.also { previewView ->
                    var camera = CameraControll(context, viewModel, lifecycleOwner, previewView.surfaceProvider)
                }
            }
        )
    }
}

@Preview
@Composable
private fun Preview_CameraContent() {
    CameraContent(
        viewModel = viewModel()
//        onPhotoCaptured = {}
    )
}