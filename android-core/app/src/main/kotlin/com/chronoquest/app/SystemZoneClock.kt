package com.chronoquest.app

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** 호출할 때마다 기기의 현재 타임존을 읽는 시계. 타임존이 바뀌어도 다시 만들 필요가 없다(인계 메모 A2b "시계"). */
class SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)

    override fun instant(): Instant = Instant.now()
}
