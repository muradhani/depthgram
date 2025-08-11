package org.craftaura.depthgram

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import java.io.DataInputStream
import java.io.File
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import javax.imageio.ImageIO


@Composable
fun App() {
    val imageState = remember { mutableStateOf<ImageBitmap?>(null) }
    runAdbReverse()
    LaunchedEffect(Unit) {
        startImageReceiver { bytes ->
            val img = Image.makeFromEncoded(bytes)
            imageState.value = img.toComposeImageBitmap()
        }
    }

    imageState.value?.let { image ->
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}
fun startImageReceiver(onImageReceived: (ByteArray) -> Unit) {
    val executor = Executors.newSingleThreadExecutor()
    executor.submit {
        try {
            val server = ServerSocket(8080)
            println("📡 Listening on port 8080...")

            while (true) {
                val socket = server.accept()
                println("📥 Client connected: ${socket.inetAddress.hostAddress}")
                val input = DataInputStream(socket.getInputStream())

                while (!socket.isClosed) {
                    try {
                        val size = input.readInt()
                        val bytes = ByteArray(size)
                        input.readFully(bytes)
                        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                        val fx = buffer.float
                        val fy = buffer.float
                        val cx = buffer.float
                        val cy = buffer.float
                        val width = buffer.int
                        val height = buffer.int
                        val jpegBytes = bytes.copyOfRange(24, bytes.size)
                        onImageReceived(bytes)
                        println("✅ Image received: JPEG=${jpegBytes.size} bytes, Intrinsics=fx:$fx fy:$fy cx:$cx cy:$cy w:$width h:$height")

                    } catch (e: Exception) {
                        println("⚠️ Connection error: ${e.message}")
                        socket.close()
                        break
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ Server error: ${e.message}")
            e.printStackTrace()
        }
    }
}

fun runAdbReverse() {
    try {
        val process = ProcessBuilder("adb", "reverse", "tcp:8080", "tcp:8080")
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use {
            it.lines().forEach { line -> println("ADB: $line") }
        }
        process.waitFor()
        println("✅ adb reverse set up")
    } catch (e: Exception) {
        println("❌ Failed to run adb reverse: ${e.message}")
    }
}