package com.github.mavi.kover.plugin

import io.mockk.every
import io.mockk.mockk
import org.apache.maven.project.MavenProject
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

open class BaseMojoTest {
    internal val mavenProject = mockk<MavenProject> {
        every { name } returns "kover-maven-plugin"
        every { properties } returns Properties()
        every { build } returns mockk {
            every { directory } returns "target/test"
            every { sourceDirectory } returns "src"
            every { outputDirectory } returns "classes"
            every { resources } returns listOf()
            every { reporting } returns mockk {
                every { outputDirectory } returns "target/test/report"
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun startClean() {
        Path.of(mavenProject.build.directory).deleteRecursively()
    }
}
