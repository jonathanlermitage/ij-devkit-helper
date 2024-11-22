rootProject.name = "ij-devkit-helper"
//startParameter.isContinueOnFailure = true

// uncomment to use gradle-intellij-plugin snapshots
// https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#snapshot-release
/*pluginManagement {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        gradlePluginPortal()
    }
}*/

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
