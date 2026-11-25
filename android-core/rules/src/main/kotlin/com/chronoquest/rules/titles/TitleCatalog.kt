package com.chronoquest.rules.titles

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CatalogFile(
    val thresholdSets: Map<String, List<Int>>,
    val series: List<SeriesEntry>,
)

@Serializable
private data class SeriesEntry(
    val key: String,
    val firstId: Int,
    val thresholds: String,
    val names: List<String>,
)

/** 룰북 칭호 200종과 지급 규칙(룰북 03-02). */
class TitleCatalog(val titles: List<Title>) {
    private val byId: Map<Int, Title> = titles.associateBy { it.id }
    private val bySeries: Map<Series, List<Title>> = titles.groupBy { it.series }

    fun title(id: Int): Title = byId[id] ?: error("Unknown title id: $id")

    fun series(series: Series): List<Title> = bySeries[series].orEmpty()

    /** 누적 계열: 카운터가 [count]가 된 순간 지급할 칭호. 단계의 배수마다 지급한다. */
    fun cumulativeAwards(series: Series, count: Int): List<Title> {
        require(series.kind == SeriesKind.CUMULATIVE) { "$series is not cumulative" }
        if (count <= 0) return emptyList()
        return series(series).filter { count % it.threshold == 0 }
    }

    /** 연속 계열: 연속 값이 [streak]가 된 순간 지급할 칭호. 정확히 그 단계만 지급한다. */
    fun streakAwards(series: Series, streak: Int): List<Title> {
        require(series.kind == SeriesKind.STREAK) { "$series is not a streak series" }
        if (streak <= 0) return emptyList()
        return series(series).filter { streak == it.threshold }
    }

    companion object {
        private const val RESOURCE = "/com/chronoquest/rules/titles.json"

        fun load(): TitleCatalog {
            val text = TitleCatalog::class.java.getResourceAsStream(RESOURCE)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: error("Missing resource $RESOURCE")
            return parse(text)
        }

        fun parse(text: String): TitleCatalog {
            val file = Json.decodeFromString(CatalogFile.serializer(), text)
            val titles = file.series.flatMap { entry ->
                val series = Series.valueOf(entry.key)
                val thresholds = file.thresholdSets[entry.thresholds]
                    ?: error("Unknown threshold set ${entry.thresholds}")
                require(entry.names.size == 20) { "${entry.key} must have 20 names" }
                require(thresholds.size == 20) { "${entry.thresholds} must have 20 thresholds" }
                entry.names.mapIndexed { i, name ->
                    Title(id = entry.firstId + i, series = series, threshold = thresholds[i], name = name)
                }
            }
            require(titles.map { it.id }.toSet().size == titles.size) { "Duplicate title ids" }
            return TitleCatalog(titles)
        }
    }
}
