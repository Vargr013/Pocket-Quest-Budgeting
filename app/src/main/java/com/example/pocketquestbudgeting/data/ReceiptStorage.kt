package com.example.pocketquestbudgeting.data

import android.content.Context
import android.net.Uri
import java.io.File
fun copyReceiptToAppStorage(context: Context, source: Uri): String? {
    // I kept a local receipt copy to avoid needing gallery access later.
    val dir = File(context.filesDir, "receipts")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    // I copied to a new file to keep the original receipt safe until saving.
    val dest = File.createTempFile("receipt_", ".jpg", dir)
    try {
        val input = context.contentResolver.openInputStream(source) ?: return null
        input.use { inStream ->
            dest.outputStream().use { outStream ->
                inStream.copyTo(outStream)
            }
        }
        return dest.absolutePath.takeIf { dest.length() > 0L }
    } catch (failure: Exception) {
        dest.delete()
        throw failure
    } finally {
        if (dest.length() == 0L) dest.delete()
    }
}
