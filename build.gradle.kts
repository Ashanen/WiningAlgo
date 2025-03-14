plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "1.8.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.binance:binance-futures-connector-java:3.0.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("ch.qos.logback:logback-classic:1.2.11")
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.8.0")
}


application {
    mainClass.set("MainKt")
}