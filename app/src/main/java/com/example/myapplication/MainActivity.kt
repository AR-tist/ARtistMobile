package com.example.myapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.ConnectedScreen
import com.example.myapplication.ui.*
import com.example.myapplication.ui.theme.MyApplicationTheme


import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.myapplication.ui.camera.CameraScreen

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
class MainActivity : ComponentActivity() {
    private lateinit var serverManager: WebSocketServerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        serverManager = WebSocketServerManager("0.0.0.0", 4439)
        // IP와 포트 설정

        setContent {
            MyApplicationTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(serverManager = serverManager)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("짜증나", "웹 연결 해체")
        serverManager.stopServer()

    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Composable
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
fun AppNavigation(serverManager: WebSocketServerManager) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main_screen") {
        composable("main_screen") { MainScreen(serverManager = serverManager, navController) }
        composable("connected_screen") { ConnectedScreen(webSocketServerManager = serverManager) }
        composable("cameraScreen") {
            CameraScreen(serverManager = serverManager, navController = navController)
        }
        // 기타 목적지 정의
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}