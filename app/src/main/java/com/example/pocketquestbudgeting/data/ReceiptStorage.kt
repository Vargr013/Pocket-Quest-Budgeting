package com.example.pocketquestbudgeting.data

import android.content.Context
import android.net.Uri
import java.io.File
fun copyReceiptToAppStorage(context: Context, source: Uri): String? {
    val dir = File(context.filesDir, "receipts")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    val dest = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
    val input = context.contentResolver.openInputStream(source) ?: return null
    input.use { inStream ->
        dest.outputStream().use { outStream ->
            inStream.copyTo(outStream)
        }
    }
    return dest.absolutePath
}