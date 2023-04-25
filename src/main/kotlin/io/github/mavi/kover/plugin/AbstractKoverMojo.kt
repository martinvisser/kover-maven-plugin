package io.github.mavi.kover.plugin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.intellij.rt.coverage.util.ErrorReporter
import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.nio.file.Path

abstract class AbstractKoverMojo : AbstractMojo() {
    @Parameter(defaultValue = "\${project}", required = true, readonly = true)
    internal lateinit var project: MavenProject

    @Parameter(property = "kover.skip", defaultValue = "false")
    internal val skip = false

    /**
     * Map of plugin artifacts.
     */
    @Parameter(property = "plugin.artifactMap", required = true, readonly = true)
    internal lateinit var pluginArtifactMap: Map<String, Artifact>

    /**
     * Comma-separated list of classes to include. E.g. com.example.*. Defaults to all.
     */
    @Parameter(property = "kover.includesClasses")
    internal val includesClasses = mutableSetOf<String>()

    /**
     * Comma-separated list of classes to exclude. E.g. com.example.*. Defaults to none.
     */
    @Parameter(property = "kover.excludesClasses")
    internal val excludesClasses = mutableSetOf<String>()

    /**
     * Comma-separated list of annotations to exclude. E.g. com.example.*. Defaults to none.
     */
    @Parameter(property = "kover.excludesAnnotations")
    internal val excludesAnnotations = mutableSetOf<String>()

    override fun execute() {
        if (skip) {
            log.info("Skipping Kover execution because property kover.skip is set.")
            return
        }

        ErrorReporter.setBasePath(project.build.directory)
        executeMojo()
    }

    internal abstract fun executeMojo()

    companion object {
        internal val objectMapper = jacksonMapperBuilder()
            .serializationInclusion(JsonInclude.Include.NON_EMPTY)
            .addModule(SimpleModule("PathToString").apply { addSerializer(Path::class.java, ToStringSerializer()) })
            .build()
    }
}
