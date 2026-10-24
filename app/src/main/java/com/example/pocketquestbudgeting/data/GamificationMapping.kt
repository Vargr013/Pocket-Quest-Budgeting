package com.example.pocketquestbudgeting.data

object GamificationMapping {
    const val ACTIVITY = "activityDays"
    const val ITEMS = "customWorthItems"
    val types = setOf(ACTIVITY, ITEMS)

    fun requireItem(item: CustomWorthItemEntity) {
        require(item.userId > 0)
        require(item.name.isNotBlank() && item.name == item.name.trim() && item.name.length <= 40) { "Enter an item name of at most 40 characters." }
        require(item.valueCents > 0) { "Enter a value greater than zero." }
        require(item.category == WorthCategory.CUSTOM.name)
        require(item.cloudId != null && Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}").matches(item.cloudId))
    }

    fun fields(item: CustomWorthItemEntity): Map<String, Any?> = mapOf(
        "name" to item.name, "valueCents" to item.valueCents, "category" to item.category,
    )

    fun item(owner: Long, cloudId: String, fields: Map<String, Any?>, deleted: Boolean = false, id: Long = 0) =
        CustomWorthItemEntity(id = id, userId = owner, name = fields["name"] as String,
            valueCents = fields["valueCents"] as Long, category = fields["category"] as String,
            cloudId = cloudId, isDeleted = deleted)

    fun validate(type: String, fields: Map<String, Any?>) {
        when (type) {
            ACTIVITY -> require(ExpenseChecks.validDate(fields["date"] as? String ?: "")) { "Use a valid activity date." }
            ITEMS -> requireItem(item(1, "00000000-0000-0000-0000-000000000000", fields))
            else -> error("Unsupported gamification record.")
        }
    }
}
