package com.inspiredandroid.kai.email

import com.inspiredandroid.kai.data.EmailAccount
import com.inspiredandroid.kai.data.EmailStore
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class EmailPoller(
    private val emailStore: EmailStore,
    private val imapClientFactory: (host: String, port: Int) -> ImapClient = ::ImapClient,
) {
    suspend fun poll(account: EmailAccount) {
        val syncState = emailStore.getSyncState(account.id)
        val attemptAt = Clock.System.now().toEpochMilliseconds()
        try {
            val password = emailStore.getPassword(account.id)
            val imap = imapClientFactory(account.imapHost, account.imapPort)
            try {
                imap.connect()
                imap.login(account.username.ifEmpty { account.email }, password)
                imap.selectInbox()
                // searchUnseen returns UIDs ascending per RFC 3501, so filtering alone
                // keeps oldest-first ordering — overflow past MAX_FETCH_PER_POLL is
                // picked up on the next poll.
                val unseenUids = imap.searchUnseen()
                // lastSeenUid = highest UID already delivered to the user (via heartbeat
                // or check_email). Anything ≤ that has been surfaced and shouldn't return
                // to pending. Also skip UIDs already queued so back-to-back polls without
                // a heartbeat in between don't duplicate.
                val pendingUidsForAccount = emailStore.getPending()
                    .asSequence()
                    .filter { it.accountId == account.id }
                    .map { it.uid }
                    .toSet()
                val newUids = unseenUids
                    .filter { it > syncState.lastSeenUid && it !in pendingUidsForAccount }
                    .take(MAX_FETCH_PER_POLL)

                val updated = syncState.copy(
                    lastSyncEpochMs = attemptAt,
                    lastAttemptEpochMs = attemptAt,
                    unreadCount = unseenUids.size,
                    lastError = null,
                )
                if (newUids.isNotEmpty()) {
                    emailStore.addPending(imap.fetchHeaders(newUids, account.id))
                }
                emailStore.updateSyncState(updated)
            } finally {
                imap.logout()
            }
        } catch (e: Exception) {
            emailStore.updateSyncState(
                syncState.copy(
                    lastAttemptEpochMs = attemptAt,
                    lastError = e.message ?: e::class.simpleName ?: "Poll failed",
                ),
            )
        }
    }

    companion object {
        const val MAX_FETCH_PER_POLL = 50
    }
}
