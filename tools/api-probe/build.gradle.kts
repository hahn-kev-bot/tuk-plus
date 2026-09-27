import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

application {
    mainClass.set("app.hahn.tukplus.tools.probe.MainKt")
}

dependencies {
    implementation(project(":core:network"))
}

// Run from the repository root, so that paths such as --record work as expected:
// ./gradlew :tools:api-probe:run --args="--record core/model/src/test/resources/fixtures/live"
tasks.named<JavaExec>("run") {
    workingDir = rootDir
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dfile.encoding=UTF-8")
}
