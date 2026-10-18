package com.example.pocketquestbudgeting.data

import org.json.JSONObject
import java.security.MessageDigest

object SyncMapping {
    private val fields = mapOf(
        "categories" to setOf("name", "minimumCents", "maximumCents"),
        "expenses" to setOf("categoryId", "amountCents", "date", "startTime", "endTime", "description", "receiptObjectPath"),
        "monthlyGoals" to setOf("minimumCents", "maximumCents", "startDate", "endDate"),
    )

    fun checkKey(type: String, id: String) {
        require(type in fields) { "Unsupported record type." }
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid stable record ID." }
    }

    fun encode(type: String, values: Map<String, Any?>): String {
        require(values.keys == fields[type]) { "Use the exact agreed business fields." }
        if (type == "categories") {
            val name = values["name"] as? String ?: error("Invalid category name.")
            require(name.isNotBlank() && name == name.trim()) { "Use a nonblank trimmed category name." }
        }
        if (type == "expenses") {
            checkKey("categories", values["categoryId"] as String)
            val receipt = values["receiptObjectPath"]
            require(receipt == null || receipt is String && receipt.matches(Regex(
                "users/[^/]+/receipts/[A-Za-z0-9_-]{1,80}/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))) {
                "Use a cloud receipt object path, never a local file path."
            }
            ExpenseChecks.requireValues(values["amountCents"] as Long, values["date"] as String,
                values["startTime"] as String, values["endTime"] as String, values["description"] as String)
        }
        val json = JSONObject()
        values.toSortedMap().forEach { (key, value) ->
            // I kept cents as integers and excluded device paths and credentials (Google, 2026v).
            require(value == null || value is String || value is Long || value is Boolean) { "Unsupported field type." }
            when (key) {
                "minimumCents", "maximumCents", "amountCents" -> require(value is Long && value >= 0) { "Use whole nonnegative cents." }
                "receiptObjectPath" -> require(value == null || value is String) { "Invalid receipt reference." }
                else -> require(value is String) { "Invalid text field." }
            }
            json.put(key, value ?: JSONObject.NULL)
        }
        if ("minimumCents" in values) require((values["minimumCents"] as Long) <= (values["maximumCents"] as Long))
        if ("amountCents" in values) require((values["amountCents"] as Long) > 0)
        return json.toString()
    }

    fun decode(type: String, payload: String): Map<String, Any?> {
        val json = JSONObject(payload)
        val values = json.keys().asSequence().associateWith { key ->
            when (val value = json.get(key)) {
                JSONObject.NULL -> null
                is Int -> value.toLong()
                else -> value
            }
        }
        encode(type, values)
        return values
    }

    fun fingerprint(operation: SyncOperation): String {
        val canonical = encode(operation.recordType, decode(operation.recordType, operation.payload))
        val text = "${operation.recordType}\n${operation.recordId}\n${operation.baseRevision}\n${operation.isDeleted}\n$canonical"
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
