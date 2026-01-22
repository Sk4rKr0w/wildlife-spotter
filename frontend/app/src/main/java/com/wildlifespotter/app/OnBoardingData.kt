package com.wildlifespotter.app

data class OnboardingPage(
    val title: String,
    val description: String
)

val onboardingPages = listOf(
    OnboardingPage(
        title = "Discover Wildlife Around You, One Sighting at a Time",
        description = "Join our community of explorers and help map wildlife in your area. Snap photos, record sightings, and track rare species directly from your smartphone. Every report contributes to protecting nature and supporting research."
    ),
    OnboardingPage(
        title = "Explore Interactive Maps and Hotspots",
        description = "Navigate detailed, interactive maps to discover wildlife hotspots near you. See recent sightings, explore natural habitats, and plan your next adventure with real-time data from the community."
    ),
    OnboardingPage(
        title = "Track Your Observations and Make an Impact",
        description = "Keep track of your wildlife sightings, view your personal statistics, and watch your contributions grow over time. Your data helps scientists, conservationists, and local communities protect biodiversity."
    )
)
