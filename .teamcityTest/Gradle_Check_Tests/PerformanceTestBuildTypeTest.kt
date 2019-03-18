/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import common.JvmVendor
import common.JvmVersion
import common.Os
import configurations.BaseGradleBuildType
import configurations.PerformanceTest
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.GradleBuildStep
import model.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PerformanceTestBuildTypeTest {
    private
    val buildModel = model.CIBuildModel(buildScanTags = listOf("Check"))

    @Test
    fun `create correct PerformanceTest build type`() {
        val performanceTest = PerformanceTest(buildModel, PerformanceTestType.test, Stage(StageNames.READY_FOR_MERGE,
                specificBuilds = listOf(
                        SpecificBuild.BuildDistributions,
                        SpecificBuild.Gradleception,
                        SpecificBuild.SmokeTests),
                functionalTests = listOf(
                        TestCoverage(TestType.platform, Os.linux, JvmVersion.java8),
                        TestCoverage(TestType.platform, Os.windows, JvmVersion.java11, vendor = JvmVendor.openjdk)),
                performanceTests = listOf(PerformanceTestType.test),
                omitsSlowProjects = true))

        assertEquals(listOf(
                "GRADLE_RUNNER",
                "CHECK_CLEAN_M2",
                "GRADLE_RERUNNER"
        ), performanceTest.steps.items.map(BuildStep::name))

        val expectedRunnerParams = listOf(
                "-PmaxParallelForks=%maxParallelForks%",
                "-s",
                "--daemon",
                "--continue",
                "-I",
                "\"%teamcity.build.checkoutDir%/gradle/init-scripts/build-scan.init.gradle.kts\"",
                "-Dorg.gradle.internal.tasks.createops",
                "-Dorg.gradle.internal.plugins.portal.url.override=%gradle.plugins.portal.url%",
                "--build-cache",
                "\"-Dgradle.cache.remote.url=%gradle.cache.remote.url%\"",
                "\"-Dgradle.cache.remote.username=%gradle.cache.remote.username%\"",
                "\"-Dgradle.cache.remote.password=%gradle.cache.remote.password%\"",
                "-x",
                "prepareSamples",
                "--baselines",
                "%performance.baselines%",
                "",
                "-PtimestampedVersion",
                "-Porg.gradle.performance.branchName=%teamcity.build.branch%",
                "-Porg.gradle.performance.db.url=%performance.db.url%",
                "-Porg.gradle.performance.db.username=%performance.db.username%",
                "-Porg.gradle.performance.buildTypeId=Gradle_Check_IndividualPerformanceScenarioWorkersLinux",
                "-Porg.gradle.performance.workerTestTaskName=fullPerformanceTest",
                "-Porg.gradle.performance.coordinatorBuildId=%teamcity.build.id%",
                "-Porg.gradle.performance.db.password=%performance.db.password.tcagent%",
                "\"-Dscan.tag.PerformanceTest\"",
                "-PtestJavaHome=%linux.java8.oracle.64bit%",
                "-PteamCityUsername=%teamcity.username.restbot%",
                "-PteamCityPassword=%teamcity.password.restbot%",
                "-PteamCityBuildId=%teamcity.build.id%",
                "\"-Dscan.tag.Check\"",
                "\"-Dscan.tag.ReadyforMerge\""
        )

        assertEquals(expectedRunnerParams.joinToString(" "), performanceTest.getGradleStep("GRADLE_RUNNER").gradleParams)
        assertEquals("clean distributedPerformanceTests", performanceTest.getGradleStep("GRADLE_RUNNER").tasks)

        val expectedRerunnerParams = expectedRunnerParams + "-PonlyPreviousFailedTestClasses=true" + "-PteamCityBuildId=%teamcity.build.id%"
        assertEquals(expectedRerunnerParams.joinToString(" "), performanceTest.getGradleStep("GRADLE_RERUNNER").gradleParams)
        assertEquals("distributedPerformanceTests tagBuild", performanceTest.getGradleStep("GRADLE_RERUNNER").tasks)
    }

    private
    fun BaseGradleBuildType.getGradleStep(stepName: String) = steps.items.find { it.name == stepName }!! as GradleBuildStep
}
