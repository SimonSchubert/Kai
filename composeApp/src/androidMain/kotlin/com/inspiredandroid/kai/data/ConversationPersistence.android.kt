package com.inspiredandroid.kai.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.inspiredandroid.kai.db.KaiDatabase
import org.koin.java.KoinJavaComponent.inject

actual fun createConversationSqlDriver(): SqlDriver? {
    val context: Context by inject(Context::class.java)
    return AndroidSqliteDriver(KaiDatabase.Schema, context, "conversations.db")
}
