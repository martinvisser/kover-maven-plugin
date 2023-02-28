package com.github.mavi.kover.plugin

import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.rt.coverage.verify.Main
import org.apache.commons.lang3.math.NumberUtils
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.io.FileWriter
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.TreeMap
import kotlin.io.path.exists

@Mojo(name = "verify", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
class VerifyMojo : AbstractKoverMojo() {
    @Parameter(required = true)
    internal val rules = mutableListOf<Rule>()

    override fun executeMojo() {
        if (!canExecute()) {
            log.info("Skipping Kover execution because property no report file was found.")
            return
        }

        validateRules()
        verifyCoverage()
    }

    private fun canExecute(): Boolean = project.instrumentation().exists()

    private fun validateRules() {
        if (rules.isEmpty()) {
            throw MojoExecutionException("At least one rule needs to be defined")
        }

        rules.forEach { rule ->
            if (rule.metric == null || MetricType.values().none { rule.metric == it.name }) {
                throw MojoExecutionException(
                    "A rule needs to define a (valid) type of metric. " +
                        "Valid options: ${MetricType.values().joinToString(", ") { it.name }}.",
                )
            }
            if (AggregationType.values().none { rule.aggregation == it.name }) {
                throw MojoExecutionException(
                    "Invalid aggregation type '${rule.aggregation}' detected. " +
                        "Valid options: ${AggregationType.values().joinToString(", ") { it.name }}.",
                )
            }

            validateNumbers(rule)
        }
    }

    private fun validateNumbers(rule: Rule) {
        if (rule.minValue != null &&
            (!NumberUtils.isParsable(rule.minValue) || BigDecimal(rule.minValue).signum() == -1)
        ) {
            throw MojoExecutionException("'minValue' needs to be (positive) number")
        }

        if (rule.maxValue != null &&
            (!NumberUtils.isParsable(rule.maxValue) || BigDecimal(rule.maxValue).signum() == -1)
        ) {
            throw MojoExecutionException("'maxValue' needs to be (positive) number")
        }

        if (AggregationType.valueOf(rule.aggregation).isPercentage) {
            if (rule.minValue != null && BigDecimal(rule.minValue) > maxPercentage) {
                throw MojoExecutionException("'minValue' cannot be above 100%")
            }
            if (rule.maxValue != null && BigDecimal(rule.maxValue) > maxPercentage) {
                throw MojoExecutionException("'maxValue' cannot be above 100%")
            }
        }
    }

    private fun verifyCoverage() {
        val aggregationGroups = listOf(
            AggregationGroup(
                project.aggregationInstrumentation(),
                project.aggregationMap(),
            ),
        )
        val rulesByFilter = groupRules(aggregationGroups)

        val verifyRequestFile = project.verifyRequest()

        val verify = verifyRequest(rulesByFilter)

        objectMapper.writeValue(FileWriter(verifyRequestFile.toFile()), verify)

        Main.main(arrayOf(verifyRequestFile.toString()))

        processViolations()
    }

    private fun groupRules(aggregationGroups: List<AggregationGroup>): List<Pair<AggregationGroup, MutableList<Rule>>> =
        aggregationGroups.associateBy { it }.entries.map { it.key to rules }

    private fun verifyRequest(rulesByFilter: List<Pair<AggregationGroup, MutableList<Rule>>>) =
        Verify(
            resultFile = project.verifyResult(),
            rules = rulesByFilter.flatMapIndexed { index, pair ->
                pair.second.mapIndexed { ruleIndex, rule ->
                    val group = pair.first
                    VerificationRule(
                        id = ruleIndex,
                        aggregatedReportFile = group.ic,
                        smapFile = group.smap,
                        targetType = "ALL",
                        bounds = listOf(
                            VerificationBound(
                                id = index,
                                counter = rule.metric?.let(MetricType::valueOf),
                                valueType = AggregationType.valueOf(rule.aggregation).reporter,
                                min = rule.valueToReporter(rule.minValue),
                                max = rule.valueToReporter(rule.maxValue),
                            ),
                        ),
                    )
                }
            },
        )

    @Suppress("UNCHECKED_CAST", "ThrowsCount")
    private fun processViolations() {
        val violations = objectMapper.readValue<Map<String, Any>>(project.verifyResult().toFile())
        // the order of the rules is guaranteed for Kover (as in config)
        val result = TreeMap<Int, RuleViolations>()

        try {
            violations.forEach { (ruleIdString, boundViolations) ->
                val ruleIndex = ruleIdString.toInt()
                val boundsMap = rules.mapIndexed { index, rule -> index to rule }.associate { it }
                val bound = boundsMap[ruleIndex]
                    ?: throw MojoExecutionException(
                        "Error occurred while parsing verification result: unmapped rule with index $ruleIndex",
                    )

                val boundsResult = TreeMap<ViolationId, BoundViolations>()

                (boundViolations as Map<String, Map<String, Map<String, Any>>>).forEach { (boundIdString, v) ->
                    val boundIndex = boundIdString.toInt()

                    v["min"]?.map {
                        bound.minValue
                            ?: throw MojoExecutionException(
                                "Error occurred while parsing verification error: " +
                                    "no minimal bound with ID $boundIndex and rule index $ruleIndex",
                            )

                        addViolation(it, bound, boundsResult, boundIndex, false)
                    }

                    v["max"]?.map {
                        bound.maxValue
                            ?: throw MojoExecutionException(
                                "Error occurred while parsing verification error: " +
                                    "no maximal bound with index $boundIndex and rule index $ruleIndex",
                            )

                        addViolation(it, bound, boundsResult, boundIndex, true)
                    }
                }

                result += ruleIndex to RuleViolations(boundsResult.values.toList())
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            throw MojoExecutionException("Error occurred while parsing verifier result", e)
        }

        val ruleViolations = result.values.toList()
        if (ruleViolations.isNotEmpty()) {
            log.warn("Coverage checks have not been met, see log for details")
            throw MojoExecutionException(generateErrorMessage(ruleViolations))
        } else {
            log.info("All coverage checks have been met")
        }
    }

    private fun addViolation(
        outcome: Map.Entry<String, Any>,
        rule: Rule,
        boundsResult: TreeMap<ViolationId, BoundViolations>,
        boundIndex: Int,
        isMax: Boolean,
    ) {
        val entityName = outcome.key.ifEmpty { null }
        val rawValue = outcome.value
        val value = if (rawValue is String) rawValue.toBigDecimal() else rawValue as BigDecimal

        val aggregationType = AggregationType.valueOf(rule.aggregation)
        val actual = if (aggregationType.isPercentage) value * ONE_HUNDRED else value

        boundsResult += ViolationId(boundIndex, entityName) to BoundViolations(
            isMax,
            if (isMax) rule.maxValue!!.toBigDecimal() else rule.minValue!!.toBigDecimal(),
            actual,
            MetricType.valueOf(rule.metric!!),
            aggregationType,
            entityName,
        )
    }

    private fun generateErrorMessage(violations: List<RuleViolations>): String {
        val messageBuilder = StringBuilder()

        violations.forEach { rule ->
            val namedRule = "Rule"

            if (rule.bounds.size == 1) {
                messageBuilder.appendLine("$namedRule violated: ${rule.bounds[0].format()}")
            } else {
                messageBuilder.appendLine("$namedRule violated:")

                rule.bounds.forEach { bound ->
                    messageBuilder.append("  ").appendLine(bound.format())
                }
            }
        }

        return messageBuilder.toString()
    }

    private fun BoundViolations.format(): String {
        val directionText = if (isMax) "maximum" else "minimum"

        val metricText = when (metric) {
            MetricType.LINE -> "lines"
            MetricType.INSTRUCTION -> "instructions"
            MetricType.BRANCH -> "branches"
        }

        val valueTypeText = when (aggregation) {
            AggregationType.COVERED_COUNT -> "covered count"
            AggregationType.MISSED_COUNT -> "missed count"
            AggregationType.COVERED_PERCENTAGE -> "covered percentage"
            AggregationType.MISSED_PERCENTAGE -> "missed percentage"
        }

        return "$metricText $valueTypeText is $actualValue, but expected $directionText is $expectedValue"
    }

    private fun Rule.valueToReporter(value: String?): BigDecimal? =
        if (AggregationType.valueOf(aggregation).isPercentage) {
            value?.toBigDecimal()?.divide(ONE_HUNDRED, scale, RoundingMode.HALF_UP)
        } else {
            value?.toBigDecimal()
        }

    companion object {
        private val ONE_HUNDRED = 100.toBigDecimal()
        private const val scale = 6
        private val maxPercentage = BigDecimal(100)
    }
}

private data class ViolationId(val index: Int, val entityName: String?) : Comparable<ViolationId> {
    @Suppress("ReturnCount")
    override fun compareTo(other: ViolationId): Int {
        // first compared by index
        index.compareTo(other.index).takeIf { it != 0 }?.let { return it }

        // if indexes are equals then compare by entity name
        if (entityName == null) {
            // bounds with empty entity names goes first
            return if (other.entityName == null) 0 else -1
        }
        if (other.entityName == null) return 1

        entityName.compareTo(other.entityName).takeIf { it != 0 }?.let { return it }

        // indexes and names are equals
        return 0
    }
}
