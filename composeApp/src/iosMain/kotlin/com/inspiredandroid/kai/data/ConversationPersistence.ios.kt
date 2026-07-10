package com.inspiredandroid.kai.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.inspiredandroid.kai.db.KaiDatabase

actual fun createConversationSqlDriver(): SqlDriver? = NativeSqliteDriver(KaiDatabase.Schema, "conversations.db")
