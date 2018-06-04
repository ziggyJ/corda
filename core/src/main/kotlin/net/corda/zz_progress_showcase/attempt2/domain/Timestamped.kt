package net.corda.zz_progress_showcase.attempt2.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

interface Timestamped {

    val timestamp: Instant

    fun localTimestamp(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime = timestamp.localized(zoneId)
}

fun Instant.localized(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime = LocalDateTime.ofInstant(this, zoneId)