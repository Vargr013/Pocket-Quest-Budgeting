package com.example.pocketquestbudgeting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.pocketquestbudgeting.ui.PocketQuestNavigation
import com.example.pocketquestbudgeting.ui.theme.PocketQuestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )

        setContent {
            PocketQuestTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PocketQuestNavigation(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                    )
                }
            }
        }
    }
}
