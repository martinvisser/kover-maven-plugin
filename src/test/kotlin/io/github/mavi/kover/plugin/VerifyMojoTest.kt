package io.github.mavi.kover.plugin

import io.github.mavi.kover.plugin.AggregationType.COVERED_COUNT
import io.github.mavi.kover.plugin.AggregationType.COVERED_PERCENTAGE
import io.github.mavi.kover.plugin.MetricType.BRANCH
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.paths.exist
import io.kotest.matchers.should
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

        javaClass.getResourceAsStream("/test.ic")?.use {
            session.currentProject.instrumentation().toFile().outputStream().use { output ->
                it.transferTo(output)
            }
        }

        shouldThrow<MojoExecutionException> { mojo.execute() } shouldHaveMessage "At least one rule needs to be defined"
    }

    @ParameterizedTest
    @MethodSource("incorrectRules")
    fun `should fail if incorrect rule`(rule: Rule, message: String) {
        super.setUp()
        val mojo = mojo<VerifyMojo>("verify")

        javaClass.getResourceAsStream("/test.ic")?.use {
            session.currentProject.instrumentation().toFile().outputStream().use { output ->
                it.transferTo(output)
            }
        }

        mojo.rules.add(rule)

        shouldThrow<MojoExecutionException> { mojo.execute() } shouldHaveMessage message
    }

    fun `test should verify rules`() {
        val mojo = mojo<VerifyMojo>("verify")

        javaClass.getResourceAsStream("/test.ic")?.use {
            session.currentProject.instrumentation().toFile().outputStream().use { output ->
                it.transferTo(output)
            }
        }

        mojo.rules.add(
            Rule(
                metric = BRANCH.name,
                minValue = "42",
                aggregation = COVERED_PERCENTAGE.name,
            ),
        )

        shouldNotThrowAny { mojo.execute() }
        session.currentProject.verifyResult() should exist()
    }

    fun `test should fail on violations`() {
        val mojo = mojo<VerifyMojo>("verify")

        javaClass.getResourceAsStream("/test.ic")?.use {
            session.currentProject.instrumentation().toFile().outputStream().use { output ->
                it.transferTo(output)
            }
        }

        mojo.rules.add(
            Rule(
                metric = BRANCH.name,
                minValue = "5000",
                aggregation = COVERED_COUNT.name,
            ),
        )

        shouldThrow<MojoExecutionException> { mojo.execute() } shouldHaveMessage
            "Rule violated: branches covered count is 0, but expected minimum is 5000"
        session.currentProject.verifyResult() should exist()
    }

    companion object {
        @JvmStatic
        fun incorrectRules(): List<Arguments> = listOf(
            Arguments.of(
                Rule(),
                "A rule needs to define a (valid) type of metric. Valid options: LINE, INSTRUCTION, BRANCH.",
            ),
            Arguments.of(
                Rule(metric = "dummy"),
                "A rule needs to define a (valid) type of metric. Valid options: LINE, INSTRUCTION, BRANCH.",
            ),
            Arguments.of(
                Rule(metric = BRANCH.name, aggregation = "dummy"),
                "Invalid aggregation type 'dummy' detected. " +
                    "Valid options: COVERED_COUNT, MISSED_COUNT, COVERED_PERCENTAGE, MISSED_PERCENTAGE.",
            ),
            Arguments.of(Rule(metric = BRANCH.name, minValue = "abc"), "'minValue' needs to be (positive) number"),
            Arguments.of(Rule(metric = BRANCH.name, minValue = "-1"), "'minValue' needs to be (positive) number"),
            Arguments.of(Rule(metric = BRANCH.name, maxValue = "abc"), "'maxValue' needs to be (positive) number"),
            Arguments.of(Rule(metric = BRANCH.name, maxValue = "-1"), "'maxValue' needs to be (positive) number"),
            Arguments.of(Rule(metric = BRANCH.name, minValue = "101"), "'minValue' cannot be above 100%"),
            Arguments.of(Rule(metric = BRANCH.name, maxValue = "101"), "'maxValue' cannot be above 100%"),
        )
    }
}
