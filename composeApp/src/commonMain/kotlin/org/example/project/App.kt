package org.example.project

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App() {
    var isZajacMoving by remember { mutableStateOf(true)}
    var isZajacGivingPosition by remember { mutableStateOf(false)}
    val mysliwySteps = remember { mutableStateListOf(Offset(0f,0f)) }
    val zajacSteps = remember { mutableStateListOf(Offset(0f,0f)) }
    val zajacGivenSteps = remember { mutableStateListOf<Offset>() }
    var hoverPreview by remember { mutableStateOf<Offset?>(null) }
    val circleRadius = 1

    val circles = listOf(if(isZajacMoving) {
        zajacSteps.last()
    } else {
        mysliwySteps.last()
    })
    var cameraPosition by remember { mutableStateOf(Offset(0.0f,0.0f)) }
    var cameraZoom by remember { mutableStateOf(1f) }
    var visibleWidth = 8/cameraZoom

    var viewPortSizeInPixels by remember { mutableStateOf(IntSize(1,1))  }

    val aspectRatio = viewPortSizeInPixels.width.toFloat() / viewPortSizeInPixels.height.toFloat()
    println(aspectRatio)
    val visibleHeight = visibleWidth / aspectRatio

    // Transform and filter
    val transformedZajacPoints = getTransformedPoints(zajacSteps, cameraPosition, visibleWidth, visibleHeight)
    val transformedZajacGivenPoints = getTransformedPoints(zajacGivenSteps, cameraPosition, visibleWidth, visibleHeight)
    val transformedMysliwyPoints = getTransformedPoints(mysliwySteps, cameraPosition, visibleWidth, visibleHeight)
    val transformedCircles = getTransformedPoints(circles, cameraPosition, visibleWidth, visibleHeight)
    val hoverPreviewTransformed = hoverPreview?.let { getTransformedPoint(it, cameraPosition, visibleWidth, visibleHeight) }
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned {
                    viewPortSizeInPixels = it.size
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid: Offset, pan: Offset, zoom: Float, rotation: Float ->
                        cameraZoom = cameraZoom * zoom
                        cameraPosition = cameraPosition - pan * visibleWidth / viewPortSizeInPixels.width.toFloat()
                    }
                }
                .pointerInput("scroll") {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val scrollDelta = event.changes.firstOrNull()?.scrollDelta

                            if (scrollDelta != null ) {
                                val zoomFactor = 1.0 - scrollDelta.y * 0.01
                                // Apply zoomFactor to your scale
                                cameraZoom = cameraZoom * zoomFactor.toFloat()
                            }
                        }
                    }
                }
                .pointerInput(circles.last()) {
                    detectTapGestures { tap ->
                        // Convert tap (screen) coordinates to normalized 0..1
                        val world = trnasnsformScreenPositionToWorldPosition(
                            tap,
                            viewPortSizeInPixels,
                            cameraPosition,
                            visibleWidth,
                            visibleHeight
                        )
                        val result = snapWorldPosition(circles, world, isZajacGivingPosition)
                        result?:return@detectTapGestures

                        if(isZajacGivingPosition) {
                            isZajacGivingPosition = false
                            isZajacMoving = false
                            zajacGivenSteps.add(result)
                        } else {
                            if(isZajacMoving) {
                                zajacSteps.add(result)
                                isZajacGivingPosition = true
                            } else {
                                mysliwySteps.add(result)
                                isZajacMoving = true
                            }
                        }
                    }
                }
                .onPointerEvent(PointerEventType.Move) { event ->
                    val position = event.changes.first().position
                    val world = trnasnsformScreenPositionToWorldPosition(
                        position,
                        viewPortSizeInPixels,
                        cameraPosition,
                        visibleWidth,
                        visibleHeight
                    )

                    hoverPreview = snapWorldPosition(circles, world, isZajacGivingPosition)
                }
                .onPointerEvent(PointerEventType.Exit) {
                    hoverPreview = null
                }
                .drawBehind {
                    val widthF = size.width
                    val heightF = size.height

                    fun Offset.toPixelOffset(): Offset = Offset(x * widthF, y * heightF)

                    // Helper to draw lines between a list of points
                    fun drawPath(points: List<Offset>, color: Color) {
                        for (i in 0 until points.size - 1) {
                            val start = points[i].toPixelOffset()
                            val end = points[i + 1].toPixelOffset()
                            drawLine(color = color, start = start, end = end, strokeWidth = 2f)
                        }
                    }

                    if(isZajacMoving || isZajacGivingPosition) {
                        drawPath(transformedZajacPoints, Color.Red)
                    }
                    drawPath(transformedMysliwyPoints, Color.Blue)
                }
        ) {
            val boxSize = with(LocalDensity.current) { 10.dp.toPx() }
            val isMysliwyMoving = !isZajacMoving && !isZajacGivingPosition
            if(!isMysliwyMoving) {
                transformedZajacPoints.forEach { (x, y) ->
                    drawPoint(x, viewPortSizeInPixels, boxSize, y, Color.Red)
                }
            }
            transformedZajacGivenPoints.takeLast(1).forEach { (x, y) ->
                drawPoint(x, viewPortSizeInPixels, boxSize, y, Color.Gray)
            }
            transformedMysliwyPoints.forEach { (x, y) ->
                drawPoint(x, viewPortSizeInPixels, boxSize, y, Color.Blue)
            }


            val circleSize = 2* circleRadius * viewPortSizeInPixels.width / visibleWidth
            val circleSizeDp = with(LocalDensity.current) {
                circleSize.toDp()
            }
            transformedCircles.forEach { (x, y) ->
                Box(Modifier.size(circleSizeDp).graphicsLayer {
                    translationX = x * viewPortSizeInPixels.width - circleSize / 2
                    translationY = y * viewPortSizeInPixels.height - circleSize / 2
                }.border(1.dp, Color.Red, RoundedCornerShape(circleSizeDp/2)))
            }

            hoverPreviewTransformed?.let { (x, y) ->
                drawPoint(x, viewPortSizeInPixels, boxSize, y, Color.Green)
            }

            Text(
                "currentDistance: ${(zajacSteps.last()-mysliwySteps.last()).getDistance()}\n"+
                        "steps = ${zajacSteps.size}\n" +
                        "state = ${if(isMysliwyMoving) "Ruch myśliwego" else if(isZajacGivingPosition) "Ząjac wybiera punkt do pokazania" else "Ruch zająca"}"
            )
        }
    }
}

