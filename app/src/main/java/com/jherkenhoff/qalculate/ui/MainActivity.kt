package com.jherkenhoff.qalculate.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.jherkenhoff.qalculate.data.AutocompleteRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var autocompleteRepository: AutocompleteRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

    // Hide the status bar for fullscreen experience (modern way)
    val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
    controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
    controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            QalculateApp()
        }

        lifecycleScope.launch {
            autocompleteRepository.initialize()
        }
    }
}