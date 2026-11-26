package com.divy.chhoti

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.divy.chhoti.ui.ChatScreen
import com.divy.chhoti.ui.ChhotiTheme
import com.divy.chhoti.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) vm.checkUpdate()

        setContent {
            ChhotiTheme {
                var screen by rememberSaveable { mutableStateOf(if (vm.prefs.apiKey.isBlank()) "settings" else "chat") }
                when (screen) {
                    "settings" -> {
                        BackHandler { screen = "chat" }
                        SettingsScreen(vm) { screen = "chat" }
                    }
                    else -> ChatScreen(vm) { screen = "settings" }
                }
            }
        }
    }
}
