package com.inspiredandroid.kai

// For desktop, a common way is to use a system property.
// This can be set, for example, in the JVM arguments when running in debug mode: -Dkai.debug=true
actual val isDebugBuild: Boolean = System.getProperty("kai.debug", "false").toBoolean()
