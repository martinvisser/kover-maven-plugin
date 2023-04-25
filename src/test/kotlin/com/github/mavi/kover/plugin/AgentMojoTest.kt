package com.github.mavi.kover.plugin

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import kotlin.io.path.readLines

class AgentMojoTest : BaseMojoTest() {
    fun `test should write arg line`() {
        val mojo = mojo<AgentMojo>("prepare-agent")

        mojo.execute()

        mojo.project.properties shouldContainKey "argLine"
        mojo.project.argsFile().readLines() shouldContainExactly
            listOf(
                mojo.project.instrumentation().toString(),
                "false",
                "false",
                "true",
                "false",
            )
    }

    fun `test should write argLine with excludes`() {
        val mojo = mojo<AgentMojo>("prepare-agent")
        mojo.excludesClasses.add("com.example.**")

        mojo.execute()

        mojo.project.properties shouldContainKey "argLine"
        mojo.project.argsFile().readLines() shouldContainExactly
            listOf(
                mojo.project.instrumentation().toString(),
                "false",
                "false",
                "true",
                "false",
                "-exclude",
                "com\\.example\\..*.*",
            )
    }
}
