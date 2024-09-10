package io.github.mavi.kover.plugin

import com.intellij.rt.coverage.aggregate.api.AggregatorApi
import com.intellij.rt.coverage.aggregate.api.Request
import com.intellij.rt.coverage.report.api.Filters
import com.intellij.rt.coverage.report.api.ReportApi
import io.github.mavi.kover.plugin.ReportType.HTML
import io.github.mavi.kover.plugin.ReportType.XML
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.io.File
import java.nio.charset.Charset
import java.util.regex.Pattern
import kotlin.io.path.exists

@Mojo(name = "report", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
class ReportMojo : AbstractKoverMojo() {
    /**
     * Comma-separated list of report types. Defaults to both HTML and XML.
     */
    @Parameter(property = "kover.reportFormats", defaultValue = "HTML,XML")
    internal val reportFormats = mutableSetOf(HTML, XML)

    override fun executeMojo() {
        if (!canExecute()) {
            log.info("Skipping Kover execution because no report file was found.")
            return
        }

        generateReports()
    }

    private fun canExecute(): Boolean = project.instrumentation().exists()

    private fun generateReports() {
        val filters =
            Filters(
                includesClasses.toList().asPatterns(),
                excludesClasses.toList().asPatterns(),
                excludesAnnotations.toList().asPatterns(),
                emptyList(),
                emptyList(),
                emptyList(),
            )

        aggregateRawReports(filters)

        val reports = listOf(project.aggregationInstrumentation().toFile())
        val outputRoots = listOf(File(project.build.outputDirectory))
        val sourceRoots =
            (
                setOf(project.build.sourceDirectory) +
                    project.build.resources
                        .map { it.directory }
                        .toSet()
            ).map(::File)

        if (reportFormats.contains(XML)) {
            ReportApi.xmlReport(
                project.xmlOutput().toFile(),
                "XML Report",
                reports,
                outputRoots,
                sourceRoots,
                filters,
            )
        }

        if (reportFormats.contains(HTML)) {
            ReportApi.htmlReport(
                project.htmlOutputDir().toFile(),
                "Kover Report",
                Charset.defaultCharset().name(),
                reports,
                outputRoots,
                sourceRoots,
                filters,
            )
        }

        if (reportFormats.contains(HTML)) {
            log.info("Kover: HTML report for '${project.name}' file://${project.htmlOutputDir()}/index.html")
        }
    }

    private fun aggregateRawReports(filters: Filters) {
        val aggregationGroups =
            listOf(
                AggregationGroup(
                    project.aggregationInstrumentation(),
                    project.aggregationMap(),
                ),
            )

        val requests =
            aggregationGroups.map { group ->
                Request(filters, group.ic.toFile(), group.smap.toFile())
            }

        AggregatorApi.aggregate(
            requests,
            listOf(project.instrumentation().toFile()),
            listOf(File(project.build.outputDirectory)),
        )
    }

    private fun List<String>.asPatterns(): List<Pattern> = map { Pattern.compile(it.wildcardsToRegex()) }
}
