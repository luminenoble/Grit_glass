/*
 * Copyright (C) 2026  Shubham Gorai
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
}

val gritVersionName = providers.gradleProperty("grit.versionName").get()
val gritVersionCode = providers.gradleProperty("grit.versionCode").get()

// MSI versions are MAJOR.MINOR.BUILD; the build is the version code, so every release is higher.
val msiVersion = gritVersionName.split('.').take(2).joinToString(".") + ".$gritVersionCode"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-sensitive-resolution")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        optIn.add(
            "androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi"
        )
    }
}

koinCompiler { compileSafety = false }

dependencies {
    implementation(projects.shared.core)
    implementation(projects.shared.ui)
    implementation(projects.shared.sync)

    implementation(compose.desktop.currentOs)
    // Windows natives as well, so a jar built anywhere also runs on Windows.
    implementation(compose.desktop.windows_x64)
    implementation(libs.kotlinx.coroutines.swing)

    implementation(libs.compose.material3)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.windowsizeclass)
    implementation(libs.jetbrains.navigation3.ui)

    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.compose.viewmodel.navigation)
    implementation(libs.koin.annotations)

    implementation(libs.filekit.core)
    implementation(libs.filekit.dialogs)
    implementation(libs.zxing.core)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(compose.desktop.uiTestJUnit4)
}

// The app version and the changelog (the same ten newest entries the Android app shows), as
// resources made from gradle.properties and CHANGELOG.md.
val generatedResources = layout.buildDirectory.dir("generated/gritResources")
val writeAppInfo =
    tasks.register("writeAppInfo") {
        val out = generatedResources
        val name = gritVersionName
        val changelog = rootProject.file("CHANGELOG.md")
        inputs.property("version", name)
        inputs.file(changelog)
        outputs.dir(out)
        doLast {
            val dir = out.get().asFile
            dir.mkdirs()
            File(dir, "grit-version.txt").writeText(name)
            val entries = LinkedHashMap<String, MutableList<String>>()
            var current: String? = null
            changelog.readLines().forEach { line ->
                when {
                    line.startsWith("## ") -> current = line.removePrefix("## ").trim().also { entries[it] = mutableListOf() }
                    line.startsWith("- ") && current != null -> entries.getValue(current!!) += line.removePrefix("- ").trim()
                }
            }
            val json =
                entries.entries.take(10).map { mapOf("version" to it.key, "changes" to it.value) }
            File(dir, "changelog.json").writeText(groovy.json.JsonOutput.toJson(json))
        }
    }

sourceSets.main { resources.srcDir(writeAppInfo) }

compose.desktop {
    application {
        mainClass = "com.shub39.grit.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "--enable-native-access=ALL-UNNAMED")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "GritGlass"
            packageVersion = msiVersion
            description = "Grit Glass"
            copyright = "GPL-3.0"
            modules("jdk.httpserver", "jdk.unsupported", "java.naming")
            windows {
                menuGroup = "Grit Glass"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "5a0c6d1e-8b4f-4c7a-9f0e-7d2b3c1a9e41"
                iconFile.set(project.file("icon.ico"))
            }
        }
    }
}

/** A single jar with the Windows natives, runnable with `javaw -jar` on any Windows with Java 21+. */
tasks.register<Copy>("windowsJar") {
    group = "distribution"
    dependsOn("packageUberJarForCurrentOS")
    from(layout.buildDirectory.dir("compose/jars"))
    include("*-$msiVersion.jar")
    into(layout.buildDirectory.dir("windows"))
    rename { "GritGlass-$gritVersionName.jar" }
}
