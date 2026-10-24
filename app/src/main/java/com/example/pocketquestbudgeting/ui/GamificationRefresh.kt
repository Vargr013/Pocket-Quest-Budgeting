package com.example.pocketquestbudgeting.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.InvalidationTracker
import com.example.pocketquestbudgeting.data.DatabaseProvider
import com.example.pocketquestbudgeting.data.todayIsoDate
import kotlinx.coroutines.delay

@Composable
internal fun gamificationReload(userId: Long): Int {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val inspecting = LocalInspectionMode.current
    var reload by remember(userId) { mutableIntStateOf(0) }
    if (!inspecting) {
        val db = remember(context) { DatabaseProvider.get(context) }
        DisposableEffect(db, userId, lifecycle) {
            val observer = object : InvalidationTracker.Observer("expenses", "budget_goals", "categories", "activity_days", "earned_achievements", "custom_worth_items") {
                override fun onInvalidated(tables: Set<String>) {
                    // I posted back to the screen; Room can notify from its transaction thread.
                    android.os.Handler(android.os.Looper.getMainLooper()).post { reload++ }
                }
            }
            val resume = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) reload++ }
            db.invalidationTracker.addObserver(observer)
            lifecycle.addObserver(resume)
            onDispose { db.invalidationTracker.removeObserver(observer); lifecycle.removeObserver(resume) }
        }
        LaunchedEffect(userId) {
            var date = todayIsoDate()
            while (true) {
                delay(60_000)
                val today = todayIsoDate()
                if (today != date) { date = today; reload++ }
            }
        }
    }
    return reload
}
