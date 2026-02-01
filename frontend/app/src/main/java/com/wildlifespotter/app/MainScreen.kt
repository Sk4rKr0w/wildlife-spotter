package com.wildlifespotter.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.compose.ui.unit.dp
import com.wildlifespotter.app.models.AuthViewModel
import java.util.Locale

sealed class Screen(val route: String) {
    object Home : Screen("home_tab")
    object Add : Screen("add_tab")
    object MySpots : Screen("my_spots_tab")
    object Profile : Screen("profile_tab")
    object Rankings : Screen ("rankings_tab")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    var username by remember { mutableStateOf("") }
    var usernameError by remember { mutableStateOf(false) }
    var usernameTaken by remember { mutableStateOf(false) }
    var usernameChecking by remember { mutableStateOf(false) }
    var country by remember { mutableStateOf("") }
    var countryError by remember { mutableStateOf(false) }
    var countryExpanded by remember { mutableStateOf(false) }
    val needsCountry = authViewModel.countryName.isBlank()
    val countries = remember {
        Locale.getISOCountries()
            .map { code -> Locale("", code).displayCountry }
            .distinct()
            .sorted()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute !in listOf("steps_history", "spot_detail/{spotId}", "map_view")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomBar(navController)
            }
        }
    ) { paddingValues ->

        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToMap = {
                        navController.navigate("map_view")
                    },
                    onNavigateToHistory = {
                        navController.navigate("steps_history")
                    }
                )
            }

            composable("steps_history") {
                StepsHistoryScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable("map_view") {
                MapViewScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onSpotClick = { spotId ->
                        navController.navigate("spot_detail/$spotId")
                    }
                )
            }

            composable(Screen.Add.route) {
                AddSpotScreen()
            }

            composable(Screen.MySpots.route) {
                MySpotsScreen(
                    onNavigateToSpotDetail = { spotId ->
                        navController.navigate("spot_detail/$spotId")
                    }
                )
            }

            composable(Screen.Rankings.route) {
                RankingsScreen()
            }

            composable(Screen.Profile.route) {
                ProfileScreen(onLogout = onLogout)
            }

            composable("spot_detail/{spotId}") { backStackEntry ->
                val spotId = backStackEntry.arguments?.getString("spotId") ?: return@composable

                SpotDetailScreen(
                    spotId = spotId,
                    onNavigateBack = { navController.popBackStack() },
                    onSpotDeleted = { navController.popBackStack() }
                )
            }
        }
    }

    if (authViewModel.showGoogleProfileDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Welcome!") },
            text = {
                Column {
                    Text("Before you start, please set your username.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            usernameError = false
                            usernameTaken = false
                        },
                        label = { Text("Username") },
                        singleLine = true,
                        isError = usernameError || usernameTaken
                    )
                    if (usernameError) {
                        Text("Username cannot be empty", color = MaterialTheme.colorScheme.error)
                    }
                    if (usernameTaken) {
                        Text("Username already taken", color = MaterialTheme.colorScheme.error)
                    }
                    if (usernameChecking) {
                        Text("Checking username...")
                    }
                    if (needsCountry) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ExposedDropdownMenuBox(
                            expanded = countryExpanded,
                            onExpandedChange = { countryExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = country,
                                onValueChange = { },
                                label = { Text("Country") },
                                readOnly = true,
                                isError = countryError,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded)
                                },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = countryExpanded,
                                onDismissRequest = { countryExpanded = false }
                            ) {
                                countries.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item) },
                                        onClick = {
                                            country = item
                                            countryError = false
                                            countryExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        if (countryError) {
                            Text("Country not valid", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (username.isBlank()) {
                            usernameError = true
                            return@TextButton
                        }
                        val countryInput = if (needsCountry) country else authViewModel.countryName
                        if (authViewModel.toAlpha3Country(countryInput) == null) {
                            countryError = true
                            return@TextButton
                        }
                        usernameChecking = true
                        authViewModel.checkUsernameAvailable(username) { available, _ ->
                            usernameChecking = false
                            if (!available) {
                                usernameTaken = true
                                return@checkUsernameAvailable
                            }
                            authViewModel.completeGoogleProfile(username, countryInput)
                        }
                    }
                ) {
                    Text("Save")
                }
            }
        )
    }
}

@Composable
fun BottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Screen.Home.route,
            onClick = {
                navController.navigate(Screen.Home.route) {
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.Home, null) },
            label = { Text("Home") }
        )

        NavigationBarItem(
            selected = currentRoute == Screen.Add.route,
            onClick = {
                navController.navigate(Screen.Add.route) {
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.AddCircle, null) },
            label = { Text("Add") }
        )

        NavigationBarItem(
            selected = currentRoute == Screen.Rankings.route,
            onClick = {
                navController.navigate(Screen.Rankings.route) {
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.EmojiEvents, null) },
            label = { Text("Rankings") }
        )

        NavigationBarItem(
            selected = currentRoute == Screen.MySpots.route,
            onClick = {
                navController.navigate(Screen.MySpots.route) {
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.List, null) },
            label = { Text("My Spots") }
        )

        NavigationBarItem(
            selected = currentRoute == Screen.Profile.route,
            onClick = {
                navController.navigate(Screen.Profile.route) {
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.Person, null) },
            label = { Text("Profile") }
        )
    }
}