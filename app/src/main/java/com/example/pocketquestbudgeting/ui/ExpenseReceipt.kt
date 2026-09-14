package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pocketquestbudgeting.R
import java.io.File

@Composable
internal fun ExpenseReceipt(path: String?, enabled: Boolean = true) {
    var showing by remember(path) { mutableStateOf(false) }
    var failed by remember(path) { mutableStateOf(false) }

    if (path.isNullOrBlank()) {
        Text("No receipt")
        return
    }

    Column(modifier = Modifier.widthIn(max = 144.dp)) {
        if (failed) {
            Text("Receipt unavailable. The saved file may be missing or unreadable.")
        } else {
            AsyncImage(
                model = File(path),
                contentDescription = stringResource(R.string.receipt_photo),
                onError = { failed = true },
                modifier = Modifier.size(56.dp).clickable(enabled = enabled) { showing = true },
            )
            TextButton(onClick = { showing = true }, enabled = enabled) { Text("View receipt") }
        }
    }
    if (showing) {
        AlertDialog(
            onDismissRequest = { showing = false },
            confirmButton = {
                TextButton(onClick = { showing = false }) {
                    Text(stringResource(R.string.close))
                }
            },
            text = {
                if (failed) {
                    Text("Receipt unavailable. The saved file may be missing or unreadable.")
                } else {
                    // I kept the saved-file popup and showed a message if image loading failed.
                    AsyncImage(
                        model = File(path),
                        contentDescription = stringResource(R.string.receipt_photo),
                        onError = { failed = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
}
