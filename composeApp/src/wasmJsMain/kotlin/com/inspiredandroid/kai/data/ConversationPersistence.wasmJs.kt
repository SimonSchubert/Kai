package com.inspiredandroid.kai.data

import app.cash.sqldelight.db.SqlDriver

// The browser build keeps conversations in localStorage-backed settings: the
// sql.js web worker driver is in-memory only, so SQLite would lose data there.
actual fun createConversationSqlDriver(): SqlDriver? = null
