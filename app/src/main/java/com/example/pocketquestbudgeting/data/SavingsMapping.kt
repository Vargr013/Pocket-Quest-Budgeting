package com.example.pocketquestbudgeting.data

object SavingsMapping {
    const val GOALS = "savingsGoals"
    const val CONTRIBUTIONS = "savingsContributions"
    const val AWARDS = "earnedAchievements"
    val types = setOf(GOALS, CONTRIBUTIONS, AWARDS)
    fun immutable(type: String) = type == CONTRIBUTIONS || type == AWARDS

    fun fields(goal: SavingsGoalEntity): Map<String, Any?> = mapOf(
        "name" to goal.name, "targetCents" to goal.targetCents, "targetDate" to goal.targetDate, "note" to goal.note,
    )
    fun fields(row: SavingsContributionEntity, goalCloudId: String): Map<String, Any?> = mapOf(
        "goalCloudId" to goalCloudId, "kind" to row.kind, "amountCents" to row.amountCents,
        "effectiveDate" to row.effectiveDate, "reversesBusinessId" to row.reversesBusinessId, "note" to row.note,
    )
    fun fields(award: EarnedAchievementEntity): Map<String, Any?> = mapOf(
        "badgeId" to award.badgeId, "criteriaVersion" to award.criteriaVersion.toLong(),
        "earnedAtEpochMillis" to award.earnedAtEpochMillis, "earnedDate" to award.earnedDate,
        "earnedZoneId" to award.earnedZoneId, "evidenceRecordId" to award.evidenceRecordId,
    )
    fun goal(userId: Long, id: String, fields: Map<String, Any?>, deleted: Boolean = false) = SavingsGoalEntity(
        userId = userId, cloudId = id, name = fields["name"] as String, targetCents = fields["targetCents"] as Long,
        targetDate = fields["targetDate"] as String?, note = fields["note"] as String?, isDeleted = deleted,
    )
    fun contribution(userId: Long, goalId: Long, id: String, fields: Map<String, Any?>) = SavingsContributionEntity(
        userId = userId, goalId = goalId, businessId = id, kind = fields["kind"] as String,
        amountCents = fields["amountCents"] as Long, effectiveDate = fields["effectiveDate"] as String,
        reversesBusinessId = fields["reversesBusinessId"] as String?, note = fields["note"] as String?,
    )
    fun award(userId: Long, id: String, fields: Map<String, Any?>): EarnedAchievementEntity {
        val version = fields["criteriaVersion"] as Long
        require(version in 1..Int.MAX_VALUE.toLong())
        return EarnedAchievementEntity(userId = userId, awardKey = id, badgeId = fields["badgeId"] as String,
            criteriaVersion = version.toInt(), earnedAtEpochMillis = fields["earnedAtEpochMillis"] as Long?,
            earnedDate = fields["earnedDate"] as String?, earnedZoneId = fields["earnedZoneId"] as String?,
            evidenceRecordId = fields["evidenceRecordId"] as String)
    }
    fun validate(type: String, fields: Map<String, Any?>) {
        when (type) {
            GOALS -> SavingsChecks.requireGoal(goal(1, "00000000-0000-0000-0000-000000000000", fields))
            CONTRIBUTIONS -> {
                val goal = fields["goalCloudId"] as String
                require(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(goal))
                val opening = fields["kind"] == SavingsChecks.OPENING
                val key = if (opening) "opening:$goal" else if (fields["reversesBusinessId"] == "a") "b" else "a"
                SavingsChecks.requireContribution(contribution(1, 1, key, fields))
            }
            AWARDS -> EarnedAchievementChecks.requireValues(award(1, "synthetic", fields))
        }
    }
}

object EarnedAchievementChecks {
    fun requireValues(award: EarnedAchievementEntity) {
        require(award.userId > 0 && award.criteriaVersion > 0)
        SavingsChecks.requireKey(award.awardKey)
        SavingsChecks.requireKey(award.badgeId)
        SavingsChecks.requireKey(award.evidenceRecordId)
        require(award.earnedAtEpochMillis != null || award.earnedDate != null) { "Supply the earned instant or business date." }
        require(award.earnedAtEpochMillis == null || award.earnedAtEpochMillis >= 0)
        require(award.earnedDate == null || ExpenseChecks.validDate(award.earnedDate))
        require(award.earnedDate == null || award.earnedZoneId != null)
        require(award.earnedZoneId == null || award.earnedZoneId in java.util.TimeZone.getAvailableIDs()) { "Supply a recognised timezone." }
    }
}
