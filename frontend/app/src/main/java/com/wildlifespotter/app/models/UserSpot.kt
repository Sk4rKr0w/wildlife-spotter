package com.wildlifespotter.app.models

import com.google.firebase.Timestamp

data class UserSpot(
    val id: String,
    val speciesLabel: String,
    val speciesTaxonomy: Map<String, Any?>,
    val description: String,
    val locationName: String,
    val imageId: String,
    val userId: String,
    val timestamp: Timestamp?,
    val dailySteps: Long = 0L
)
