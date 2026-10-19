package com.example.pocketquestbudgeting.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.FirebaseNetworkException

internal suspend fun sendImmutableSavings(firestore: FirebaseFirestore, operation: SyncOperation, stillCurrent: () -> Boolean): SendResult {
    require(operation.baseRevision == 0L && !operation.isDeleted)
    val fields = SyncMapping.decode(operation.recordType, operation.payload)
    SyncMapping.checkIdentity(operation.recordType, operation.recordId, fields)
    val fingerprint = SyncMapping.fingerprint(operation)
    val owner = firestore.collection("users").document(operation.uid)
    val reference = owner.collection(operation.recordType).document(operation.recordId)
    val acknowledgement = owner.collection("mutations").document(operation.mutationId)
    // I used the business identity before any balance change, including a reissued transport attempt (Google, 2026x).
    var observedBalanceMutation: String? = null
    return try { firestore.runTransaction { transaction ->
        check(stillCurrent()) { "Session changed." }
        val receipt = transaction.get(acknowledgement)
        val existing = transaction.get(reference)
        if (receipt.exists()) {
            check(receipt.getString("fingerprint") == fingerprint && receipt.getLong("revision") == 1L)
            SendResult.Accepted(1)
        } else if (existing.exists()) {
            val remote = existing.toSyncRecord(operation.uid, operation.userId, operation.recordType)
            if (!remote.isDeleted && SyncMapping.decode(remote.recordType, remote.payload) == fields) SendResult.Accepted(1)
            else SendResult.Conflict(remote)
        } else {
            if (operation.recordType == SavingsMapping.CONTRIBUTIONS) {
                val goalId = fields["goalCloudId"] as String
                val goal = transaction.get(owner.collection(SavingsMapping.GOALS).document(goalId))
                check(goal.exists() && goal.getBoolean("isDeleted") == false) { "This savings goal is unavailable on the server. The contribution is retained locally." }
                val balanceRef = owner.collection("savingsBalances").document(goalId)
                val balance = transaction.get(balanceRef)
                observedBalanceMutation = balance.getString("lastMutationId")
                val before = if (balance.exists()) requireNotNull(balance.getLong("balanceCents")) else 0L
                val amount = fields["amountCents"] as Long
                val reverse = fields["kind"] == SavingsChecks.REVERSAL
                val originalId = fields["reversesBusinessId"] as String?
                val guardRef = originalId?.let { owner.collection("savingsReversals").document(it) }
                val original = originalId?.let { transaction.get(owner.collection(SavingsMapping.CONTRIBUTIONS).document(it)) }
                val guard = guardRef?.let { transaction.get(it) }
                if (reverse) {
                    check(original?.exists() == true && original.getString("goalCloudId") == goalId
                        && original.getString("kind") != SavingsChecks.REVERSAL && original.getLong("amountCents") == amount)
                    check(guard?.exists() == false) { "This contribution was already reversed on the server." }
                }
                val after = if (reverse) Math.subtractExact(before, amount) else Math.addExact(before, amount)
                check(after >= 0)
                check(stillCurrent()) { "Session changed." }
                transaction.set(balanceRef, mapOf("schemaVersion" to 1L, "balanceCents" to after,
                    "lastContributionId" to operation.recordId, "lastMutationId" to operation.mutationId, "updatedAt" to FieldValue.serverTimestamp()))
                if (guardRef != null) transaction.set(guardRef, mapOf("schemaVersion" to 1L, "goalCloudId" to goalId,
                    "contributionId" to operation.recordId, "lastMutationId" to operation.mutationId, "createdAt" to FieldValue.serverTimestamp()))
            }
            check(stillCurrent()) { "Session changed." }
            transaction.set(reference, fields + mapOf("schemaVersion" to 1L, "revision" to 1L,
                "lastMutationId" to operation.mutationId, "updatedAt" to FieldValue.serverTimestamp(), "isDeleted" to false, "deletedAt" to null))
            transaction.set(acknowledgement, mapOf("recordType" to operation.recordType, "recordId" to operation.recordId,
                "baseRevision" to 0L, "revision" to 1L, "fingerprint" to fingerprint))
            SendResult.Accepted(1)
        }
    }.awaitSync()
    } catch (error: FirebaseFirestoreException) {
        if (error.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw error
        check(stillCurrent()) { "Session changed." }
        val current = reference.get(Source.SERVER).awaitSync()
        if (current.exists()) {
            val remote = current.toSyncRecord(operation.uid, operation.userId, operation.recordType)
            check(stillCurrent()) { "Session changed." }
            if (!remote.isDeleted && SyncMapping.decode(remote.recordType, remote.payload) == fields) SendResult.Accepted(1)
            else SendResult.Conflict(remote)
        } else if (operation.recordType == SavingsMapping.CONTRIBUTIONS) {
            val goal = owner.collection(SavingsMapping.GOALS).document(fields["goalCloudId"] as String).get(Source.SERVER).awaitSync()
            check(goal.exists() && goal.getBoolean("isDeleted") == false) { "This savings goal is unavailable on the server. The contribution is retained locally." }
            val latest = owner.collection("savingsBalances").document(fields["goalCloudId"] as String).get(Source.SERVER).awaitSync()
            check(stillCurrent()) { "Session changed." }
            // I retried a raced balance without treating a schema rejection as an acknowledgement (Google, 2026x).
            if (latest.getString("lastMutationId") != observedBalanceMutation) throw FirebaseNetworkException("Savings balance changed during publication; retry the retained contribution.")
            throw error
        } else throw error
    }
}
