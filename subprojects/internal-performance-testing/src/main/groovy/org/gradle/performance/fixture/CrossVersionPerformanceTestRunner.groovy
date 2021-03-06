/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.performance.fixture

import com.google.common.base.Splitter
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.executer.ExecuterDecoratingGradleDistribution
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuterDecorator
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.results.CrossVersionPerformanceResults
import org.gradle.performance.results.DataReporter
import org.gradle.performance.results.MeasuredOperationList
import org.gradle.performance.results.ResultsStore
import org.gradle.performance.results.ResultsStoreHelper
import org.gradle.util.GradleVersion
import org.junit.Assume

import java.util.regex.Pattern

public class CrossVersionPerformanceTestRunner extends PerformanceTestSpec {
    private static final Pattern COMMA_OR_SEMICOLON = Pattern.compile('[;,]')

    GradleDistribution current
    final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    final DataReporter<CrossVersionPerformanceResults> reporter
    TestProjectLocator testProjectLocator = new TestProjectLocator()
    final BuildExperimentRunner experimentRunner
    final ReleasedVersionDistributions releases

    String testProject
    File workingDir
    boolean useDaemon

    List<String> tasksToRun = []
    List<String> args = []
    List<String> gradleOpts = ['-Xms2g', '-Xmx2g']
    List<String> previousTestIds = []
    int maxPermSizeMB = 256

    List<String> targetVersions = []

    BuildExperimentListener buildExperimentListener
    GradleExecuterDecorator executerDecorator

    CrossVersionPerformanceTestRunner(BuildExperimentRunner experimentRunner, DataReporter<CrossVersionPerformanceResults> reporter, ReleasedVersionDistributions releases) {
        this.reporter = reporter
        this.experimentRunner = experimentRunner
        this.releases = releases
    }

    CrossVersionPerformanceResults run() {
        if (testId == null) {
            throw new IllegalStateException("Test id has not been specified")
        }
        if (testProject == null) {
            throw new IllegalStateException("Test project has not been specified")
        }
        if (workingDir == null) {
            throw new IllegalStateException("Working directory has not been specified")
        }
        if (!targetVersions) {
            throw new IllegalStateException("Target versions have not been specified")
        }

        def scenarioSelector = new TestScenarioSelector()
        Assume.assumeTrue(scenarioSelector.shouldRun(testId, [testProject].toSet(), (ResultsStore) reporter))

        def results = new CrossVersionPerformanceResults(
            testId: testId,
            previousTestIds: previousTestIds.collect { it.toString() }, // Convert GString instances
            testProject: testProject,
            tasks: tasksToRun.collect { it.toString() },
            args: args.collect { it.toString() },
            gradleOpts: resolveGradleOpts().collect { it.toString() },
            daemon: useDaemon,
            jvm: Jvm.current().toString(),
            operatingSystem: OperatingSystem.current().toString(),
            versionUnderTest: GradleVersion.current().getVersion(),
            vcsBranch: Git.current().branchName,
            vcsCommits: [Git.current().commitId],
            startTime: System.currentTimeMillis(),
            channel: ResultsStoreHelper.determineChannel()
        )

        runVersion(current, perVersionWorkingDirectory('current'), results.current)

        def baselineVersions = toBaselineVersions(releases, targetVersions)

        baselineVersions.each { it ->
            def baselineVersion = results.baseline(it)
            runVersion(buildContext.distribution(baselineVersion.version), perVersionWorkingDirectory(baselineVersion.version), baselineVersion.results)
        }

        results.endTime = System.currentTimeMillis()

        results.assertEveryBuildSucceeds()

        // Don't store results when builds have failed
        reporter.report(results)

        results.assertCurrentVersionHasNotRegressed()

        return results
    }

    protected File perVersionWorkingDirectory(String version) {
        def perVersion = new File(workingDir, version)
        if (!perVersion.exists()) {
            perVersion.mkdirs()
        } else {
            throw new IllegalArgumentException("Didn't expect to find an existing directory at $perVersion")
        }
        perVersion
    }

