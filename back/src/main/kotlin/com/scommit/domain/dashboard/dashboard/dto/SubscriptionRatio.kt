package com.scommit.domain.dashboard.dashboard.dto

data class SubscriptionRatio(
    val followCount: Long,
    val membershipCount: Long,
    val followPercentage: Double,
    val membershipPercentage: Double,
)
