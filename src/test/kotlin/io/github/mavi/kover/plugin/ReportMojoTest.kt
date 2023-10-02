package io.github.mavi.kover.plugin

import io.kotest.matchers.paths.exist
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

@Suppress("ktlint:standard:function-naming")
class ReportMojoTest : BaseMojoTest() {
    fun `test should skip generation if no report found`() {
        val mojo = mojo<ReportMojo>("report")

        mojo.execute()

        session.currentProject.report() shouldNot exist()
    }

    fun `test should generate reports`() {
        val mojo = mojo<ReportMojo>("report")
        javaClass.getResourceAsStream("/test.ic")?.use {
            session.currentProject.instrumentation().toFile().outputStream().use { output ->
                it.transferTo(output)
            }
        }

        mojo.execute()

        session.currentProject.htmlOutputDir().resolve("index.html") should exist()
        session.currentProject.xmlOutput() should exist()
    }

    fun `test should generate html report`() {
        val mojo = mojo<ReportMojo>("report")
        mojo.reportFormats.clear()
        mojo.reportFormats.add(ReportType.HTML)

        javaClass.getResourceAsStream("/test.ic")?.use {
            session.currentProject.instrumentation().toFile().outputStream().use { output ->
                it.transferTo(output)
            }
        }

        mojo.execute()

        session.currentProject.htmlOutputDir().resolve("index.html") should exist()
        session.currentProject.xmlOutput() shouldNot exist()
    }

    fun `test should generate xml report`() {
        val mojo = mojo<ReportMojo>("report")
        mojo.reportFormats.clear()
        mojo.reportFormats.add(ReportType.XML)

        javaClass.getResourceAsStream("/test.ic")?.use {
            session.currentProject.instrumentation().toFile().outputStream().use { output ->
                it.transferTo(output)
            }
        }

        mojo.execute()

        session.currentProject.htmlOutputDir().resolve("index.html") shouldNot exist()
        session.currentProject.xmlOutput() should exist()
    }
}
