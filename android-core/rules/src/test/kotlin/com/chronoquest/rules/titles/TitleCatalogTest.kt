package com.chronoquest.rules.titles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TitleCatalogTest {
    private val catalog = TitleCatalog.load()

    @Test
    fun `칭호는 200종이고 ID는 1부터 200까지다`() {
        assertEquals((1..200).toList(), catalog.titles.map { it.id }.sorted())
    }

    @Test
    fun `계열별 첫 칭호와 마지막 칭호가 룰북과 같다`() {
        assertEquals("최초의 각성", catalog.title(1).name)
        assertEquals("시간축의 지배자", catalog.title(20).name)
        assertEquals("운명의 첫걸음", catalog.title(21).name)
        assertEquals("경계를 넘는 자", catalog.title(41).name)
        assertEquals("미래의 방문자", catalog.title(61).name)
        assertEquals("잠에서 깨어난 자", catalog.title(81).name)
        assertEquals("유혹을 지나친 자", catalog.title(101).name)
        assertEquals("일곱 날의 승리", catalog.title(121).name)
        assertEquals("첫 번째 왕관", catalog.title(141).name)
        assertEquals("완벽의 신화, 크로노스", catalog.title(160).name)
        assertEquals("잿더미에서 일어난 자", catalog.title(161).name)
        assertEquals("달빛의 개척자", catalog.title(181).name)
        assertEquals("크로노스의 화신", catalog.title(200).name)
    }

    @Test
    fun `칭호 계열과 단계 값이 룰북 표와 같다`() {
        assertEquals(Series.ARRIVE_STREAK, catalog.title(30).series)
        assertEquals(200, catalog.title(20).threshold)
        assertEquals(12, catalog.title(31).threshold)
        assertEquals(14, catalog.title(132).threshold)
        assertEquals(52, catalog.title(140).threshold)
        assertEquals(20, catalog.title(180).threshold)
        assertEquals(11, catalog.title(191).threshold)
    }

    @Test
    fun `누적 계열은 카운트가 단계의 배수일 때마다 지급한다`() {
        assertEquals(listOf(1), catalog.cumulativeAwards(Series.ARRIVE_TOTAL, 1).map { it.id })
        assertEquals(listOf(1, 5), catalog.cumulativeAwards(Series.ARRIVE_TOTAL, 5).map { it.id })
        assertEquals(listOf(1, 2, 5, 10), catalog.cumulativeAwards(Series.ARRIVE_TOTAL, 10).map { it.id })
        assertEquals(listOf(1, 2, 3, 4, 6, 11), catalog.cumulativeAwards(Series.ARRIVE_TOTAL, 12).map { it.id })
    }

    @Test
    fun `연속 계열은 정확히 그 단계에서만 지급한다`() {
        assertEquals(listOf(21), catalog.streakAwards(Series.ARRIVE_STREAK, 1).map { it.id })
        assertEquals(listOf(31), catalog.streakAwards(Series.ARRIVE_STREAK, 12).map { it.id })
        assertEquals(emptyList<Int>(), catalog.streakAwards(Series.ARRIVE_STREAK, 11).map { it.id })
    }

    @Test
    fun `0 이하 카운트는 지급하지 않는다`() {
        assertEquals(emptyList<Int>(), catalog.cumulativeAwards(Series.WAKE_TOTAL, 0).map { it.id })
        assertEquals(emptyList<Int>(), catalog.streakAwards(Series.PERFECT_WEEK_STREAK, 0).map { it.id })
    }

    @Test
    fun `계열 종류가 맞지 않으면 예외`() {
        assertFailsWith<IllegalArgumentException> { catalog.cumulativeAwards(Series.ARRIVE_STREAK, 1) }
        assertFailsWith<IllegalArgumentException> { catalog.streakAwards(Series.ARRIVE_TOTAL, 1) }
    }
}
