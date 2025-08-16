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
    private var imageServer: ServerSocket? = null
    private var controlServer: ServerSocket? = null
    private var imageClient: Socket? = null

    private var controlClient: Socket? = null
    private var imageInput: DataInputStream? = null
    private var imageOutput: DataOutputStream? = null

    private var controlInput: DataInputStream? = null

    private var controlOutput: DataOutputStream? = null
    private val _imageFlow = MutableStateFlow<ImageBitmap?>(null)
    val imageFlow = _imageFlow.asStateFlow()

    private val _distanceFlow = MutableStateFlow<Float?>(null)
    val distanceFlow = _distanceFlow.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startServers() {
        val scope = CoroutineScope(Dispatchers.IO)

        // Start image server
        scope.launch {
            try {
                imageServer = ServerSocket(8080)
                println("📡 Waiting for image client...")
                imageClient = imageServer!!.accept()
                println("✅ Image client connected")
                imageInput = DataInputStream(imageClient!!.getInputStream())
                listenForMessages()
            } catch (e: Exception) {
                println("❌ Image server error: ${e.message}")
            }
        }

        // Start control server
        scope.launch {
            try {
                controlServer = ServerSocket(8081)
                println("📡 Waiting for control client...")
                controlClient = controlServer!!.accept()
                println("✅ Control client connected")
                controlInput = DataInputStream(controlClient!!.getInputStream())
                controlOutput = DataOutputStream(controlClient!!.getOutputStream())
                listenForDistance()
            } catch (e: Exception) {
                println("❌ Control server error: ${e.message}")
            }
        }
    }


    private suspend fun listenForMessages() = withContext(Dispatchers.IO) {
        try {
            while (imageClient?.isClosed == false) {
                val msgType = imageInput!!.readInt()
                when (msgType) {
                    1 -> {
                        val size = imageInput!!.readInt()
                        val bytes = ByteArray(size)
                        imageInput!!.readFully(bytes)
                        val jpegBytes = bytes.copyOfRange(24, bytes.size)
                        ImageIO.read(ByteArrayInputStream(jpegBytes))?.let {
                            _imageFlow.value = it.toComposeImageBitmap()
                        }
                    }
                    2 -> {
                        val distance = imageInput!!.readFloat()
                        _distanceFlow.value = distance
                        println("📏 Distance from phone: $distance m")
                    }
                    else -> println("⚠ Unknown message type: $msgType")
                }
            }
        } catch (e: Exception) {
            println("⚠ Connection lost: ${e.message}")
        } finally {
            reconnect()
        }
    }
    private suspend fun listenForDistance() = withContext(Dispatchers.IO) {
        try {
            while (controlClient?.isClosed == false) {
                val distance = controlInput!!.readFloat()
                _distanceFlow.value = distance
                println("📏 Distance: $distance m")
            }
        } catch (e: Exception) {
            println("⚠ Control stream lost: ${e.message}")
        }
    }

    suspend fun sendTouchCoordinates(x: Int, y: Int) {
        try {
            controlOutput?.apply {
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
            ProcessBuilder("adb", "reverse", "tcp:8080", "tcp:8080")
                .redirectErrorStream(true)
                .start()
                .waitFor()

            ProcessBuilder("adb", "reverse", "tcp:8081", "tcp:8081")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        } catch (e: Exception) {
            println("Failed to run adb reverse: ${e.message}")
        }
    }
    private fun reconnect() {
        println("🔄 Reconnecting...")
        imageClient?.close()
        imageInput = null
        imageOutput = null
        startServers()
    }
}


