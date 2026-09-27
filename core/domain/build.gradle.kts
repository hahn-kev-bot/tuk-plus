import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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

dependencies {
    api(project(":core:model"))
    testImplementation(libs.kotlin.test)
}

tasks.test {
    // The tests read the responses that tools/api-probe recorded from the live API.
    systemProperty("fixturesDir", rootProject.file("core/model/src/test/resources/fixtures/live").absolutePath)
}
