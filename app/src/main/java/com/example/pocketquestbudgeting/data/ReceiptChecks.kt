package com.example.pocketquestbudgeting.data

import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

internal data class ReceiptInfo(val sha256: String, val bytes: Long, val mime: String)

internal object ReceiptChecks {
    const val MAX_BYTES = 10L * 1024 * 1024
    private val uuid = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")

    fun pathJob(uid: String, expense: String, path: String): String {
        val id = path.substringAfterLast('/')
        require(uuid.matches(expense) && uuid.matches(id) && path == receiptObjectPath(uid, expense, id)) {
            "Invalid receipt owner or object identity."
        }
        return id
    }

    fun inspect(file: File): ReceiptInfo {
        require(file.isFile && file.canRead()) { "The saved photo is missing or unreadable. Attach it again." }
        require(file.length() in 1..MAX_BYTES) { "Receipt photos must be no larger than 10 MiB." }
        val header = ByteArray(8)
        file.inputStream().use { require(it.read(header) >= 8) { "The receipt is not a complete image." } }
        val mime = when {
            header.take(3) == listOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()) -> "image/jpeg"
            header.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) -> "image/png"
            else -> throw IllegalArgumentException("Use a JPEG or PNG receipt photo.")
        }
        // I checked image bounds before a sampled decode so a small file could not allocate a huge bitmap (Google, 2026au).
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outMimeType == mime && options.outWidth in 1..16384 && options.outHeight in 1..16384
            && options.outWidth.toLong() * options.outHeight <= 40_000_000) { "The receipt image is unreadable or too large to decode safely." }
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize.coerceAtLeast(1) > 1024) {
            options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
        }
        options.inJustDecodeBounds = false
        requireNotNull(BitmapFactory.decodeFile(file.absolutePath, options)) { "The receipt image could not be decoded." }.recycle()
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return ReceiptInfo(digest.digest().joinToString("") { "%02x".format(it) }, file.length(), mime)
    }
}
