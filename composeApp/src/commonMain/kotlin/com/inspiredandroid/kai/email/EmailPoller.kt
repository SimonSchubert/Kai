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
                val newUids = unseenUids.filter { it > syncState.lastSeenUid }.take(MAX_FETCH_PER_POLL)

                var updated = syncState.copy(
                    lastSyncEpochMs = attemptAt,
                    lastAttemptEpochMs = attemptAt,
                    unreadCount = unseenUids.size,
                    lastError = null,
                )
                if (newUids.isNotEmpty()) {
                    emailStore.addPending(imap.fetchHeaders(newUids, account.id))
                    updated = updated.copy(lastSeenUid = newUids.max())
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
