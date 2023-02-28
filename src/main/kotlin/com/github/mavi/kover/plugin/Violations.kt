package com.github.mavi.kover.plugin

import java.math.BigDecimal

internal data class RuleViolations(
    val bounds: List<BoundViolations>,
)

internal data class BoundViolations(
    val isMax: Boolean,
    val expectedValue: BigDecimal,
    val actualValue: BigDecimal,
    val metric: MetricType,
    val aggregation: AggregationType,
    val entityName: String? = null,
)
