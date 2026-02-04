package com.wildlifespotter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wildlifespotter.app.ui.components.AnimatedWaveBackground
import com.wildlifespotter.app.ui.components.FloatingParticles
import java.time.format.DateTimeFormatter
import com.wildlifespotter.app.models.StepsHistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepsHistoryScreen(onBackClick: () -> Unit) {
    val viewModel: StepsHistoryViewModel = viewModel()
    val uiState = viewModel.uiState

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd-MM-yyyy") }
    val todayKey = remember { java.time.LocalDate.now().format(dateFormatter) }

    LaunchedEffect(Unit) {
        viewModel.loadHistory()
    }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A2332), Color(0xFF2D3E50), Color(0xFF1A2332))
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedWaveBackground(
            primaryColor = Color(0xFF4CAF50),
            secondaryColor = Color(0xFF2EA333)
        )

        FloatingParticles(particleCount = 10)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Steps History",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF374B5E).copy(alpha = 0.9f)
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF4CAF50))
                    }
                } else if (uiState.historySteps.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF374B5E).copy(alpha = 0.8f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(40.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No history available",
                                color = Color.Gray,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    val totalStepsAllTime = uiState.historySteps.values.sum()
                    val daysWithSteps = uiState.historySteps.filter { it.value > 0 }.size
                    val averageSteps = if (daysWithSteps > 0) totalStepsAllTime / daysWithSteps else 0L

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2D3E50).copy(alpha = 0.85f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                "Summary Statistics",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(16.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1A2332))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Total Days",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        daysWithSteps.toString(),
                                        color = Color(0xFF4CAF50),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Avg Steps",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        averageSteps.toString(),
                                        color = Color(0xFF4CAF50),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Total Steps",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        totalStepsAllTime.toString(),
                                        color = Color(0xFF4CAF50),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF374B5E).copy(alpha = 0.8f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                "Daily History",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )

                            Spacer(Modifier.height(16.dp))

                            uiState.historySteps
                                .toSortedMap(compareByDescending { it })
                                .forEach { (date, steps) ->
                                    val isToday = date == todayKey

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isToday) Color(0xFF2D3E50)
                                                else Color.Transparent
                                            )
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                if (isToday) "$date (Today)" else date,
                                                color = if (isToday) Color(0xFF4CAF50) else Color.LightGray,
                                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                        Text(
                                            "$steps steps",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }

                                    if (date != uiState.historySteps.keys.minOrNull()) {
                                        Divider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = Color.Gray.copy(alpha = 0.3f)
                                        )
                                    }
                                }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
