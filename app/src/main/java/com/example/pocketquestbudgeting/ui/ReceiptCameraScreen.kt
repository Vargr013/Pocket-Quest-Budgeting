package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val Teal = Color(0xFF0F6B5C)
private val ZoomBg = Color(0xFF0F6B5C)
private val ShutterGray = Color(0xFFC4C4C4)
private val CornerBlack = Color(0xFF000000)
private val GalleryBorder = Color(0xFF2A2929)

@Composable
fun ReceiptScannerScreen() {
    var zoom by remember { mutableStateOf("1x") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
    ) {
        // ☰ = menu (corner only)
        Text(
            text = "☰",
            fontSize = 28.sp,
            color = Teal,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp),
        )

        // Optional page label (not in Figma text, but useful)
        Text(
            text = "Receipt Scanner",
            fontSize = 20.sp,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp),
        )

        // Viewfinder corner brackets
        ViewfinderCorners(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 15.dp, vertical = 80.dp),
        )

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Zoom: 1x / 2x / 4x
            Row(
                modifier = Modifier
                    .background(ZoomBg, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("1x", "2x", "4x").forEach { option ->
                    Text(
                        text = option,
                        fontSize = 12.sp,
                        color = TextPrimary,
                        modifier = Modifier.clickable { zoom = option },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                // Shutter button
                Box(
                    modifier = Modifier
                        .size(103.dp)
                        .align(Alignment.Center)
                        .border(2.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
                        .padding(10.dp)
                        .clip(CircleShape)
                        .background(ShutterGray)
                        .clickable { /* UI only */ },
                )

                // Gallery preview (bottom-right in Figma)
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .align(Alignment.CenterEnd)
                        .border(2.dp, GalleryBorder, CircleShape)
                        .clip(CircleShape)
                        .background(ShutterGray),
                )
            }
        }
    }
}

@Composable
private fun ViewfinderCorners(modifier: Modifier = Modifier) {
    val corner = 65.dp
    val stroke = 3.dp
    val radius = 20.dp

    Box(modifier = modifier) {
        CornerBracket(
            modifier = Modifier.align(Alignment.TopStart),
            size = corner,
            stroke = stroke,
            radius = radius,
            top = true,
            start = true,
        )
        CornerBracket(
            modifier = Modifier.align(Alignment.TopEnd),
            size = corner,
            stroke = stroke,
            radius = radius,
            top = true,
            start = false,
        )
        CornerBracket(
            modifier = Modifier.align(Alignment.BottomStart),
            size = corner,
            stroke = stroke,
            radius = radius,
            top = false,
            start = true,
        )
        CornerBracket(
            modifier = Modifier.align(Alignment.BottomEnd),
            size = corner,
            stroke = stroke,
            radius = radius,
            top = false,
            start = false,
        )
    }
}

@Composable
private fun CornerBracket(
    modifier: Modifier = Modifier,
    size: Dp,
    stroke: Dp,
    radius: Dp,
    top: Boolean,
    start: Boolean,
) {
    Box(
        modifier = modifier
            .size(size)
            .border(
                width = stroke,
                color = CornerBlack,
                shape = RoundedCornerShape(
                    topStart = if (top && start) radius else 0.dp,
                    topEnd = if (top && !start) radius else 0.dp,
                    bottomStart = if (!top && start) radius else 0.dp,
                    bottomEnd = if (!top && !start) radius else 0.dp,
                ),
            ),
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ReceiptScannerScreenPreview() {
    MaterialTheme {
        ReceiptScannerScreen()
    }
}