package io.github.mavi.kover.plugin

import io.kotest.assertions.withClue
import io.kotest.matchers.file.exist
import io.kotest.matchers.should
import io.kotest.matchers.shouldNotBe
import org.apache.maven.execution.DefaultMavenExecutionRequest
import org.apache.maven.execution.MavenExecutionRequestPopulator
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.testing.AbstractMojoTestCase
import org.apache.maven.project.ProjectBuilder
import org.eclipse.aether.DefaultRepositorySystemSession
import java.io.File

abstract class BaseMojoTest : AbstractMojoTestCase() {
    internal lateinit var session: MavenSession

    override fun getPluginDescriptorPath(): String = "${getBasedir()}/target/classes/META-INF/maven/plugin.xml"

    override fun setUp() {
        val basedir = File("${javaClass.getResource("/")?.path}/projects/default")
        File(basedir, "target").deleteRecursively()
        System.setProperty("basedir", basedir.path)

        val pom = getTestFile("pom.xml")
        withClue("pom should not be null") { pom shouldNotBe null }
        pom should exist()

        val request = DefaultMavenExecutionRequest()
        request.setBaseDirectory(basedir)
        val populator = container.lookup(MavenExecutionRequestPopulator::class.java)
        populator.populateDefaults(request)
        val buildingRequest =
            request.projectBuildingRequest
                .setRepositorySession(DefaultRepositorySystemSession())
                .setResolveDependencies(true)

        val projectBuilder = lookup(ProjectBuilder::class.java)
        val project = projectBuilder.build(pom, buildingRequest).project
        session = newMavenSession(project)
        super.setUp()
    }

    internal inline fun <reified T : AbstractKoverMojo> mojo(goal: String): T {
        val execution = newMojoExecution(goal)
        val mojo = lookupConfiguredMojo(session, execution) as T
        mojo.project = session.currentProject
        mojo.pluginArtifactMap = session.currentProject.pluginArtifactMap +
            session.currentProject.artifactMap.filterKeys {
                it.startsWith("org.jetbrains.intellij.deps:intellij-coverage-")
            }
        withClue("mojo should not be null") { mojo shouldNotBe null }
        return mojo
    }
}
