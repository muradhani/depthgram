package org.craftaura.depthgram

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
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
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var inputStream: DataInputStream? by remember { mutableStateOf(null) }
    var outputStream: DataOutputStream? by remember { mutableStateOf(null) }
    var distance by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) {
        startImageReceiver(
            onImageReceived = { jpegBytes ->
                val img = ImageIO.read(ByteArrayInputStream(jpegBytes))
                imageState.value = img.toComposeImageBitmap()
            },
            onStreamReady = { out ->
                outputStream = out
            },
            onDistanceReceived = { dist ->
                distance = dist
                println("📏 Distance from phone: $dist meters")
            }
        )
    }

    imageState.value?.let { img ->
        ClickableImage(image = img) { x, y ->
            sendTouchCoordinates(x.toInt(), y.toInt(), outputStream!!)
            distance = receiveDistance(DataInputStream(inputStream))
        }
    }
}
fun startImageReceiver(
    onImageReceived: (ByteArray) -> Unit,
    onStreamReady: (DataOutputStream) -> Unit,
    onDistanceReceived: (Float) -> Unit
) {
    val executor = Executors.newSingleThreadExecutor()
    executor.submit {
        try {
            val server = ServerSocket(8080)
            println("📡 Listening on port 8080...")

            while (true) {
                val socket = server.accept()
                println("📥 Client connected: ${socket.inetAddress.hostAddress}")

                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                onStreamReady(output)

                while (!socket.isClosed) {
                    try {
                        val msgType = input.readInt()

                        if (msgType == 1) { // Image packet
                            val size = input.readInt()
                            val bytes = ByteArray(size)
                            input.readFully(bytes)

                            // Skip intrinsics for display, only JPEG needed
                            val jpegBytes = bytes.copyOfRange(24, bytes.size)
                            onImageReceived(jpegBytes)

                        } else if (msgType == 2) { // Distance reply
                            val distance = input.readFloat()
                            onDistanceReceived(distance)
                        }

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

fun sendTouchCoordinates(x: Int, y: Int, out: DataOutputStream) {
    out.writeInt(3) // Message type: touch coordinates
    out.writeInt(x)
    out.writeInt(y)
    out.flush()
}
fun receiveDistance(input: DataInputStream): Float {
    val msgType = input.readUTF()
    if (msgType == "DISTANCE") {
        return input.readFloat()
    }
    throw IllegalStateException("Unexpected message: $msgType")
}
@Composable
fun ClickableImage(
    image: ImageBitmap,
    onClick: (x: Float, y: Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onClick(offset.x, offset.y)
                }
            }
    ) {
        Image(
            bitmap =image,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
