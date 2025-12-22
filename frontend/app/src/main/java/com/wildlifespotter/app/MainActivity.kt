package com.wildlifespotter.app

import LoadingScreen
import com.wildlifespotter.app.SignIn
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var showLoading by remember { mutableStateOf(true) }
            var showOnboarding by remember { mutableStateOf(false) }

            if (showLoading) {
                LoadingScreen {
                    showLoading = false
                    showOnboarding = true
                }
            } else if (showOnboarding) {
                OnboardingScreen {
                    // qui poi andrai alla HomeScreen (Dashboard)
                    showOnboarding = false
                }
            } else {
                SignIn()
            }
        }
    }
}
