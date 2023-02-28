package com.github.mavi.kover.plugin

import com.github.mavi.kover.plugin.AgentMojo.Companion.AGENT_ARTIFACT_NAME
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.readLines

class AgentMojoTest : BaseMojoTest() {
    private val mojo = AgentMojo()
        .apply {
            project = mavenProject
            pluginArtifactMap = mapOf(
                AGENT_ARTIFACT_NAME to mockk {
                    every { file } returns File("/agent.jar")
                },
            )
        }

    @Test
    fun `should write argLine`() {
        mojo.execute()

        mavenProject.properties["argLine"] shouldBe "-javaagent:/agent.jar=target/test/tmp/kover-agent.args " +
            "-Didea.new.tracing.coverage=true -Didea.coverage.log.level=error " +
            "-Dcoverage.ignore.private.constructor.util.class=true -Didea.coverage.calculate.hits=false"

        mavenProject.argsFile().readLines() shouldContainExactly
            listOf(
                "target/test/kover/test.ic",
                "false",
                "false",
                "true",
                "false",
            )
    }

    @Test
    fun `should write argLine with excludes`() {
        mojo.excludesClasses.add("com.example.**")
        mojo.execute()

        mavenProject.properties["argLine"] shouldBe "-javaagent:/agent.jar=target/test/tmp/kover-agent.args " +
            "-Didea.new.tracing.coverage=true -Didea.coverage.log.level=error " +
            "-Dcoverage.ignore.private.constructor.util.class=true -Didea.coverage.calculate.hits=false"

        mavenProject.argsFile().readLines() shouldContainExactly
            listOf(
                "target/test/kover/test.ic",
                "false",
                "false",
                "true",
                "false",
                "-exclude",
                "com\\.example\\..*.*",
            )
    }
}
