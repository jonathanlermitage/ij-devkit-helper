import com.github.benmanes.gradle.versions.reporter.PlainTextReporter
import com.github.benmanes.gradle.versions.reporter.result.Result
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.palantir.gradle.gitversion.VersionDetails
import groovy.lang.Closure
import org.apache.commons.io.FileUtils
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.w3c.dom.Document
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

plugins {
    id("java")
    alias(libs.plugins.intelliJPlatform) // https://github.com/JetBrains/intellij-platform-gradle-plugin
    alias(libs.plugins.changelog) // https://github.com/JetBrains/gradle-changelog-plugin
    alias(libs.plugins.gradleVersionsPlugin) // https://github.com/ben-manes/gradle-versions-plugin
    alias(libs.plugins.gradleGitVersion) // https://github.com/palantir/gradle-git-version
    alias(libs.plugins.ogaGradlePlugin) // https://github.com/jonathanlermitage/oga-gradle-plugin
}

// Import variables from the gradle.properties file
val pluginVersion: String by project
val pluginJavaVersion: String by project
val pluginIdeaVersion: String by project
val pluginIdeaPlatformType: String by project
val pluginSinceBuild: String by project
val pluginUntilBuild: String by project

val myDisplayName = "DevKit Helper"
val myIdeaVersion = findIdeaVersion(pluginIdeaVersion)
val myReleaseVersion: String = latestTagToVersion()
val myPersistedSandboxIDEDir = "${rootProject.projectDir}/.idea-sandbox/${findSandboxedIDEVersionStr(pluginIdeaPlatformType, pluginIdeaVersion, myIdeaVersion)}"

logger.quiet("Will use IDEA $pluginIdeaVersion ($myIdeaVersion) and Java $pluginJavaVersion compiler. Plugin version set to $myReleaseVersion")

group = "lermitage.ij.devkit.helper"

repositories {
    mavenCentral()

    if (!hasProperty("bypassIntellijPlatformRepositories")) { // these repos are incredibly slow, and dependencyUpdates finds no test libs
        intellijPlatform {
            defaultRepositories()
        }
    }
}

buildscript {
    repositories {
        mavenCentral()
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(pluginJavaVersion)
        vendor = JvmVendorSpec.ADOPTIUM
    }
    sourceCompatibility = JavaVersion.toVersion(pluginJavaVersion)
    targetCompatibility = JavaVersion.toVersion(pluginJavaVersion)
}

dependencies {
    testImplementation(libs.junitApi)
    testRuntimeOnly(libs.junitEngine)
    testRuntimeOnly(libs.junitPlatformLauncher)
    testRuntimeOnly("junit:junit:4.13.2") // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-faq.html#junit5-test-framework-refers-to-junit4 should be fixed in 2024.3

    if (!hasProperty("bypassIntellijPlatformRepositories")) {
        intellijPlatform { // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
            create(pluginIdeaPlatformType, myIdeaVersion)
            instrumentationTools()
            pluginVerifier()
            testFramework(TestFrameworkType.Platform)

            // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#bundled-plugin
            // https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html#modules-specific-to-functionality
            bundledModule("com.intellij.java")
            plugins(listOf("DevKit:243.21565.129"))
        }
    }
}

if (!hasProperty("bypassIntellijPlatformRepositories")) {
    intellijPlatform { // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginConfiguration
        autoReload = false
        buildSearchableOptions = false
        instrumentCode = true
        projectName = myDisplayName

        pluginConfiguration {
            id = "lermitage.ij.devkit.helper"
            name = myDisplayName
            version = myReleaseVersion
            description = FileUtils.readFileToString(projectDir.resolve("misc/pluginDescription.html"), Charsets.UTF_8)

            ideaVersion {
                sinceBuild = pluginSinceBuild
                untilBuild = pluginUntilBuild
            }
        }

        pluginVerification {
            failureLevel = listOf(
                VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
                VerifyPluginTask.FailureLevel.COMPATIBILITY_WARNINGS,
                VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES,
                VerifyPluginTask.FailureLevel.EXPERIMENTAL_API_USAGES,
                VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
                VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
                VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
                VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
            )
            // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginVerification-ides
            ides {
                ide(IntelliJPlatformType.IntellijIdeaCommunity, myIdeaVersion)
                //ide(IntelliJPlatformType.IntellijIdeaCommunity, pluginSinceBuild)
                //ide(IntelliJPlatformType.IntellijIdeaCommunity, pluginUntilBuild)
                select {
                    types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
                    channels = ProductRelease.Channel.values().toList()
                    sinceBuild = pluginSinceBuild
                    untilBuild = pluginUntilBuild
                }
            }
        }
    }
}

