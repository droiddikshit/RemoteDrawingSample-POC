package com.example.myapplication123.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

data class DrawingPath(
    val path: Path,
    val points: List<Offset> = emptyList(), // Store points for serialization
    val color: Color = Color.Black,
    val strokeWidth: Float = 5f
)

@Composable
fun DrawingCanvas(
    modifier: Modifier = Modifier,
    isDrawable: Boolean = true,
    paths: List<DrawingPath> = emptyList(),
    onPathDrawn: (DrawingPath) -> Unit = {},
    onClear: () -> Unit = {}
) {
    val drawingPaths = remember { mutableStateOf(paths.toMutableList()) }
    
    // Sync external paths when they change
    LaunchedEffect(paths) {
        if (!isDrawable) {
            // Student mode: always sync from external paths (from server)
            drawingPaths.value = paths.toMutableList()
        } else if (paths.isEmpty() && drawingPaths.value.isNotEmpty()) {
            // Teacher mode: clear when external paths become empty
            drawingPaths.value = mutableListOf()
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(isDrawable) {
                if (!isDrawable) return@pointerInput

                var currentPath = Path()
                var currentPoints = mutableListOf<Offset>()
                var lastOffset: Offset? = null

                detectDragGestures(
                    onDragStart = { offset ->
                        currentPath = Path()
                        currentPoints = mutableListOf()
                        currentPath.moveTo(offset.x, offset.y)
                        currentPoints.add(offset)
                        lastOffset = offset
                    },
                    onDrag = { change, dragAmount ->
                        val currentOffset = change.position
                        lastOffset?.let { last ->
                            // Draw smooth lines
                            val distance = abs(currentOffset.x - last.x) + abs(currentOffset.y - last.y)
                            if (distance > 2) { // Only add points with some distance
                                val midX = (currentOffset.x + last.x) / 2
                                val midY = (currentOffset.y + last.y) / 2
                                currentPath.quadraticBezierTo(
                                    last.x, last.y,
                                    midX, midY
                                )
                                currentPoints.add(Offset(midX, midY))
                                currentPoints.add(currentOffset)
                                lastOffset = currentOffset
                            }
                        }
                    },
                    onDragEnd = {
                        lastOffset?.let {
                            currentPath.lineTo(it.x, it.y)
                            if (currentPoints.isEmpty() || currentPoints.last() != it) {
                                currentPoints.add(it)
                            }
                        }
                        // Only create path if we have points
                        if (currentPoints.isNotEmpty()) {
                            val newPath = DrawingPath(
                                path = currentPath,
                                points = currentPoints.toList(),
                                color = Color.Black,
                                strokeWidth = 5f
                            )
                            drawingPaths.value.add(newPath)
                            onPathDrawn(newPath)
                        }
                    }
                )
            }
    ) {
        // Draw white background first
        drawRect(Color.White)
        
        // Draw all paths
        drawingPaths.value.forEach { drawingPath ->
            drawPath(
                path = drawingPath.path,
                color = drawingPath.color,
                style = Stroke(
                    width = drawingPath.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

