package io.github.mavi.kover.plugin

import org.apache.maven.project.MavenProject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private const val TEMP_DIR = "tmp"

private const val MAIN_DIR = "kover"

internal fun MavenProject.argsFile(): Path =
    Paths.get(build.directory, TEMP_DIR, "kover-agent.args")
        .also { Files.createDirectories(it.parent) }

internal fun MavenProject.aggregationInstrumentation(): Path =
    Paths.get(build.directory, TEMP_DIR, MAIN_DIR, "agg-ic.ic")
        .also { Files.createDirectories(it.parent) }

internal fun MavenProject.aggregationMap(): Path =
    Paths.get(build.directory, TEMP_DIR, MAIN_DIR, "agg-smap.smap")
        .also { Files.createDirectories(it.parent) }

internal fun MavenProject.aggregationRequest(): Path =
    Paths.get(build.directory, TEMP_DIR, MAIN_DIR, "agg-request.json")
        .also { Files.createDirectories(it.parent) }

internal fun MavenProject.htmlOutputDir(): Path =
    Paths.get(model.reporting.outputDirectory, MAIN_DIR, "html")
        .also { Files.createDirectories(it) }

internal fun MavenProject.instrumentation(): Path =
    Paths.get(build.directory, MAIN_DIR, "test.ic")
        .also { Files.createDirectories(it.parent) }

internal fun MavenProject.report(): Path =
    Paths.get(build.directory, TEMP_DIR, MAIN_DIR, "intellijreport.json")
        .also { Files.createDirectories(it.parent) }

internal fun MavenProject.verifyRequest(): Path =
    Paths.get(build.directory, TEMP_DIR, MAIN_DIR, "verify-request.json")
        .also { Files.createDirectories(it.parent) }

internal fun MavenProject.verifyResult(): Path =
    Paths.get(build.directory, TEMP_DIR, MAIN_DIR, "verify-result.json")
        .also { Files.createDirectories(it.parent) }

internal fun MavenProject.xmlOutput(): Path =
    Paths.get(model.reporting.outputDirectory, MAIN_DIR, "xml", "report.xml")
        .also { Files.createDirectories(it.parent) }

/**
 * Replaces characters `*` or `.` to `.*` and `.` regexp characters.
 */
internal fun String.wildcardsToRegex(): String {
    // in most cases, the characters `*` or `.` will be present therefore, we increase the capacity in advance
    val builder = StringBuilder(length * 2)

    forEach { char ->
        when (char) {
            in regexMetacharactersSet -> builder.append('\\').append(char)
            '*' -> builder.append('.').append("*")
            '?' -> builder.append('.')
            else -> builder.append(char)
        }
    }

    return builder.toString()
}

private val regexMetacharactersSet = "<([{\\^-=$!|]})+.>".toSet()