changelog {
    headerParserRegex.set("(.*)".toRegex())
    itemPrefix.set("*")
}


tasks {
    register("clearLogs") {
        doLast {
            fun deleteDir(dir: File) {
                if (dir.exists() && dir.isDirectory) {
                    FileUtils.deleteDirectory(dir)
                    logger.quiet("Deleted: $dir")
                }
            }
            deleteDir(File("$myPersistedSandboxIDEDir/system/log/"))
            deleteDir(File("$myPersistedSandboxIDEDir/log/"))
        }
    }

    withType<JavaCompile> {
        sourceCompatibility = pluginJavaVersion
        targetCompatibility = pluginJavaVersion
        options.compilerArgs = listOf("-Xlint:deprecation")
        options.encoding = "UTF-8"
    }
    withType<Test> {
        dependsOn("prepareSandbox") // TODO should not depend on sandbox - bug in platform plugin v2?
        useJUnitPlatform()

        // avoid JBUIScale "Must be precomputed" error because the IDE is not started (LoadingState.APP_STARTED.isOccurred is false)
        jvmArgs("-Djava.awt.headless=true")

        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            events.add(TestLogEvent.PASSED)
            events.add(TestLogEvent.FAILED)
            showStandardStreams = true
        }
    }
    withType<DependencyUpdatesTask> {
        checkForGradleUpdate = true
        gradleReleaseChannel = "current"
        revision = "release"
        rejectVersionIf {
            isNonStable(candidate.version) || (candidate.group == "com.jetbrains.intellij.java" && candidate.module == "java-compiler-ant-tasks")
        }
        outputFormatter = closureOf<Result> {
            unresolved.dependencies.removeIf {
                val coordinates = "${it.group}:${it.name}"
                coordinates.startsWith("unzipped.com") ||
                    coordinates.startsWith("com.jetbrains:ideaI") ||
                    coordinates.startsWith("com.jetbrains.intellij") ||
                    coordinates.startsWith("com.intellij.remoterobot") ||
                    coordinates.startsWith("idea:ideaIC")
            }
            PlainTextReporter(project, revision, gradleReleaseChannel)
                .write(System.out, this)
        }
    }
    val runIde2 by intellijPlatformTesting.runIde.registering {
        task {
            // https://docs.gradle.org/current/dsl/org.gradle.api.tasks.JavaExec.html
            maxHeapSize = "2g"

            // ignore "The IDE seems to be launched with a script launcher" startup warning https://youtrack.jetbrains.com/articles/SUPPORT-A-56
            jvmArgs("-Dignore.ide.script.launcher.used=true")

            // force detection of slow operations in EDT when playing with sandboxed IDE (SlowOperations.assertSlowOperationsAreAllowed)
            jvmArgs("-Dide.slow.operations.assertion=true")
            jvmArgs("-Didea.is.internal=true")

            jvmArgs(
                "-XX:+HeapDumpOnOutOfMemoryError",
                "-XX:HeapDumpPath=${rootProject.projectDir}\\build\\java_error_in_idea64.hprof",
                "-XX:ErrorFile=${rootProject.projectDir}\\build\\java_error_in_idea64.log"
            )

            // disable hiding frequent exceptions in logs (annoying for debugging). See com.intellij.idea.IdeaLogger
            jvmArgs("-Didea.logger.exception.expiration.minutes=0")

            // disable the splash screen on startup because it may interrupt when debugging
            args(listOf("nosplash"))

            // If any warning or error with missing --add-opens, wait for the next gradle-intellij-plugin's update that should sync
            // with https://raw.githubusercontent.com/JetBrains/intellij-community/master/plugins/devkit/devkit-core/src/run/OpenedPackages.txt
            // or do it manually
        }

        prepareSandboxTask {
            sandboxDirectory = project.layout.buildDirectory.dir(myPersistedSandboxIDEDir)
            sandboxSuffix = ""
        }
    }
    patchPluginXml {
        changeNotes.set(provider {
            with(changelog) {
                renderItem(getLatest(), Changelog.OutputType.HTML)
            }
        })
    }
}

