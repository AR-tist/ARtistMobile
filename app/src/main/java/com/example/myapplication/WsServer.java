package com.example.myapplication;

import android.util.Log;

import com.example.myapplication.ui.ServerStatus;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import kotlinx.serialization.json.Json;

public class WsServer extends WebSocketServer {

    private static final String TAG = "WsServer";

    public static final int ERROR_TYPE_NORMAL = 0;
    public static final int ERROR_TYPE_PORT_IN_USE = 1;
    public static final int ERROR_TYPE_SERVER_CLOSE_FAIL = 2;

    public WsServer(InetSocketAddress address) {
        super(address);
    }

    public static WsServer init(String host, int port) {
        return new WsServer(new InetSocketAddress(host, port));
    }

    public void stopWithException() {
        try {
            this.stop();
            running = false;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String connIp = conn.getRemoteSocketAddress().getAddress().toString().replace("/", "");
        connList.add(connIp);
        Log.d(TAG, "onOpen: // " + connIp + " //Opened connection number  " + connList.size());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.d(TAG, "onClose: ");
    }


    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        Log.d(TAG, "onMessage: buffer");
    }

    private boolean readyReceived = false;

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.d(TAG, "onMessage: " + message);
        if ("ready".equals(message)) {
            readyReceived = true;
            // 추가적인 동작 수행...
//            ServerStatus.value = ServerStatus.Running;

        }
    }

    public boolean isReadyReceived() {
        return readyReceived;
    }

    private boolean running = false;

    public boolean isRunning() {
        return running;
    }

    private List<String> connList = new ArrayList<>();

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.d(TAG, "onError: " + ex.getMessage());
        ex.printStackTrace();
        if (ex.getMessage() != null) {
            if (ex.getMessage().contains("Address already in use")) {
                Log.d(TAG, "ws server: 주소 사용중");
                return;
            }
        }
    }

    @Override
    public void onClosing(WebSocket conn, int code, String reason, boolean remote) {
        super.onClosing(conn, code, reason, remote);
        String connIp = conn.getRemoteSocketAddress().getAddress().toString().replace("/", "");
        for (String ip : connList) {
            if (ip.equals(connIp)) {
                connList.remove(ip);
                break;
            }
        }
        Log.d(TAG, "onClosing: // " + connIp + " //Opened connection number  " + connList.size());
    }

    @Override
    public void onStart() {
        running = true;
        Log.d(TAG, "onStart: ");
    }

}