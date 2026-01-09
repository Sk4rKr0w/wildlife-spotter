package com.wildlifespotter.app

import LoadingScreen
import com.wildlifespotter.app.SignIn
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wildlifespotter.app.ui.signup.SignUp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wildlifespotter.app.models.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation()
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val authViewModel: AuthViewModel = viewModel()

    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "loading") {

            // ----- LoadingScreen -----
            composable("loading") {
                LaunchedEffect(Unit) {
                    if (auth.currentUser != null) {
                        // Logged user
                        navController.navigate("home") {
                            popUpTo("loading") { inclusive = true }
                        }
                    } else {
                        navController.navigate("onboarding") {
                            popUpTo("loading") { inclusive = true }
                        }
                    }
                }

                LoadingScreen {  }
            }

            // ----- OnboardingScreen -----
            composable("onboarding") {
                OnboardingScreen {
                    navController.navigate("sign_in") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            }

            // ----- SignIn -----
            composable("sign_in") {
                val authViewModel: AuthViewModel = viewModel()

                LaunchedEffect(authViewModel.user) {
                    if (authViewModel.user != null) {
                        navController.navigate("home") {
                            popUpTo("sign_in") { inclusive = true }
                        }
                    }
                }

                SignIn(
                    authViewModel = authViewModel,
                    onSignInClick = { authViewModel.login() },
                    onCreateAccountClick = {
                        navController.navigate("sign_up")
                    }
                )
            }

            // ----- SignUp -----
            composable("sign_up") {
                LaunchedEffect(authViewModel.user) {
                    if (authViewModel.user != null) {
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                }

                SignUp(
                    authViewModel = authViewModel,
                    onBackToSignIn = {
                        navController.navigate("sign_in") {
                            popUpTo("sign_up") { inclusive = true }
                        }
                    },
                    onSignUpClick = { authViewModel.register() },
                )
            }

            // Home
            composable("home") {
                HomeScreen(
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate("sign_in") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
