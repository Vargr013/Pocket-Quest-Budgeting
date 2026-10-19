package com.example.pocketquestbudgeting.data

object SavingsChecks {
    const val OPENING = "OPENING"
    const val ADDITION = "ADDITION"
    const val REVERSAL = "REVERSAL"

    fun requireKey(value: String) {
        require(Regex("[A-Za-z0-9_:.\\-]{1,128}").matches(value)) { "Use a stable record key of at most 128 characters." }
    }

    fun requireGoal(goal: SavingsGoalEntity) {
        require(goal.userId > 0)
        require(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(goal.cloudId))
        require(goal.name.isNotBlank() && goal.name == goal.name.trim() && goal.name.length <= 100) { "Enter a goal name of at most 100 characters." }
        require(goal.targetCents > 0) { "Enter a positive whole-cent target." }
        require(goal.targetDate == null || ExpenseChecks.validDate(goal.targetDate)) { "Enter a valid target date." }
        requireNote(goal.note)
    }

    fun requireContribution(row: SavingsContributionEntity) {
        require(row.userId > 0 && row.goalId > 0)
        requireKey(row.businessId)
        require(row.amountCents > 0) { "Enter a positive whole-cent contribution." }
        require(ExpenseChecks.validDate(row.effectiveDate)) { "Enter a valid contribution date." }
        require(row.kind in setOf(OPENING, ADDITION, REVERSAL))
        require((row.kind == OPENING) == row.businessId.startsWith("opening:"))
        require((row.kind == REVERSAL) == (row.reversesBusinessId != null))
        row.reversesBusinessId?.let { requireKey(it); require(it != row.businessId) }
        requireNote(row.note)
    }

    fun requireNote(note: String?) {
        require(note == null || (note.isNotBlank() && note == note.trim() && note.length <= 1000)) { "Use no note or a trimmed note of at most 1000 characters." }
    }

    // I totalled the retained events without depending on their download order.
    fun balance(rows: List<SavingsContributionEntity>): Long {
        val total = rows.fold(java.math.BigInteger.ZERO) { sum, row ->
            requireContribution(row)
            val amount = java.math.BigInteger.valueOf(row.amountCents)
            if (row.kind == REVERSAL) sum.subtract(amount) else sum.add(amount)
        }
        require(total.signum() >= 0) { "Saved amounts cannot become negative." }
        if (total > java.math.BigInteger.valueOf(Long.MAX_VALUE)) throw ArithmeticException("Saved amount exceeds whole-cent range.")
        return total.toLong()
    }
}
