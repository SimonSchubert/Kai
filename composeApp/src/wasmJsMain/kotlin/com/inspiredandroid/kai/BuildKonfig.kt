package com.inspiredandroid.kai

import kotlinx.browser.window

// For WasmJS, it's often harder to get a build-time debug flag.
// A common approach is to check the hostname or a global variable set during development.
// For simplicity, we can default to 'true' assuming development builds are more common for Wasm,
// or 'false' for safer production. Let's default to true for now, assuming it's for development.
// Alternatively, could use: actual val isDebugBuild: Boolean = true
actual val isDebugBuild: Boolean = window.location.hostname == "localhost" || window.location.hostname == "127.0.0.1"
