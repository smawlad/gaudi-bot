plugins {
    kotlin("jvm") version "2.0.0"  // or the latest version available
    application
    kotlin("plugin.serialization") version "2.0.0"
    id("io.ktor.plugin") version "2.3.12"
}

group = "com.gaudi.bot"
version = "1.0.0"

application {
    mainClass.set("com.gaudi.bot.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin Standard Library
    implementation(kotlin("stdlib"))

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Ktor
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.46.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.46.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.46.0")
    implementation("org.postgresql:postgresql:42.7.2")

    // Encryption
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")

    // NLP
    implementation("org.apache.opennlp:opennlp-tools:2.3.1")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.0")
    testImplementation("io.ktor:ktor-client-mock:2.3.12")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.gaudi.bot.MainKt"
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}