    static Iterable<String> toBaselineVersions(ReleasedVersionDistributions releases, List<String> targetVersions) {
        Iterable<String> versions
        boolean addMostRecentFinalRelease = true
        def overrideBaselinesProperty = System.getProperty('org.gradle.performance.baselines')
        if (overrideBaselinesProperty) {
            versions = resolveOverriddenVersions(overrideBaselinesProperty, targetVersions)
            addMostRecentFinalRelease = false
        } else {
            versions = targetVersions
        }

        def baselineVersions = new LinkedHashSet<String>()

        def mostRecentFinalRelease = releases.mostRecentFinalRelease.version.version
        def mostRecentSnapshot = releases.mostRecentSnapshot.version.version
        def currentBaseVersion = GradleVersion.current().getBaseVersion().version

        for (String version : versions) {
            if (version == currentBaseVersion) {
                // current version is run by default, skip adding it to baseline
                continue
            }
            if (version == 'last') {
                addMostRecentFinalRelease = false
                baselineVersions.add(mostRecentFinalRelease)
                continue
            }
            if (version == 'nightly') {
                addMostRecentFinalRelease = false
                baselineVersions.add(mostRecentSnapshot)
                continue
            }
            if (version == 'none') {
                // when 'none' is the only target version, no baselines will be used
                addMostRecentFinalRelease = false
                continue
            }
            if (version == 'defaults') {
                throw new IllegalArgumentException("'defaults' shouldn't be used in target versions.")
            }
            def releasedVersion = findRelease(releases, version)
            if (releasedVersion) {
                baselineVersions.add(releasedVersion.version.version)
            } else if (GradleVersion.version(version).snapshot) {
                // for snapshots, we don't have a cheap way to check if it really exists, so we'll just
                // blindly add it to the list and trust the test author
                addMostRecentFinalRelease = false
                baselineVersions.add(version)
            } else {
                throw new RuntimeException("Cannot find Gradle release that matches version '$version'")
            }
        }

        if (addMostRecentFinalRelease) {
            // Always include the most recent final release if we're not testing against a nightly or a snapshot
            baselineVersions.add(mostRecentFinalRelease)
        }

        baselineVersions
    }

    private static Iterable<String> resolveOverriddenVersions(String overrideBaselinesProperty, List<String> targetVersions) {
        def versions = Splitter.on(COMMA_OR_SEMICOLON)
            .omitEmptyStrings()
            .splitToList(overrideBaselinesProperty)
        versions.collectMany([] as Set) { version -> version == 'defaults' ? targetVersions : [version] }
    }

    protected static GradleDistribution findRelease(ReleasedVersionDistributions releases, String requested) {
        GradleDistribution best = null
        for (GradleDistribution release : releases.all) {
            if (release.version.version == requested) {
                return release
            }
            if (!release.version.snapshot && release.version.baseVersion.version == requested && (best == null || best.version < release.version)) {
                best = release
            }
        }

        best
    }

    private void runVersion(GradleDistribution dist, File workingDir, MeasuredOperationList results) {
        def gradleOptsInUse = resolveGradleOpts()
        def builder = GradleBuildExperimentSpec.builder()
            .projectName(testProject)
            .displayName(dist.version.version)
            .warmUpCount(warmUpRuns)
            .invocationCount(runs)
            .listener(buildExperimentListener)
            .invocation {
                workingDirectory(workingDir)
                distribution(new ExecuterDecoratingGradleDistribution(dist, executerDecorator))
                tasksToRun(this.tasksToRun as String[])
                args(this.args as String[])
                gradleOpts(gradleOptsInUse as String[])
                useDaemon(this.useDaemon)
            }
        builder.workingDirectory = workingDir
        def spec = builder.build()
        if (experimentRunner.honestProfiler) {
            experimentRunner.honestProfiler.sessionId = "${testId}-${dist.version.version}".replaceAll('[^a-zA-Z0-9.-]', '_').replaceAll('[_]+', '_')
        }
        experimentRunner.run(spec, results)
    }

    def resolveGradleOpts() {
        def gradleOptsInUse = [] + this.gradleOpts
        if (!JavaVersion.current().isJava8Compatible() && gradleOptsInUse.count { it.startsWith('-XX:MaxPermSize=') } == 0) {
            gradleOptsInUse << "-XX:MaxPermSize=${maxPermSizeMB}m".toString()
        }
        gradleOptsInUse
    }

    HonestProfilerCollector getHonestProfiler() {
        return experimentRunner.honestProfiler
    }
}
