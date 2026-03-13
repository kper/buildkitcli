import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.google.protobuf") version "0.9.5"
}

description = "Pure Java BuildKit client library."

val grpcVersion = "1.76.0"
val protobufVersion = "3.25.6"
val googleCommonProtoVersion = "2.58.0"
val nettyVersion = "4.1.121.Final"
val junitVersion = "5.13.4"
val assertjVersion = "3.27.6"
val testcontainersVersion = "2.0.3"

java {
    withJavadocJar()
    withSourcesJar()
}

base {
    archivesName.set("buildkit-java-client")
}

dependencies {
    implementation(platform("io.grpc:grpc-bom:$grpcVersion"))
    implementation("io.grpc:grpc-netty")
    implementation("io.grpc:grpc-protobuf")
    implementation("io.grpc:grpc-stub")
    implementation("io.grpc:grpc-services")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("com.google.api.grpc:proto-google-common-protos:$googleCommonProtoVersion")
    implementation("com.google.code.findbugs:jsr305:3.0.2")

    implementation("io.netty:netty-transport-classes-epoll:$nettyVersion")
    implementation("io.netty:netty-transport-classes-kqueue:$nettyVersion")
    runtimeOnly("io.netty:netty-transport-native-epoll:$nettyVersion:linux-x86_64")
    runtimeOnly("io.netty:netty-transport-native-epoll:$nettyVersion:linux-aarch_64")
    runtimeOnly("io.netty:netty-transport-native-kqueue:$nettyVersion:osx-x86_64")
    runtimeOnly("io.netty:netty-transport-native-kqueue:$nettyVersion:osx-aarch_64")

    protobuf("com.google.api.grpc:proto-google-common-protos:$googleCommonProtoVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("io.grpc:grpc-inprocess")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                create("grpc")
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "buildkit-java-client"
            from(components["java"])

            pom {
                name.set("buildkit-java-client")
                description.set(project.description)
            }
        }
    }

    repositories {
        maven {
            name = "release"

            val githubRepository = providers.environmentVariable("GITHUB_REPOSITORY")
            val configuredRepository = providers.environmentVariable("MAVEN_REPOSITORY_URL")
                .orElse(githubRepository.map { "https://maven.pkg.github.com/$it" })

            url = uri(configuredRepository.orElse(layout.buildDirectory.dir("repo").map { it.asFile.toURI().toString() }).get())

            credentials {
                username = providers.environmentVariable("MAVEN_USERNAME")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orNull
                password = providers.environmentVariable("MAVEN_PASSWORD")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orNull
            }
        }
    }
}

signing {
    val signingKey = providers.environmentVariable("MAVEN_SIGNING_KEY")
    val signingPassword = providers.environmentVariable("MAVEN_SIGNING_PASSWORD")

    if (signingKey.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), signingPassword.orNull)
        sign(publishing.publications["mavenJava"])
    }
}
