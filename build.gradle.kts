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
}


application {
    mainClass.set("MainKt")
}