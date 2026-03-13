plugins {
    `java-library`
    application
    id("com.google.protobuf") version "0.9.5"
}

group = "io.github.kper"
version = "0.1.0-SNAPSHOT"

val grpcVersion = "1.76.0"
val protobufVersion = "3.25.6"
val googleCommonProtoVersion = "2.58.0"
val nettyVersion = "4.1.121.Final"
val junitVersion = "5.13.4"
val assertjVersion = "3.27.6"
val testcontainersVersion = "1.21.3"

repositories {
    mavenCentral()
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
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
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
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

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "io.github.kper.buildkit.cli.BuildkitCli"
}
