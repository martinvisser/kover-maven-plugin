package io.github.mavi.kover.plugin

import java.nio.file.Path

class AggregationGroup(
    val ic: Path,
    val smap: Path,
)

data class VerificationRule(
    var minValue: String? = null,
    var maxValue: String? = null,
    var metric: MetricType? = null,
    var aggregation: AggregationType = AggregationType.COVERED_PERCENTAGE,
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
enum class AggregationType(val isPercentage: Boolean) {
    COVERED_COUNT(false),
    MISSED_COUNT(false),
    COVERED_PERCENTAGE(true),
    MISSED_PERCENTAGE(true),
}
