package com.wildlifespotter.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.pager.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalPagerApi::class)
@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {
    val pagerState = rememberPagerState()
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF2EA333), Color.Black))
            )
    ) {

        // Logo in alto a destra
        Image(
            painter = painterResource(id = R.drawable.whitelogo),
            contentDescription = "Logo",
            modifier = Modifier
                .width(187.dp)
                .height(115.dp)
                .align(Alignment.TopEnd)
                .padding(16.dp)  // distanza dai bordi
        )

        // Colonna principale centrata
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            HorizontalPager(
                count = onboardingPages.size,
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val item = onboardingPages[page]

                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.title,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraLight,
                        color = Color.White,
                        textAlign = TextAlign.Left
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = item.description,
                        fontSize = 16.sp,
                        color = Color.White,
                        textAlign = TextAlign.Left,
                        fontWeight = FontWeight.Light
                    )
                }
            }

            // Puntini indicatori
            HorizontalPagerIndicator(
                pagerState = pagerState,
                activeColor = Color(0xFF2EA333),
                inactiveColor = Color.White.copy(alpha = 0.5f),
                indicatorWidth = 8.dp,
                spacing = 8.dp,
                modifier = Modifier.padding(16.dp)
            )

            Button(
                onClick = {
                    if (pagerState.currentPage < onboardingPages.size - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onGetStarted()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = if (pagerState.currentPage == onboardingPages.size - 1) "Get Started" else "Continue"
                )
            }
        }
    }
}
