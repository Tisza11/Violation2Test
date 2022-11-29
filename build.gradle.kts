plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("application")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    implementation("com.beust:jcommander:1.82")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

application {
    mainClassName = "hu.bme.mit.violation2test.Main"
}