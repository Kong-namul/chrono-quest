package com.chronoquest.rules.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TierTest {
    @Test
    fun `누적 XP로 티어가 정해진다`() {
        assertEquals(Tier.BRONZE, Tier.of(0))
        assertEquals(Tier.BRONZE, Tier.of(99))
        assertEquals(Tier.SILVER, Tier.of(100))
        assertEquals(Tier.DIAMOND, Tier.of(2999))
        assertEquals(Tier.MASTER, Tier.of(3000))
        assertEquals(Tier.MYTHIC, Tier.of(10000))
        assertEquals(Tier.MYTHIC, Tier.of(99999))
    }

    @Test
    fun `다음 티어와 대표 칭호`() {
        assertEquals(Tier.SILVER, Tier.BRONZE.next)
        assertNull(Tier.MYTHIC.next)
        assertEquals("시간의 지배자", Tier.MASTER.title)
    }
}