// tasks helpers

fun isNonStable(version: String): Boolean {
    if (listOf("RELEASE", "FINAL", "GA").any { version.uppercase().endsWith(it) }) {
        return false
    }
    return listOf("alpha", "Alpha", "ALPHA", "b", "beta", "Beta", "BETA", "rc", "RC", "M", "EA", "pr", "atlassian").any {
        "(?i).*[.-]${it}[.\\d-]*$".toRegex().matches(version)
    }
}

fun computeReleaseVersion(): String {
    if (!myReleaseVersion.contains(".")) {
        return "0.0.0-dev"
    }
    return myReleaseVersion.substring(0, myReleaseVersion.lastIndexOf(".")).replace(".", "")
}

fun findAndUpdateReleaseDate(): String {
    if (!myReleaseVersion.contains(".")) {
        return ""
    }
    val props = Properties()
    props.load(FileInputStream(projectDir.resolve("versions")))
    if (myReleaseVersion.endsWith(".1")) {
        if (props.containsKey(myReleaseVersion)) {
            return props.getProperty(myReleaseVersion)
        } else {
            val yesterday = LocalDate.now().minusDays(1)
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val newMajorReleaseDate = yesterday.format(formatter)
            props[myReleaseVersion] = newMajorReleaseDate
            props.store(FileOutputStream(projectDir.resolve("versions")), "Last update was for release $myReleaseVersion $newMajorReleaseDate")
            return newMajorReleaseDate
        }
    } else {
        val lastMajorReleaseVersion = myReleaseVersion.subSequence(0, myReleaseVersion.lastIndexOf(".")).toString() + ".1"
        if (props.containsKey(lastMajorReleaseVersion)) {
            return props.getProperty(lastMajorReleaseVersion)
        } else {
            throw GradleException("Failed to find last major release $lastMajorReleaseVersion")
        }
    }
}

/**
 * If the plugin version is set to "auto", returns the latest git tag "vXXX" as "XXX".
 * Otherwise, returns the plugin version property.
 */
fun latestTagToVersion(): String {
    return if (pluginVersion == "auto") {
        val versionDetails: Closure<VersionDetails> by extra
        val lastTag = versionDetails().lastTag
        if (lastTag.startsWith("v", ignoreCase = true)) {
            lastTag.substring(1)
        } else {
            lastTag
        }
    } else {
        pluginVersion
    }
}

/**
 * Find the best IDE sandbox version string for given IDE channel and version.
 * @param ideaChannel the IDE channel: LATEST-STABLE or LATEST-EAP-SNAPSHOT
 * @param ideaVersion the IDE version number: strings like 2023.3 or 233.11799.241
 */
fun findSandboxedIDEVersionStr(platformType: String, ideaChannel: String, ideaVersion: String): String {
    if (ideaChannel == "LATEST-STABLE") {
        return "$platformType-" + ideaVersion.substring(0, ideaVersion.indexOf("."))
    }
    if (ideaChannel == "LATEST-EAP-SNAPSHOT") {
        return "$platformType-eap-" + ideaVersion.substring(0, 2)
    }
    return "$platformType-$ideaVersion-manually-set"
}

/**
 * Find the latest stable or EAP IDE version from the JetBrains website, otherwise simply returns the given IDE version string.
 * @param ideaVersion can be LATEST-STABLE, LATEST-EAP-SNAPSHOT or a specific IDE version string (year.maj.min).
 */
