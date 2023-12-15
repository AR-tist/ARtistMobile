package com.example.myapplication

import android.util.Log
import kotlinx.coroutines.*
import java.nio.ByteBuffer

class WebSocketServerManager(private val ipAddress: String, private val port: Int) {
    private var server: WsServer? = null

    fun startServer() {
        CoroutineScope(Dispatchers.IO).launch {
            server = WsServer.init(ipAddress, port)
            server?.start() ?: println("wsServer is null")

        }
    }

    fun stopServer() {
        server?.stop(0)
        Log.d("wsServer","연결 끊기")
    }

    fun broadcast(message: String) {
        // 모든 연결된 클라이언트에게 메시지를 브로드캐스트합니다.
        server?.broadcast(message)
    }

    fun isReadyReceived(): Boolean {
        Log.d("WSMANAGER", "${server?.isReadyReceived()}")
        return server?.isReadyReceived ?: false
    }

    fun isRunning(): Boolean {
        return server?.isRunning ?: false
    }


}
