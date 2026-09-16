package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pocketquestbudgeting.R

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val Teal = Color(0xFF0F6B5C)
private val Coral = Color(0xFFE07A5F)
private val CardWhite = Color(0xFFFFFFFF)
private val PanelBg = Color(0xFFE8EEEC)
private val CircleBg = Color(0xFFC4C4C4)
private val CardBorder = Color(0xFF7D0000)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun WhatItWorthScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Text("Back", color = Teal)
            }
            Text(
                text = "What Its Worth",
                fontSize = 36.sp,
                lineHeight = 44.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 72.dp),
            )
        }

        Text(
            text = "See the value of what you’ve saved compared to real life items",
            fontSize = 12.sp,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircle(
                drawableRes = R.drawable.piggy_bank,
                size = 140.dp,
                iconSize = 90.dp,
            )
            Text("=", fontSize = 36.sp, color = TextPrimary)
            IconCircle(
                drawableRes = R.drawable.icon_coffee,
                size = 140.dp,
                iconSize = 90.dp,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "You stayed R500 below budget",
                fontSize = 20.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "= 12 Coffees",
                fontSize = 36.sp,
                color = Teal,
                textAlign = TextAlign.Center,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Box(modifier = Modifier.size(12.dp).background(Teal, CircleShape))
            Box(modifier = Modifier.size(12.dp).background(Coral, CircleShape))
            Box(modifier = Modifier.size(12.dp).background(Coral, CircleShape))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Try another comparison",
                    fontSize = 15.sp,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ComparisonCard(
                        drawableRes = R.drawable.icon_burger,
                        label = "2 Burgers\n= R160",
                    )
                    ComparisonCard(
                        drawableRes = R.drawable.icon_ticket,
                        label = "1 Movie\n= R175",
                    )
                }
            }
        }
    }
}

@Composable
private fun IconCircle(
    drawableRes: Int,
    size: Dp,
    iconSize: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(CircleBg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun ComparisonCard(
    drawableRes: Int,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(145.dp)
            .height(145.dp)
            .background(PanelBg, CardShape)
            .border(1.dp, CardBorder, CardShape)
            .padding(8.dp),
    ) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(72.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun WhatItWorthScreenPreview() {
    MaterialTheme {
        WhatItWorthScreen(onBack = {})
    }
}