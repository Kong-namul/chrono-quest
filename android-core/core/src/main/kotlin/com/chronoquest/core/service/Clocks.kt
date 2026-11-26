package com.chronoquest.core.service

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** 기기 로컬 벽시계 시각(초 단위). */
fun Clock.nowLocal(): LocalDateTime = LocalDateTime.now(this).truncatedTo(ChronoUnit.SECONDS)

fun Clock.today(): LocalDate = LocalDate.now(this)