fun findIdeaVersion(ideaVersion: String): String {

    /** Find the latest IntelliJ EAP version from the JetBrains website. Result is cached locally for 24h. */
    fun findLatestIdeaVersion(isStable: Boolean): String {

        /** Read a remote file as String. */
        fun readRemoteContent(url: URL): String {
            val t1 = System.currentTimeMillis()
            val content = StringBuilder()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            BufferedReader(InputStreamReader(conn.inputStream)).use { rd ->
                var line: String? = rd.readLine()
                while (line != null) {
                    content.append(line)
                    line = rd.readLine()
                }
            }
            val t2 = System.currentTimeMillis()
            logger.quiet("Download $url, took ${t2 - t1} ms (${content.length} B)")
            return content.toString()
        }

        /** Find the latest IntelliJ version from the given url and xpath expression that picks the desired IDE version and channel.
         * The result is cached for 24hr.*/
        fun getOnlineLatestIdeVersion(definitionsUrl: URL, xpathExpression: String): String {
            val definitionsStr = readRemoteContent(definitionsUrl)
            val builderFactory = DocumentBuilderFactory.newInstance()
            val builder = builderFactory.newDocumentBuilder()
            val xmlDocument: Document = builder.parse(ByteArrayInputStream(definitionsStr.toByteArray()))
            val xPath = XPathFactory.newInstance().newXPath()
            return xPath.compile(xpathExpression).evaluate(xmlDocument, XPathConstants.STRING) as String
        }

        val t1 = System.currentTimeMillis()
        // IMPORTANT if not available, migrate to https://data.services.jetbrains.com/products?code=IC
        val definitionsUrl = URL("https://www.jetbrains.com/updates/updates.xml")
        val xpathExpression =
            if (isStable) "/products/product[@name='IntelliJ IDEA']/channel[@id='IC-IU-RELEASE-licensing-RELEASE']/build[1]/@version"
            else "/products/product[@name='IntelliJ IDEA']/channel[@id='IC-IU-EAP-licensing-EAP']/build[1]/@fullNumber"
        val cachedLatestVersionFile =
            File(System.getProperty("java.io.tmpdir") + (if (isStable) "/jle-ij-latest-stable-version.txt" else "/jle-ij-latest-eap-version.txt"))
        var latestVersion: String
        try {
            if (cachedLatestVersionFile.exists()) {

                val cacheDurationMs = 24 * 60 * 60_000 // 24hr
                if (cachedLatestVersionFile.exists() && cachedLatestVersionFile.lastModified() < (System.currentTimeMillis() - cacheDurationMs)) {
                    logger.quiet("Cache expired, find latest EAP IDE version from $definitionsUrl then update cached file $cachedLatestVersionFile")
                    latestVersion = getOnlineLatestIdeVersion(definitionsUrl, xpathExpression)
                    cachedLatestVersionFile.delete()
                    Files.writeString(cachedLatestVersionFile.toPath(), latestVersion, Charsets.UTF_8)

                } else {
                    logger.quiet("Find latest EAP IDE version from cached file $cachedLatestVersionFile")
                    latestVersion = Files.readString(cachedLatestVersionFile.toPath())
                }

            } else {
                logger.quiet("Find latest EAP IDE version from $definitionsUrl")
                latestVersion = getOnlineLatestIdeVersion(definitionsUrl, xpathExpression)
                Files.writeString(cachedLatestVersionFile.toPath(), latestVersion, Charsets.UTF_8)
            }

        } catch (e: Exception) {
            if (cachedLatestVersionFile.exists()) {
                logger.warn("Error: ${e.message}. Will find latest EAP IDE version from cached file $cachedLatestVersionFile")
                latestVersion = Files.readString(cachedLatestVersionFile.toPath())
            } else {
                throw RuntimeException(e)
            }
        }
        if (logger.isDebugEnabled) {
            val t2 = System.currentTimeMillis()
            logger.debug("Operation took ${t2 - t1} ms")
        }
        return latestVersion
    }

    if (ideaVersion == "LATEST-STABLE") {
        val version = findLatestIdeaVersion(true)
        logger.quiet("Found latest stable IDE version: $version")
        return version
    }
    if (ideaVersion == "LATEST-EAP-SNAPSHOT") {
        val version =  findLatestIdeaVersion(false)
        logger.quiet("Found latest EAP IDE version: $version")
        return version
    }
    logger.warn("Will use user-defined IDE version: $version")
    return ideaVersion
}
