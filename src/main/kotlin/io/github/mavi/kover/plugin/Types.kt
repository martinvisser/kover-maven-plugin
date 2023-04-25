package io.github.mavi.kover.plugin

import java.math.BigDecimal
import java.nio.file.Path

class AggregationGroup(
    val ic: Path,
    val smap: Path,
)

data class AggregationReport(
    val format: String = "kover-agg",
    val title: String = "Kover Report",
    val reports: List<Report>,
    val modules: List<Module>,
    val html: Path?,
    val xml: Path?,
)

data class Report(
    val ic: Path,
    val smap: Path? = null,
)

data class Module(
    val sources: Set<String>,
    val output: Set<String> = emptySet(),
)

data class Aggregation(
    val reports: List<Report>,
    val modules: List<Module>,
    val result: List<Result>,
)

data class Result(
    val aggregatedReportFile: Path,
    val smapFile: Path,
    val filters: Filters,
)

data class Filters(
    val include: Filter? = null,
    val exclude: Filter? = null,
)

data class Filter(
    val classes: List<String> = emptyList(),
    val annotations: List<String> = emptyList(),
)

data class Verify(
    val resultFile: Path,
    val rules: List<VerificationRule>,
)

data class VerificationRule(
    val id: Int,
    val aggregatedReportFile: Path,
    val smapFile: Path,
    val targetType: String,
    val bounds: List<VerificationBound>,
)

data class VerificationBound(
    val id: Int,
    val counter: MetricType?,
    val valueType: String?,
    val min: BigDecimal?,
    val max: BigDecimal?,
)

data class Rule(
    var minValue: String? = null,
    var maxValue: String? = null,
    var metric: String? = null,
    var aggregation: String = AggregationType.COVERED_PERCENTAGE.name,
)

enum class MetricType {
    /**
     * Number of lines.
     */
    LINE,

    /**
     * Number of JVM bytecode instructions.
     */
    INSTRUCTION,

    /**
     * Number of branches covered.
     */
    BRANCH,
}

/**
 * Type of counter value to compare with minimal and maximal values if them defined.
 */
enum class AggregationType(val isPercentage: Boolean, val reporter: String) {
    COVERED_COUNT(false, "COVERED"),
    MISSED_COUNT(false, "MISSED"),
    COVERED_PERCENTAGE(true, "COVERED_RATE"),
    MISSED_PERCENTAGE(true, "MISSED_RATE"),
}
