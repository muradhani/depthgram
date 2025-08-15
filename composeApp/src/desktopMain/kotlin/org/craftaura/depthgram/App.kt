package org.craftaura.depthgram

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Composable
fun App() {
    val image by SocketManager.imageFlow.collectAsState()
    val distance by SocketManager.distanceFlow.collectAsState()

    LaunchedEffect(Unit) {
        SocketManager.startServer()
    }

    image?.let { img ->
        ClickableImage(image = img) { x, y ->
            CoroutineScope(Dispatchers.IO).launch { SocketManager.sendTouchCoordinates(x.toInt(), y.toInt()) }
        }
    }
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
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}