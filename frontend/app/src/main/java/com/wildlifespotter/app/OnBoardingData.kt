package com.wildlifespotter.app

data class OnboardingPage(
    val title: String,
    val description: String
)

val onboardingPages = listOf(
    OnboardingPage(
        title = "Step Outside and Turn Every Walk into an Adventure",
        description = "Get out, walk more, and explore the world around you. Our app motivates you to stay active by transforming your daily walks into exciting wildlife discovery journeys."
    ),
    OnboardingPage(
        title = "Spot Animals and Capture the Moment",
        description = "Encounter animals along your path and snap a photo when you see one. Each sighting becomes a personal memory you can revisit, helping you build a visual diary of your outdoor experiences."
    ),
    OnboardingPage(
        title = "Collect Memories and Compete with Others",
        description = "Keep track of all the animals you identify, relive your past sightings, and challenge others to see who can discover the most species. Walk more, explore more, and climb the leaderboard."
    )
)
