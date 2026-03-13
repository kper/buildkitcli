plugins {
    id("java")
    application
}

group = "io.github.kper"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback:logback-classic:1.4.14")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("io.github.kper.buildkitcli.cli.BuildkitCli")
}

dependencies {
    implementation(project(":lib"))
}

tasks.test {
    useJUnitPlatform()
}