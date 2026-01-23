package com.inspiredandroid.kai

import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.compose.rememberNavController

fun MainViewController() = ComposeUIViewController {
    val navController = rememberNavController()
    App(
        navController = navController,
    )
}