private fun snapWorldPosition(
    circles: List<Offset>,
    world: Offset,
    insideOk: Boolean,
): Offset? {
    val center = circles.last()

    val direction = world - center
    if(insideOk && direction.getDistance() < 1) {
        return world
    }

    if (direction.getDistance() > 0f) {
        val normalized = direction / direction.getDistance()
        return center + normalized
    } else {
        return null
    }

}

private fun trnasnsformScreenPositionToWorldPosition(
    position: Offset,
    viewPortSizeInPixels: IntSize,
    cameraPosition: Offset,
    visibleWidth: Float,
    visibleHeight: Float
): Offset {
    val normX = position.x / viewPortSizeInPixels.width.toFloat()
    val normY = position.y / viewPortSizeInPixels.height.toFloat()

    val worldX = cameraPosition.x + (normX - 0.5f) * visibleWidth
    val worldY = cameraPosition.y + (normY - 0.5f) * visibleHeight
    val world = Offset(worldX, worldY)
    return world
}

@Composable
private fun drawPoint(
    x: Float,
    viewPortSizeInPixels: IntSize,
    boxSize: Float,
    y: Float,
    color: Color,
) {
    Box(Modifier.size(10.dp).graphicsLayer {
        translationX = x * viewPortSizeInPixels.width - boxSize / 2
        translationY = y * viewPortSizeInPixels.height - boxSize / 2
    }.border(1.dp, color, RoundedCornerShape(boxSize / 2)))
}

private fun getTransformedPoints(
    points: List<Offset>,
    cameraPosition: Offset,
    visibleWidth: Float,
    visibleHeight: Float
): List<Offset> = points.mapNotNull { point ->
    val normalized = normalizePoint(point, cameraPosition, visibleWidth, visibleHeight)
    if (normalized.x in 0.0..1.0 && normalized.y in 0.0..1.0) {
        normalized
    } else {
        null // Filter out points outside visible area
    }
}

private fun getTransformedPoint(
    point: Offset,
    cameraPosition: Offset,
    visibleWidth: Float,
    visibleHeight: Float
): Offset? {
    val normalized = normalizePoint(point, cameraPosition, visibleWidth, visibleHeight)
    return if (normalized.x in 0.0..1.0 && normalized.y in 0.0..1.0) {
        normalized
    } else {
        null // Filter out points outside visible area
    }
}

// Convert world point to normalized space (0..1 range)
private fun normalizePoint(point: Offset, camPos: Offset, width: Float, height: Float): Offset {
    val xNorm = (point.x - camPos.x + width / 2) / width
    val yNorm = (point.y - camPos.y + height / 2) / height
    return Offset(xNorm, yNorm)
}