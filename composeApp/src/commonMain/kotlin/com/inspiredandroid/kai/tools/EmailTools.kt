package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.data.EmailAccount
import com.inspiredandroid.kai.data.EmailStore
import com.inspiredandroid.kai.email.ImapClient
import com.inspiredandroid.kai.email.ServerAutoDetect
import com.inspiredandroid.kai.email.SmtpClient
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_check_email_description
import kai.composeapp.generated.resources.tool_check_email_name
import kai.composeapp.generated.resources.tool_compose_email_description
import kai.composeapp.generated.resources.tool_compose_email_name
import kai.composeapp.generated.resources.tool_read_email_description
import kai.composeapp.generated.resources.tool_read_email_name
import kai.composeapp.generated.resources.tool_reply_email_description
import kai.composeapp.generated.resources.tool_reply_email_name
import kai.composeapp.generated.resources.tool_search_email_description
import kai.composeapp.generated.resources.tool_search_email_name
import kai.composeapp.generated.resources.tool_setup_email_description
import kai.composeapp.generated.resources.tool_setup_email_name
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object EmailTools {

    private suspend fun <T> withImapSession(
        account: EmailAccount,
        emailStore: EmailStore,
        block: suspend (ImapClient) -> T,
    ): T {
        val imap = ImapClient(account.imapHost, account.imapPort)
        try {
            val password = emailStore.getPassword(account.id)
            imap.connect()
            imap.login(account.username.ifEmpty { account.email }, password)
            imap.selectInbox()
            return block(imap)
        } finally {
            imap.logout()
        }
    }

    private suspend fun withSmtpSession(
        account: EmailAccount,
        emailStore: EmailStore,
        block: suspend (SmtpClient, String) -> Map<String, Any>,
    ): Map<String, Any> {
        val smtp = SmtpClient(account.smtpHost, account.smtpPort, account.useStartTls)
        val password = emailStore.getPassword(account.id)
        smtp.connect()
        smtp.ehlo()
        if (account.useStartTls) smtp.startTls()
        smtp.authenticate(account.username.ifEmpty { account.email }, password)
        val from = if (account.displayName.isNotEmpty()) {
            "${account.displayName} <${account.email}>"
        } else {
            account.email
        }
        try {
            return block(smtp, from)
        } finally {
            smtp.quit()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun setupEmailTool(emailStore: EmailStore) = object : Tool {
        override val schema = ToolSchema(
            name = "setup_email",
            description = "Connect an email account for reading and sending. Auto-detects server settings for Gmail, Outlook, Yahoo, iCloud, etc. For Gmail, guide the user to create an App Password at myaccount.google.com > Security > 2-Step Verification > App passwords. For iCloud, guide them to appleid.apple.com > Sign-In and Security > App-Specific Passwords.",
            parameters = mapOf(
                "email" to ParameterSchema(type = "string", description = "The email address to connect", required = true),
                "password" to ParameterSchema(type = "string", description = "The password or app-specific password", required = true),
                "imap_host" to ParameterSchema(type = "string", description = "IMAP server hostname (auto-detected if omitted)", required = false),
                "imap_port" to ParameterSchema(type = "integer", description = "IMAP port (default 993)", required = false),
                "smtp_host" to ParameterSchema(type = "string", description = "SMTP server hostname (auto-detected if omitted)", required = false),
                "smtp_port" to ParameterSchema(type = "integer", description = "SMTP port (default 587)", required = false),
                "display_name" to ParameterSchema(type = "string", description = "Display name for outgoing emails", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val email = args["email"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing email")
            val password = args["password"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing password")
            val displayName = args["display_name"]?.toString() ?: ""

            // Auto-detect or use provided settings
            val detected = ServerAutoDetect.detect(email)
            val imapHost = args["imap_host"]?.toString() ?: detected?.imapHost
                ?: return mapOf("success" to false, "error" to "Cannot auto-detect server for this email. Please provide imap_host and smtp_host.")
            val imapPort = (args["imap_port"] as? Number)?.toInt() ?: detected?.imapPort ?: 993
            val smtpHost = args["smtp_host"]?.toString() ?: detected?.smtpHost
                ?: return mapOf("success" to false, "error" to "Cannot auto-detect SMTP server. Please provide smtp_host.")
            val smtpPort = (args["smtp_port"] as? Number)?.toInt() ?: detected?.smtpPort ?: 587

            // Test IMAP connection
            val imap = ImapClient(imapHost, imapPort)
            return try {
                imap.connect()
                val loginOk = imap.login(email, password)
                imap.logout()

                if (!loginOk) {
                    return mapOf(
                        "success" to false,
                        "error" to "Login failed. Check your password." +
                            (detected?.note?.let { " Note: $it" } ?: ""),
                    )
                }

                val accountId = Uuid.random().toString()
                val account = EmailAccount(
                    id = accountId,
                    email = email,
                    displayName = displayName,
                    imapHost = imapHost,
                    imapPort = imapPort,
                    smtpHost = smtpHost,
                    smtpPort = smtpPort,
                    username = email,
                    useStartTls = detected?.useStartTls ?: true,
                )
                emailStore.addAccount(account)
                emailStore.setPassword(accountId, password)

                mapOf(
                    "success" to true,
                    "account_id" to accountId,
                    "email" to email,
                    "imap_host" to imapHost,
                    "smtp_host" to smtpHost,
                    "message" to "Email account connected successfully. You can now use check_email, read_email, reply_email, and search_email.",
                )
            } catch (e: Exception) {
                mapOf(
                    "success" to false,
                    "error" to "Connection failed: ${e.message}" +
                        (detected?.note?.let { " Note: $it" } ?: ""),
                )
            }
        }
    }

    fun checkEmailTool(emailStore: EmailStore) = object : Tool {
        override val schema = ToolSchema(
            name = "check_email",
            description = "Check for unread emails. Returns a list of unread emails with sender, subject, date, and preview. If multiple accounts are connected, checks all of them.",
            parameters = mapOf(
                "account_id" to ParameterSchema(type = "string", description = "Specific account ID to check (checks all if omitted)", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val accountId = args["account_id"]?.toString()
            val accounts = if (accountId != null) {
                listOfNotNull(emailStore.getAccount(accountId))
            } else {
                emailStore.getAccounts()
            }

            if (accounts.isEmpty()) {
                return mapOf("success" to false, "error" to "No email accounts configured. Use setup_email first.")
            }

            val allEmails = mutableListOf<Map<String, Any?>>()
            val errors = mutableListOf<String>()

            for (account in accounts) {
                try {
                    withImapSession(account, emailStore) { imap ->
                        val unseenUids = imap.searchUnseen()
                        val messages = imap.fetchHeaders(unseenUids.takeLast(20), account.id)
                        for (msg in messages) {
                            allEmails.add(
                                mapOf(
                                    "uid" to msg.uid,
                                    "account_id" to account.id,
                                    "account_email" to account.email,
                                    "from" to msg.from,
                                    "subject" to msg.subject,
                                    "date" to msg.date,
                                    "preview" to msg.preview,
                                ),
                            )
                        }
                    }
                } catch (e: Exception) {
                    errors.add("${account.email}: ${e.message}")
                }
            }

            return mapOf(
                "success" to true,
                "unread_count" to allEmails.size,
                "emails" to allEmails,
                "errors" to errors,
            )
        }
    }

    fun readEmailTool(emailStore: EmailStore) = object : Tool {
        override val schema = ToolSchema(
            name = "read_email",
            description = "Read the full body of a specific email by its UID. Use check_email first to get the UID.",
            parameters = mapOf(
                "uid" to ParameterSchema(type = "integer", description = "The email UID from check_email results", required = true),
                "account_id" to ParameterSchema(type = "string", description = "The account ID that owns this email", required = true),
                "mark_read" to ParameterSchema(type = "boolean", description = "Whether to mark the email as read (default true)", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val uid = (args["uid"] as? Number)?.toLong()
                ?: return mapOf("success" to false, "error" to "Missing uid")
            val accountId = args["account_id"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing account_id")
            val markRead = (args["mark_read"] as? Boolean) ?: true

            val account = emailStore.getAccount(accountId)
                ?: return mapOf("success" to false, "error" to "Account not found: $accountId")

            return try {
                withImapSession(account, emailStore) { imap ->
                    val msg = imap.fetchBody(uid, account.id)
                    if (markRead) imap.markAsRead(uid)
                    if (msg != null) {
                        mapOf(
                            "success" to true,
                            "uid" to msg.uid,
                            "from" to msg.from,
                            "to" to msg.to,
                            "subject" to msg.subject,
                            "date" to msg.date,
                            "body" to msg.body,
                            "message_id" to msg.messageId,
                        )
                    } else {
                        mapOf("success" to false, "error" to "Email not found with UID $uid")
                    }
                }
            } catch (e: Exception) {
                mapOf("success" to false, "error" to "Failed to read email: ${e.message}")
            }
        }
    }

    fun replyEmailTool(emailStore: EmailStore) = object : Tool {
        override val schema = ToolSchema(
            name = "reply_email",
            description = "Reply to an email. Uses SMTP with proper In-Reply-To threading. Use read_email first to get the message_id for threading.",
            parameters = mapOf(
                "account_id" to ParameterSchema(type = "string", description = "The account ID to send from", required = true),
                "to" to ParameterSchema(type = "string", description = "Recipient email address", required = true),
                "subject" to ParameterSchema(type = "string", description = "Email subject (typically 'Re: original subject')", required = true),
                "body" to ParameterSchema(type = "string", description = "Reply body text", required = true),
                "in_reply_to" to ParameterSchema(type = "string", description = "Message-ID of the email being replied to (for threading)", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val accountId = args["account_id"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing account_id")
            val to = args["to"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing to")
            val subject = args["subject"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing subject")
            val body = args["body"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing body")
            val inReplyTo = args["in_reply_to"]?.toString()

            val account = emailStore.getAccount(accountId)
                ?: return mapOf("success" to false, "error" to "Account not found: $accountId")

            return try {
                withSmtpSession(account, emailStore) { smtp, from ->
                    val success = smtp.sendReply(
                        from = account.email,
                        to = to,
                        subject = subject,
                        body = body,
                        inReplyTo = inReplyTo,
                    )
                    if (success) {
                        mapOf(
                            "success" to true,
                            "message" to "Reply sent successfully to $to",
                            "from" to from,
                            "to" to to,
                            "subject" to subject,
                        )
                    } else {
                        mapOf("success" to false, "error" to "SMTP server rejected the message")
                    }
                }
            } catch (e: Exception) {
                mapOf("success" to false, "error" to "Failed to send reply: ${e.message}")
            }
        }
    }

    fun composeEmailTool(emailStore: EmailStore) = object : Tool {
        override val schema = ToolSchema(
            name = "compose_email",
            description = "Compose and send a new email. Use this when the user wants to write a fresh email to someone (not a reply to an existing thread).",
            parameters = mapOf(
                "account_id" to ParameterSchema(type = "string", description = "The account ID to send from. Use check_email or search_email to find account IDs if needed.", required = true),
                "to" to ParameterSchema(type = "string", description = "Recipient email address", required = true),
                "subject" to ParameterSchema(type = "string", description = "Email subject line", required = true),
                "body" to ParameterSchema(type = "string", description = "Email body text", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val accountId = args["account_id"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing account_id")
            val to = args["to"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing to")
            val subject = args["subject"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing subject")
            val body = args["body"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing body")

            val account = emailStore.getAccount(accountId)
                ?: return mapOf("success" to false, "error" to "Account not found: $accountId")

            return try {
                withSmtpSession(account, emailStore) { smtp, from ->
                    val success = smtp.sendReply(
                        from = account.email,
                        to = to,
                        subject = subject,
                        body = body,
                        inReplyTo = null,
                    )
                    if (success) {
                        mapOf(
                            "success" to true,
                            "message" to "Email sent successfully to $to",
                            "from" to from,
                            "to" to to,
                            "subject" to subject,
                        )
                    } else {
                        mapOf("success" to false, "error" to "SMTP server rejected the message")
                    }
                }
            } catch (e: Exception) {
                mapOf("success" to false, "error" to "Failed to send email: ${e.message}")
            }
        }
    }

    fun searchEmailTool(emailStore: EmailStore) = object : Tool {
        override val schema = ToolSchema(
            name = "search_email",
            description = "Search emails by sender, subject, or date. Returns matching email summaries.",
            parameters = mapOf(
                "account_id" to ParameterSchema(type = "string", description = "Account ID to search in (required)", required = true),
                "from" to ParameterSchema(type = "string", description = "Search by sender email/name", required = false),
                "subject" to ParameterSchema(type = "string", description = "Search by subject text", required = false),
                "since" to ParameterSchema(type = "string", description = "Search emails since date (format: 01-Jan-2025)", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val accountId = args["account_id"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing account_id")
            val fromQuery = args["from"]?.toString()
            val subjectQuery = args["subject"]?.toString()
            val sinceDate = args["since"]?.toString()

            if (fromQuery == null && subjectQuery == null && sinceDate == null) {
                return mapOf("success" to false, "error" to "At least one search criteria required (from, subject, or since)")
            }

            val account = emailStore.getAccount(accountId)
                ?: return mapOf("success" to false, "error" to "Account not found: $accountId")

            return try {
                withImapSession(account, emailStore) { imap ->
                    val uids = when {
                        fromQuery != null -> imap.searchByFrom(fromQuery)
                        subjectQuery != null -> imap.searchBySubject(subjectQuery)
                        sinceDate != null -> imap.searchSince(sinceDate)
                        else -> emptyList()
                    }
                    val messages = imap.fetchHeaders(uids.takeLast(20), account.id)
                    mapOf(
                        "success" to true,
                        "count" to messages.size,
                        "emails" to messages.map { msg ->
                            mapOf(
                                "uid" to msg.uid,
                                "from" to msg.from,
                                "subject" to msg.subject,
                                "date" to msg.date,
                                "preview" to msg.preview,
                                "is_read" to msg.isRead,
                            )
                        },
                    )
                }
            } catch (e: Exception) {
                mapOf("success" to false, "error" to "Search failed: ${e.message}")
            }
        }
    }

    // ToolInfo definitions for settings display
    val setupEmailToolInfo = ToolInfo(
        id = "setup_email",
        name = "Setup Email",
        description = "Connect an email account",
        nameRes = Res.string.tool_setup_email_name,
        descriptionRes = Res.string.tool_setup_email_description,
    )

    val checkEmailToolInfo = ToolInfo(
        id = "check_email",
        name = "Check Email",
        description = "Check for unread emails",
        nameRes = Res.string.tool_check_email_name,
        descriptionRes = Res.string.tool_check_email_description,
    )

    val readEmailToolInfo = ToolInfo(
        id = "read_email",
        name = "Read Email",
        description = "Read full email body",
        nameRes = Res.string.tool_read_email_name,
        descriptionRes = Res.string.tool_read_email_description,
    )

    val replyEmailToolInfo = ToolInfo(
        id = "reply_email",
        name = "Reply Email",
        description = "Send an email reply",
        nameRes = Res.string.tool_reply_email_name,
        descriptionRes = Res.string.tool_reply_email_description,
    )

    val composeEmailToolInfo = ToolInfo(
        id = "compose_email",
        name = "Compose Email",
        description = "Compose and send a new email",
        nameRes = Res.string.tool_compose_email_name,
        descriptionRes = Res.string.tool_compose_email_description,
    )

    val searchEmailToolInfo = ToolInfo(
        id = "search_email",
        name = "Search Email",
        description = "Search emails by sender, subject, or date",
        nameRes = Res.string.tool_search_email_name,
        descriptionRes = Res.string.tool_search_email_description,
    )

    val emailToolDefinitions = listOf(
        setupEmailToolInfo,
        checkEmailToolInfo,
        readEmailToolInfo,
        replyEmailToolInfo,
        composeEmailToolInfo,
        searchEmailToolInfo,
    )

    fun getEmailTools(emailStore: EmailStore): List<Tool> = listOf(
        setupEmailTool(emailStore),
        checkEmailTool(emailStore),
        readEmailTool(emailStore),
        replyEmailTool(emailStore),
        composeEmailTool(emailStore),
        searchEmailTool(emailStore),
    )
}
