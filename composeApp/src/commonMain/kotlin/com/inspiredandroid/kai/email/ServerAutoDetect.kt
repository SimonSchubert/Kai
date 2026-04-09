package com.inspiredandroid.kai.email

/**
 * Auto-detects IMAP/SMTP server settings based on email domain.
 */
object ServerAutoDetect {

    data class ServerConfig(
        val imapHost: String,
        val imapPort: Int = 993,
        val smtpHost: String,
        val smtpPort: Int = 587,
        val useStartTls: Boolean = true,
        val note: String = "",
    )

    private val knownProviders = mapOf(
        "gmail.com" to ServerConfig(
            imapHost = "imap.gmail.com",
            smtpHost = "smtp.gmail.com",
            note = "Requires an App Password. Go to myaccount.google.com > Security > 2-Step Verification > App passwords",
        ),
        "googlemail.com" to ServerConfig(
            imapHost = "imap.gmail.com",
            smtpHost = "smtp.gmail.com",
            note = "Requires an App Password. Go to myaccount.google.com > Security > 2-Step Verification > App passwords",
        ),
        "outlook.com" to ServerConfig(
            imapHost = "outlook.office365.com",
            smtpHost = "smtp.office365.com",
        ),
        "hotmail.com" to ServerConfig(
            imapHost = "outlook.office365.com",
            smtpHost = "smtp.office365.com",
        ),
        "live.com" to ServerConfig(
            imapHost = "outlook.office365.com",
            smtpHost = "smtp.office365.com",
        ),
        "yahoo.com" to ServerConfig(
            imapHost = "imap.mail.yahoo.com",
            smtpHost = "smtp.mail.yahoo.com",
            note = "Requires an App Password. Go to Yahoo Account Security > Generate app password",
        ),
        "icloud.com" to ServerConfig(
            imapHost = "imap.mail.me.com",
            smtpHost = "smtp.mail.me.com",
            note = "Requires an App-Specific Password. Go to appleid.apple.com > Sign-In and Security > App-Specific Passwords",
        ),
        "me.com" to ServerConfig(
            imapHost = "imap.mail.me.com",
            smtpHost = "smtp.mail.me.com",
            note = "Requires an App-Specific Password. Go to appleid.apple.com > Sign-In and Security > App-Specific Passwords",
        ),
        "mac.com" to ServerConfig(
            imapHost = "imap.mail.me.com",
            smtpHost = "smtp.mail.me.com",
            note = "Requires an App-Specific Password. Go to appleid.apple.com > Sign-In and Security > App-Specific Passwords",
        ),
        "aol.com" to ServerConfig(
            imapHost = "imap.aol.com",
            smtpHost = "smtp.aol.com",
        ),
        "protonmail.com" to ServerConfig(
            imapHost = "127.0.0.1",
            imapPort = 1143,
            smtpHost = "127.0.0.1",
            smtpPort = 1025,
            useStartTls = false,
            note = "Requires ProtonMail Bridge running locally",
        ),
        "proton.me" to ServerConfig(
            imapHost = "127.0.0.1",
            imapPort = 1143,
            smtpHost = "127.0.0.1",
            smtpPort = 1025,
            useStartTls = false,
            note = "Requires ProtonMail Bridge running locally",
        ),
        "zoho.com" to ServerConfig(
            imapHost = "imap.zoho.com",
            smtpHost = "smtp.zoho.com",
            smtpPort = 465,
        ),
        "fastmail.com" to ServerConfig(
            imapHost = "imap.fastmail.com",
            smtpHost = "smtp.fastmail.com",
        ),
    )

    fun detect(email: String): ServerConfig? {
        val domain = email.substringAfter("@").lowercase()
        return knownProviders[domain]
    }

}
