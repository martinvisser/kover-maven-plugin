package com.github.mavi.kover.plugin

import io.kotest.matchers.paths.exist
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import org.junit.jupiter.api.Test

class ReportMojoTest : BaseMojoTest() {
    private val mojo = ReportMojo()
        .apply {
            project = mavenProject
        }

    @Test
    fun `should skip generation if no report found`() {
        mojo.execute()

        mavenProject.report() shouldNot exist()
    }

    @Test
    fun `should generate reports`() {
        javaClass.getResourceAsStream("/test.ic")?.use {
            mavenProject.instrumentation().toFile().outputStream().use { output ->
                it.transferTo(output)
            }
        }

        mojo.execute()

        mavenProject.report() should exist()
    }
}
