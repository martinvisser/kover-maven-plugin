package io.github.mavi.kover.plugin

import com.intellij.rt.coverage.util.ErrorReporter
import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

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
}
