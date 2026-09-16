package com.example.pocketquestbudgeting.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.pocketquestbudgeting.data.copyReceiptToAppStorage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ReceiptPhotoActions(
    userId: Long,
    expenseId: Long?,
    enabled: Boolean,
    onReceipt: (String) -> Unit,
    onError: (String?) -> Unit,
    onPending: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var capturePath by rememberSaveable(userId, expenseId) { mutableStateOf<String?>(null) }
    var choosing by rememberSaveable(userId, expenseId) { mutableStateOf(false) }
    // I saved the returned URI too so recreation can restart an interrupted import.
    var importUri by rememberSaveable(userId, expenseId) { mutableStateOf<String?>(null) }
    var active by remember { mutableStateOf(true) }
    val pending = capturePath != null || choosing || importUri != null
    DisposableEffect(Unit) {
        active = true
        onDispose { active = false }
    }
    LaunchedEffect(pending) { onPending(pending) }

    fun clearCapture() {
        // I only removed the temporary output owned by this draft.
        capturePath?.let { path ->
            runCatching {
                val file = File(path)
                val directory = File(context.cacheDir, "receipt-captures").canonicalFile
                if (file.canonicalFile.parentFile == directory) file.delete()
            }
        }
        capturePath = null
    }

    LaunchedEffect(importUri) {
        val source = importUri ?: return@LaunchedEffect
        val pendingFile = capturePath
        try {
            val saved = withContext(Dispatchers.IO) {
                if (pendingFile != null) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(pendingFile, options)
                    check(options.outWidth > 0 && options.outHeight > 0)
                    // I checked a small decoded image without loading the full camera photo.
                    options.inSampleSize = 1
                    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1024) {
                        options.inSampleSize *= 2
                    }
                    options.inJustDecodeBounds = false
                    requireNotNull(BitmapFactory.decodeFile(pendingFile, options)).recycle()
                }
                requireNotNull(copyReceiptToAppStorage(context, Uri.parse(source)))
            }
            if (active && enabled) {
                onReceipt(saved)
                onError(null)
            }
            clearCapture()
            importUri = null
        } catch (cancelled: CancellationException) {
            // I kept the source for a restored draft to retry its import.
            throw cancelled
        } catch (_: Exception) {
            clearCapture()
            importUri = null
            if (active) onError("Could not attach the photo. Your previous receipt is unchanged. Please retry.")
        }
    }

    // Capture contract: https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.TakePicture
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = capturePath
        if (active && enabled && success && path != null) {
            importUri = Uri.fromFile(File(path)).toString()
        } else {
            clearCapture()
        }
    }
    // I kept the existing system photo picker alongside capture (Google, 2026f).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        choosing = false
        if (active && enabled && uri != null) importUri = uri.toString()
    }

    TextButton(
        enabled = enabled && !pending,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            choosing = true
            onPending(true)
            try {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (_: Exception) {
                choosing = false
                onPending(false)
                onError("Could not open the photo picker. Please try again.")
            }
        },
    ) { Text("Choose existing photo") }
    TextButton(
        enabled = enabled && !pending,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            try {
                val directory = File(context.cacheDir, "receipt-captures")
                check(directory.isDirectory || directory.mkdirs())
                val file = File.createTempFile("capture_", ".jpg", directory)
                capturePath = file.absolutePath
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.receipt-capture", file)
                onError(null)
                onPending(true)
                camera.launch(uri)
            } catch (_: Exception) {
                clearCapture()
                onPending(false)
                onError("Could not open the camera. Check that a camera app is available, or choose an existing photo.")
            }
        },
    ) { Text("Take photo") }
    if (pending) Text("Waiting for the photo...")
}
