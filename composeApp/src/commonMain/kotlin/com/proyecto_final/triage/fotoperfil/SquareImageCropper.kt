package com.proyecto_final.triage.fotoperfil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_ZOOM = 5f
private const val OUTPUT_SIZE_PX = 512
private val CROP_PADDING = 24.dp

/**
 * Pantalla completa para encuadrar [image] dentro de un cuadrado.
 * Se pellizca para acercar/alejar y se arrastra para mover. La imagen siempre
 * cubre todo el cuadrado, así el recorte nunca tiene bordes vacíos.
 *
 * [onCropped] recibe la imagen recortada, cuadrada, de hasta 512x512 px.
 */
@Composable
fun SquareImageCropper(
    image: ImageBitmap,
    onCancel: () -> Unit,
    onCropped: (ImageBitmap) -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        val paddingPx = with(LocalDensity.current) { CROP_PADDING.toPx() }
        var zoom by remember(image) { mutableStateOf(1f) }
        var offset by remember(image) { mutableStateOf(Offset.Zero) }
        var viewport by remember { mutableStateOf(IntSize.Zero) }

        fun geometry(): CropGeometry? =
            if (viewport.width == 0 || viewport.height == 0) null
            else CropGeometry(image, viewport.width.toFloat(), viewport.height.toFloat(), paddingPx)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()
        ) {
            Text(
                text = "Encuadrá tu foto",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )
            Text(
                text = "Pellizcá para acercar y arrastrá para mover la imagen.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Sin esto la imagen ampliada se dibuja por encima del título y los botones.
                    .clipToBounds()
                    .onSizeChanged { viewport = it }
                    .pointerInput(image) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val g = geometry() ?: return@detectTransformGestures
                            val newZoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                            // Escalar el desplazamiento mantiene fijo el punto central al hacer zoom.
                            val scaledOffset = offset * (newZoom / zoom)
                            offset = g.clampOffset(scaledOffset + pan, newZoom)
                            zoom = newZoom
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val g = CropGeometry(image, size.width, size.height, paddingPx)
                    val scale = g.scale(zoom)
                    val drawnWidth = image.width * scale
                    val drawnHeight = image.height * scale
                    val left = size.width / 2f + offset.x - drawnWidth / 2f
                    val top = size.height / 2f + offset.y - drawnHeight / 2f

                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(drawnWidth.roundToInt(), drawnHeight.roundToInt()),
                        filterQuality = FilterQuality.Medium
                    )

                    // Oscurece todo lo que queda fuera del cuadrado de recorte.
                    val side = g.side
                    val cropLeft = (size.width - side) / 2f
                    val cropTop = (size.height - side) / 2f
                    val dim = Color.Black.copy(alpha = 0.6f)
                    drawRect(dim, Offset.Zero, Size(size.width, cropTop))
                    drawRect(dim, Offset(0f, cropTop + side), Size(size.width, size.height - cropTop - side))
                    drawRect(dim, Offset(0f, cropTop), Size(cropLeft, side))
                    drawRect(dim, Offset(cropLeft + side, cropTop), Size(size.width - cropLeft - side, side))

                    drawRect(
                        color = Color.White,
                        topLeft = Offset(cropLeft, cropTop),
                        size = Size(side, side),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    // Guía de cómo se verá en el círculo del perfil.
                    drawCircle(
                        color = Color.White.copy(alpha = 0.5f),
                        radius = side / 2f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar", color = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        val g = geometry() ?: return@Button
                        onCropped(cropSquare(image, g, offset, zoom))
                    },
                    enabled = viewport != IntSize.Zero,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Usar foto", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * Relación entre la imagen y el área visible. Con zoom = 1 la imagen cubre justo el
 * cuadrado de recorte; [offset] es el corrimiento del centro de la imagen respecto
 * del centro del área visible.
 */
private class CropGeometry(
    private val image: ImageBitmap,
    viewWidth: Float,
    viewHeight: Float,
    padding: Float
) {
    val side: Float = (min(viewWidth, viewHeight) - 2 * padding).coerceAtLeast(1f)
    private val baseScale: Float = max(side / image.width, side / image.height)

    fun scale(zoom: Float): Float = baseScale * zoom

    fun clampOffset(offset: Offset, zoom: Float): Offset {
        val s = scale(zoom)
        val maxX = ((image.width * s - side) / 2f).coerceAtLeast(0f)
        val maxY = ((image.height * s - side) / 2f).coerceAtLeast(0f)
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }
}

private fun cropSquare(image: ImageBitmap, g: CropGeometry, offset: Offset, zoom: Float): ImageBitmap {
    val scale = g.scale(zoom)
    val sourceSide = (g.side / scale).roundToInt().coerceIn(1, min(image.width, image.height))
    val sourceLeft = (image.width / 2f - offset.x / scale - sourceSide / 2f)
        .roundToInt().coerceIn(0, image.width - sourceSide)
    val sourceTop = (image.height / 2f - offset.y / scale - sourceSide / 2f)
        .roundToInt().coerceIn(0, image.height - sourceSide)

    val outputSide = min(OUTPUT_SIZE_PX, sourceSide)
    val output = ImageBitmap(outputSide, outputSide)
    Canvas(output).drawImageRect(
        image = image,
        srcOffset = IntOffset(sourceLeft, sourceTop),
        srcSize = IntSize(sourceSide, sourceSide),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(outputSide, outputSide),
        paint = Paint().apply { filterQuality = FilterQuality.High }
    )
    return output
}
