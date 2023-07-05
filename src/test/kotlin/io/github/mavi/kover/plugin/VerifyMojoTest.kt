package io.github.mavi.kover.plugin

import io.github.mavi.kover.plugin.AggregationType.COVERED_COUNT
import io.github.mavi.kover.plugin.AggregationType.COVERED_PERCENTAGE
import io.github.mavi.kover.plugin.MetricType.BRANCH
import io.github.mavi.kover.plugin.MetricType.LINE
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.paths.exist
import io.kotest.matchers.shouldNot
import io.kotest.matchers.throwable.shouldHaveMessage
import org.apache.maven.plugin.MojoExecutionException
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class VerifyMojoTest : BaseMojoTest() {
    fun `test should skip generation if no report found`() {
        val mojo = mojo<VerifyMojo>("verify")

        mojo.execute()

        session.currentProject.report() shouldNot exist()
    }

    fun `test should fail if no rules are configured`() {
        val mojo = mojo<VerifyMojo>("verify")

        prepareCoverageFiles()

        shouldThrow<MojoExecutionException> {
            mojo.execute()
        } shouldHaveMessage "At least one rule needs to be defined"
    }

    @ParameterizedTest
    @MethodSource("incorrectRules")
    fun `should fail if incorrect rule`(rule: VerificationRule, message: String) {
        super.setUp()
        val mojo = mojo<VerifyMojo>("verify")

        prepareCoverageFiles()

        mojo.rules.add(rule)

        shouldThrow<MojoExecutionException> { mojo.execute() } shouldHaveMessage message
    }

    fun `test should verify rules`() {
        val mojo = mojo<VerifyMojo>("verify")

        prepareCoverageFiles()

        mojo.rules.add(
            VerificationRule(
                metric = BRANCH,
                minValue = "42",
                aggregation = COVERED_PERCENTAGE,
            ),
        )

        shouldNotThrowAny { mojo.execute() }
    }

    fun `test should fail on violations`() {
        val mojo = mojo<VerifyMojo>("verify")

        prepareCoverageFiles()

        mojo.rules.add(
            VerificationRule(
                metric = BRANCH,
                minValue = "5000",
                aggregation = COVERED_COUNT,
            ),
        )
        mojo.rules.add(
            VerificationRule(
                metric = LINE,
                maxValue = "10",
                aggregation = COVERED_PERCENTAGE,
            ),
        )

        shouldThrow<MojoExecutionException> { mojo.execute() } shouldHaveMessage
            "Rule violated: branches covered count is 80, but expected minimum is 5000\n" +
            "Rule violated: lines covered percentage is 88.679200, but expected maximum is 10"
    }

    private fun prepareCoverageFiles() {
        javaClass.getResourceAsStream("/test.ic")?.use {
            session.currentProject.instrumentation().toFile().outputStream().use { output ->
                it.transferTo(output)
            }
        }
        javaClass.getResourceAsStream("/agg-ic.ic")?.use {
            session.currentProject.aggregationInstrumentation().toFile().outputStream().use { output ->
                it.transferTo(output)
            }
        }
        javaClass.getResourceAsStream("/agg-smap.smap")?.use {
            session.currentProject.aggregationMap().toFile().outputStream().use { output ->
                it.transferTo(output)
            }
        }
    }

    companion object {
        @JvmStatic
        fun incorrectRules(): List<Arguments> = listOf(
            Arguments.of(
                VerificationRule(),
                "A rule needs to define a (valid) type of metric. Valid options: LINE, INSTRUCTION, BRANCH.",
            ),
            Arguments.of(
                VerificationRule(metric = BRANCH, minValue = "abc"),
                "'minValue' needs to be (positive) number",
            ),
            Arguments.of(
                VerificationRule(metric = BRANCH, minValue = "-1"),
                "'minValue' needs to be (positive) number",
            ),
            Arguments.of(
                VerificationRule(metric = BRANCH, maxValue = "abc"),
                "'maxValue' needs to be (positive) number",
            ),
            Arguments.of(
                VerificationRule(metric = BRANCH, maxValue = "-1"),
                "'maxValue' needs to be (positive) number",
            ),
            Arguments.of(
                VerificationRule(metric = BRANCH, minValue = "101"),
                "'minValue' cannot be above 100%",
            ),
            Arguments.of(
                VerificationRule(metric = BRANCH, maxValue = "101"),
                "'maxValue' cannot be above 100%",
            ),
        )
    }
}
