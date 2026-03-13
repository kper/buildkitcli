plugins {
    `java-library`
    signing
    id("com.vanniktech.maven.publish") version "0.36.0"
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

base {
    archivesName.set("buildkitcli-lib")
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

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()
    coordinates(group.toString(), "buildkitcli", version.toString())

    pom {
        name = "buildkitcli"
        description = project.description
        inceptionYear = "2026"
        url = "https://github.com/kper/buildkitcli/"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "kper"
                name = "Kevin Per"
                url = "https://github.com/kper/"
            }
        }
        scm {
            url = "https://github.com/kper/buildkitcli/"
            connection = "scm:git:git://github.com/kper/buildkitcli.git"
            developerConnection = "scm:git:ssh://git@github.com/kper/buildkitcli.git"
        }
    }
}