plugins {
    application
}

description = "Command-line interface for the BuildKit Java client."

val junitVersion = "5.13.4"
val slf4jVersion = "2.0.17"
val logbackVersion = "1.4.14"

application {
    mainClass.set("io.github.kper.buildkitcli.cli.BuildkitCli")
}

base {
    archivesName.set("buildkit-java-client-cli")
}

dependencies {
    implementation(project(":lib"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
