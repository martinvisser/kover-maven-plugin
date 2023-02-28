package com.github.mavi.kover.plugin

import com.github.mavi.kover.plugin.ReportType.HTML
import com.github.mavi.kover.plugin.ReportType.XML
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.io.FileWriter
import java.nio.file.Path
import kotlin.io.path.exists

@Mojo(name = "report", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
class ReportMojo : AbstractKoverMojo() {
    /**
     * Comma-separated list of report types. Defaults to both HTML and XML.
     */
    @Parameter(property = "kover.reportFormats", defaultValue = "HTML,XML")
    private val reportFormats = setOf(HTML, XML)

    override fun executeMojo() {
        if (!canExecute()) {
            log.info("Skipping Kover execution because property no report file was found.")
            return
        }

        generateReports()
    }

    private fun canExecute(): Boolean = project.instrumentation().exists()

    private fun generateReports() {
        aggregateRawReports()

        val aggregationReportFile = project.report()

        val aggregationReport = AggregationReport(
            reports = listOf(Report(project.aggregationInstrumentation(), project.aggregationMap())),
            modules = listOf(Module(setOf(project.build.sourceDirectory) + project.build.resources.map { it.directory }.toSet())),
            html = if (reportFormats.contains(HTML)) project.htmlOutputDir() else null,
            xml = if (reportFormats.contains(XML)) project.xmlOutput() else null,
        )

        objectMapper.writeValue(FileWriter(aggregationReportFile.toFile()), aggregationReport)

        com.intellij.rt.coverage.report.Main.main(arrayOf(aggregationReportFile.toString()))

        if (reportFormats.contains(HTML)) {
            log.info("Kover: HTML report for '${project.name}' file://${project.htmlOutputDir()}/index.html")
        }
    }

    private fun aggregateRawReports() {
        val aggregationRequestFile = project.aggregationRequest()

        val aggregationGroups = listOf(
            AggregationGroup(
                project.aggregationInstrumentation(),
                project.aggregationMap(),
            ),
        )

        writeAggregationJson(aggregationRequestFile, aggregationGroups)
        com.intellij.rt.coverage.aggregate.Main.main(arrayOf(aggregationRequestFile.toString()))
    }

    private fun writeAggregationJson(aggregationRequestFile: Path, groups: List<AggregationGroup>) {
        val aggregation = Aggregation(
            reports = listOf(Report(project.instrumentation())),
            modules = listOf(
                Module(
                    sources = setOf(project.build.sourceDirectory) + project.build.resources.map { it.directory }.toSet(),
                    output = setOf(project.build.outputDirectory),
                ),
            ),
            result = groups.map { group ->
                val includes = Filter(includesClasses.map(String::wildcardsToRegex))
                val excludes = Filter(
                    excludesClasses.map(String::wildcardsToRegex),
                    excludesAnnotations.map(String::wildcardsToRegex),
                )

                Result(
                    aggregatedReportFile = group.ic,
                    smapFile = group.smap,
                    filters = Filters(includes, excludes),
                )
            },
        )

        objectMapper.writeValue(FileWriter(aggregationRequestFile.toFile()), aggregation)
    }
}
