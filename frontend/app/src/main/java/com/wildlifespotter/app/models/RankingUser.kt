package com.wildlifespotter.app.models

data class RankingUser(
    val id: String,
    val username: String,
    val spots: Long,
    val steps: Long,
    val globalRank: Int? = null
)
