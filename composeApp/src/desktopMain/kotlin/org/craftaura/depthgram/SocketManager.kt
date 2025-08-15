package org.craftaura.depthgram

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.Image
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import javax.imageio.ImageIO


object SocketManager {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    private val _imageFlow = MutableStateFlow<ImageBitmap?>(null)
    val imageFlow = _imageFlow.asStateFlow()

    private val _distanceFlow = MutableStateFlow<Float?>(null)
    val distanceFlow = _distanceFlow.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startServer(port: Int = 8080) {
        if (serverSocket != null) return // Already started

        scope.launch {
            try {
                runAdbReverse()
                serverSocket = ServerSocket(port)
                println("📡 Listening on port $port...")

                while (true) {
                    clientSocket = serverSocket!!.accept()
                    println("📥 Client connected: ${clientSocket!!.inetAddress.hostAddress}")

                    input = DataInputStream(clientSocket!!.getInputStream())
                    output = DataOutputStream(clientSocket!!.getOutputStream())

                    listenForMessages()
                }
            } catch (e: Exception) {
                println("❌ Server error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun listenForMessages() {
        withContext(Dispatchers.IO) {
            try {
                while (clientSocket?.isClosed == false) {
                    val msgType = input!!.readInt()

                    when (msgType) {
                        1 -> {
                            val size = input!!.readInt()
                            val bytes = ByteArray(size)
                            input!!.readFully(bytes)

                            val jpegBytes = bytes.copyOfRange(24, bytes.size)
                            val img = ImageIO.read(ByteArrayInputStream(jpegBytes))
                            img?.let { _imageFlow.value = it.toComposeImageBitmap() }
                        }
                        2 -> {
                            val distance = input!!.readFloat()
                            _distanceFlow.value = distance
                            println("Distance from phone: $distance meters")
                        }
                        else -> {
                            println("Unknown message type: $msgType")
                        }
                    }
                }
            } finally {
                reconnect()
            }
        }
    }

    fun sendTouchCoordinates(x: Int, y: Int) {
        try {
            output?.apply {
                writeInt(3)
                writeInt(8)
                writeInt(x)
                writeInt(y)
                flush()
            }
        } catch (e: Exception) {
            println("Failed to send touch coordinates: ${e.message}")
        }
    }

    private fun runAdbReverse() {
        try {
            val process = ProcessBuilder("adb", "reverse", "tcp:8080", "tcp:8080")
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use {
                it.lines().forEach { line -> println("ADB: $line") }
            }
            process.waitFor()
            println("adb reverse set up")
        } catch (e: Exception) {
            println("Failed to run adb reverse: ${e.message}")
        }
    }
    private fun reconnect() {
        println("🔄 Reconnecting...")
        clientSocket?.close()
        input = null
        output = null
        startServer()
    }
}


