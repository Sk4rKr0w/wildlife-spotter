package com.wildlifespotter.app

import LoadingScreen
import com.wildlifespotter.app.SignIn
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wildlifespotter.app.ui.signup.SignUp

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

    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "loading") {

            // ----- LoadingScreen -----
            composable("loading") {
                LoadingScreen {
                    navController.navigate("onboarding") {
                        popUpTo("loading") { inclusive = true } // rimuove loading dalla backstack
                    }
                }
            }

            // ----- OnboardingScreen -----
            composable("onboarding") {
                OnboardingScreen {
                    navController.navigate("sign_in") {
                        popUpTo("onboarding") { inclusive = true } // rimuove onboarding dalla backstack
                    }
                }
            }

            // ----- SignIn -----
            composable("sign_in") {
                SignIn(
                    onSignInClick = {
                        // TODO: vai alla HomeScreen/Dashboard
                    },
                    onCreateAccountClick = {
                        navController.navigate("sign_up")
                    }
                )
            }

            // ----- SignUp -----
            composable("sign_up") {
                SignUp(
                    onBackToSignIn = {
                        navController.navigate("sign_in") {
                            popUpTo("sign_up") { inclusive = true } // rimuove SignUp dalla backstack
                        }
                    }
                )
            }
        }
    }
}
