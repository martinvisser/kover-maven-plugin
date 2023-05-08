package io.github.mavi.kover.plugin

import com.intellij.rt.coverage.verify.Verifier
import com.intellij.rt.coverage.verify.api.Bound
import com.intellij.rt.coverage.verify.api.BoundViolation
import com.intellij.rt.coverage.verify.api.Counter
import com.intellij.rt.coverage.verify.api.RuleViolation
import com.intellij.rt.coverage.verify.api.Target
import com.intellij.rt.coverage.verify.api.ValueType
import com.intellij.rt.coverage.verify.api.VerificationApi
import com.intellij.rt.coverage.verify.api.Violation
import org.apache.commons.lang3.math.NumberUtils
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.io.path.exists

@Mojo(name = "verify", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
class VerifyMojo : AbstractKoverMojo() {
    @Parameter(required = true)
    internal val rules = mutableListOf<VerificationRule>()

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
            if (rule.metric == null || MetricType.values().none { rule.metric == it }) {
                throw MojoExecutionException(
                    "A rule needs to define a (valid) type of metric. " +
                        "Valid options: ${MetricType.values().joinToString(", ") { it.name }}.",
                )
            }
            if (AggregationType.values().none { rule.aggregation == it }) {
                throw MojoExecutionException(
                    "Invalid aggregation type '${rule.aggregation}' detected. " +
                        "Valid options: ${AggregationType.values().joinToString(", ") { it.name }}.",
                )
            }

            validateNumbers(rule)
        }
    }

    private fun validateNumbers(rule: VerificationRule) {
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

        if (rule.aggregation.isPercentage) {
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

        val rulesArray = rulesByFilter.flatMapIndexed { index, pair ->
            pair.second.mapIndexed { ruleIndex, rule ->
                val group = pair.first
                val bound = Bound(
                    index,
                    rule.counterToReporter(),
                    rule.valueTypeToReporter(),
                    rule.valueToReporter(rule.minValue),
                    rule.valueToReporter(rule.maxValue),
                )
                com.intellij.rt.coverage.verify.api.Rule(ruleIndex, group.ic.toFile(), Target.ALL, listOf(bound))
            }
        }

        val verifier = Verifier(rulesArray)
        verifier.processRules()

        val violations = VerificationApi.verify(rulesArray)
        processViolations(violations)
    }

    private fun groupRules(aggregationGroups: List<AggregationGroup>): List<Pair<AggregationGroup, MutableList<VerificationRule>>> =
        aggregationGroups.associateBy { it }.entries.map { it.key to rules }

    private fun processViolations(violations: List<RuleViolation>) {
        val ruleViolations = runCatching {
            violations.map { violation ->
                val ruleIndex = violation.id
                val boundsMap = rules.mapIndexed { index, rule -> index to rule }.associate { it }
                val bound = boundsMap[ruleIndex]
                    ?: throw MojoExecutionException(
                        "Error occurred while parsing verification result: unmapped rule with index $ruleIndex",
                    )

                RuleViolations(violation.violations.flatMap { boundViolations(it, bound, ruleIndex) })
            }
        }.onFailure {
            throw MojoExecutionException("Error occurred while parsing verifier result", it)
        }.getOrThrow()

        if (ruleViolations.isNotEmpty()) {
            log.warn("Coverage checks have not been met, see log for details")
            throw MojoExecutionException(generateErrorMessage(ruleViolations))
        } else {
            log.info("All coverage checks have been met")
        }
    }

    private fun boundViolations(
        boundViolation: BoundViolation,
        bound: VerificationRule,
        ruleIndex: Int,
    ): List<BoundViolations> {
        val boundIndex = boundViolation.id

        val minViolations = boundViolation.minViolations.map {
            bound.minValue
                ?: throw MojoExecutionException(
                    "Error occurred while parsing verification error: " +
                        "no minimal bound with ID $boundIndex and rule index $ruleIndex",
                )

            boundViolation(it, bound, false)
        }

        val maxViolations = boundViolation.maxViolations.map {
            bound.maxValue
                ?: throw MojoExecutionException(
                    "Error occurred while parsing verification error: " +
                        "no maximal bound with index $boundIndex and rule index $ruleIndex",
                )

            boundViolation(it, bound, true)
        }

        return minViolations + maxViolations
    }

    private fun boundViolation(
        violation: Violation,
        rule: VerificationRule,
        isMax: Boolean,
    ): BoundViolations {
        val entityName = violation.targetName.ifEmpty { null }
        val value = violation.targetValue
        val actual = if (rule.aggregation.isPercentage) value * ONE_HUNDRED else value

        return BoundViolations(
            isMax,
            if (isMax) rule.maxValue!!.toBigDecimal() else rule.minValue!!.toBigDecimal(),
            actual,
            rule.metric!!,
            rule.aggregation,
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

                rule.bounds.forEach {
                    messageBuilder.append("  ").appendLine(it.format())
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

    private fun VerificationRule.counterToReporter(): Counter =
        when (metric!!) {
            MetricType.LINE -> Counter.LINE
            MetricType.INSTRUCTION -> Counter.INSTRUCTION
            MetricType.BRANCH -> Counter.BRANCH
        }

    private fun VerificationRule.valueTypeToReporter(): ValueType =
        when (aggregation) {
            AggregationType.COVERED_COUNT -> ValueType.COVERED
            AggregationType.MISSED_COUNT -> ValueType.MISSED
            AggregationType.COVERED_PERCENTAGE -> ValueType.COVERED_RATE
            AggregationType.MISSED_PERCENTAGE -> ValueType.MISSED_RATE
        }

    private fun VerificationRule.valueToReporter(value: String?): BigDecimal? =
        if (aggregation.isPercentage) {
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
