package io.github.mavi.kover.plugin

import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.ResolutionScope
import java.nio.file.Path
import kotlin.io.path.writeLines

@Mojo(name = "prepare-agent", defaultPhase = LifecyclePhase.INITIALIZE, requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
class AgentMojo : AbstractKoverMojo() {
    override fun executeMojo() {
        val projectProperties = project.properties

        projectProperties["argLine"] = buildJvmAgentArgs()
        log.info("argLine set to ${projectProperties["argLine"]}")
    }

    private fun buildJvmAgentArgs(): String {
        val argsFile = project.argsFile()
        argsFile.writeAgentArgs(project.instrumentation(), excludesClasses)

        return mutableListOf(
            "-javaagent:${pluginArtifactMap[AGENT_ARTIFACT_NAME]!!.file.canonicalPath}=${argsFile.normalize()}",
            "-D$ENABLE_TRACING",
            "-D$PRINT_ONLY_ERRORS",
            "-D$IGNORE_STATIC_CONSTRUCTORS",
            "-D$DO_NOT_COUNT_HIT_AMOUNT",
        ).joinToString(" ")
    }

    private fun Path.writeAgentArgs(instrumentationFile: Path, excludedClasses: Set<String>) {
        val lines = mutableListOf(
            instrumentationFile.toString(),
            TRACKING_PER_TEST.toString(),
            CALCULATE_FOR_UNLOADED_CLASSES.toString(),
            APPEND_TO_DATA_FILE.toString(),
            LINING_ONLY_MODE.toString(),
        )
        if (excludedClasses.isNotEmpty()) {
            lines.add("-exclude")
            excludedClasses.forEach { e ->
                lines.add(e.wildcardsToRegex())
            }
        }

        writeLines(lines)
    }

    companion object {
        const val AGENT_ARTIFACT_NAME = "org.jetbrains.intellij.deps:intellij-coverage-agent"

        /**
         * A flag to enable tracking per test coverage.
         */
        private const val TRACKING_PER_TEST = false

        /**
         * A flag to calculate coverage for unloaded classes.
         */
        private const val CALCULATE_FOR_UNLOADED_CLASSES = false

        /**
         * Use data file as initial coverage.
         *
         * `false` - to overwrite previous file content.
         */
        private const val APPEND_TO_DATA_FILE = true

        /**
         * Create hit block only for line - adds the ability to count branches
         */
        private const val LINING_ONLY_MODE = false

        /**
         * Enables saving the array in the /candy field,
         * without it there will be an appeal to the hash table foreach method, which very slow.
         */
        private const val ENABLE_TRACING = "idea.new.tracing.coverage=true"

        /**
         * Print errors to the Gradle stdout
         */
        private const val PRINT_ONLY_ERRORS = "idea.coverage.log.level=error"

        /**
         * Enables ignoring constructors in classes where all methods are static.
         */
        private const val IGNORE_STATIC_CONSTRUCTORS = "coverage.ignore.private.constructor.util.class=true"

        /**
         * Do not count amount hits of the line, only 0 or 1 will be place into int[] - reduce byte code size
         */
        private const val DO_NOT_COUNT_HIT_AMOUNT = "idea.coverage.calculate.hits=false"
    }
}
