package com.wildlifespotter.app

import LoadingScreen
import android.Manifest
import android.os.Build
import com.wildlifespotter.app.SignIn
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
            val context = LocalContext.current

            val permissionsToRequest = remember {
                val list = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    list.add(Manifest.permission.ACTIVITY_RECOGNITION)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    list.add(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                list.toTypedArray()
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val activityRecognitionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    permissions[Manifest.permission.ACTIVITY_RECOGNITION] == true
                } else true

                if (!activityRecognitionGranted) {
                    Toast.makeText(context, "Without 'Physical Activity' permission, steps will not be counted.", Toast.LENGTH_LONG).show()
                }
            }

            LaunchedEffect(Unit) {
                permissionLauncher.launch(permissionsToRequest)
            }

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

            // ----- Home -----
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
