package io.github.kper.buildkitcli.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

class BuildkitClientIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void buildsImageFromDockerfileAndContext() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");

        try (GenericContainer<?> buildkit = new GenericContainer<>("moby/buildkit:v0.28.0")
                .withPrivilegedMode(true)
                .withExposedPorts(1234)
                .withCommand("--addr", "tcp://0.0.0.0:1234")
                .waitingFor(Wait.forListeningPort())) {
            buildkit.start();

            Path contextDir = Files.createDirectory(tempDir.resolve("context"));
            Files.writeString(contextDir.resolve("hello.txt"), "hello");
            Path dockerfile = Files.writeString(contextDir.resolve("Dockerfile"), "FROM scratch\nCOPY hello.txt /hello.txt\n");

            BuildkitConnectionConfig config = new BuildkitConnectionConfig(
                    URI.create("tcp://" + buildkit.getHost() + ":" + buildkit.getMappedPort(1234)),
                    Duration.ofMinutes(2),
                    null);

            DockerfileBuildRequest request = DockerfileBuildRequest.builder(contextDir, dockerfile, "example.com/buildkit-java:test")
                    .build();

            try (BuildkitClient client = new BuildkitClient(config)) {
                BuildResult result = client.buildImage(request, BuildProgressListener.NOOP);
                assertThat(result.imageDigest()).isNotBlank();
                assertThat(result.exporterResponse()).containsKey("containerimage.digest");
            }
        }
    }

    @Test
    void buildsImageOverMutualTlsConnection() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");

        TlsTestMaterials tls = tlsTestMaterialsFromResources();

        try (GenericContainer<?> buildkit = new GenericContainer<>("moby/buildkit:v0.28.0")
                .withPrivilegedMode(true)
                .withExposedPorts(1234)
                .withCopyFileToContainer(MountableFile.forHostPath(tls.caCert()), "/certs/ca.pem")
                .withCopyFileToContainer(MountableFile.forHostPath(tls.serverCert()), "/certs/server-cert.pem")
                .withCopyFileToContainer(MountableFile.forHostPath(tls.serverKey()), "/certs/server-key.pem")
                .withCommand(
                        "--addr",
                        "tcp://0.0.0.0:1234",
                        "--tlscacert",
                        "/certs/ca.pem",
                        "--tlscert",
                        "/certs/server-cert.pem",
                        "--tlskey",
                        "/certs/server-key.pem")
                .waitingFor(Wait.forListeningPort())) {
            buildkit.start();

            Path contextDir = Files.createDirectory(tempDir.resolve("context-tls"));
            Files.writeString(contextDir.resolve("hello.txt"), "hello");
            Path dockerfile = Files.writeString(contextDir.resolve("Dockerfile"), "FROM scratch\nCOPY hello.txt /hello.txt\n");

            BuildkitConnectionConfig config = new BuildkitConnectionConfig(
                    URI.create("tcp://" + buildkit.getHost() + ":" + buildkit.getMappedPort(1234)),
                    Duration.ofMinutes(2),
                    new TlsConfig(tls.caCert(), tls.clientCert(), tls.clientKey(), "buildkitd.test"));

            DockerfileBuildRequest request = DockerfileBuildRequest.builder(contextDir, dockerfile, "example.com/buildkit-java:tls")
                    .build();

            try (BuildkitClient client = new BuildkitClient(config)) {
                BuildResult result = client.buildImage(request, BuildProgressListener.NOOP);
                assertThat(result.imageDigest()).isNotBlank();
                assertThat(result.exporterResponse()).containsKey("containerimage.digest");
            }
        }
    }

    @Test
    void buildsUsingDockerfileOutsideContextWithCustomFilename() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");

        try (GenericContainer<?> buildkit = new GenericContainer<>("moby/buildkit:v0.28.0")
                .withPrivilegedMode(true)
                .withExposedPorts(1234)
                .withCommand("--addr", "tcp://0.0.0.0:1234")
                .waitingFor(Wait.forListeningPort())) {
            buildkit.start();

            Path contextDir = Files.createDirectory(tempDir.resolve("context2"));
            Files.writeString(contextDir.resolve("hello.txt"), "hello");
            Path dockerfileDir = Files.createDirectory(tempDir.resolve("docker"));
            Path dockerfile = Files.writeString(
                    dockerfileDir.resolve("Dockerfile.custom"),
                    "FROM scratch\nCOPY hello.txt /hello.txt\n");

            BuildkitConnectionConfig config = new BuildkitConnectionConfig(
                    URI.create("tcp://" + buildkit.getHost() + ":" + buildkit.getMappedPort(1234)),
                    Duration.ofMinutes(2),
                    null);

            DockerfileBuildRequest request = DockerfileBuildRequest.builder(contextDir, dockerfile, "example.com/buildkit-java:custom")
                    .build();

            try (BuildkitClient client = new BuildkitClient(config)) {
                BuildResult result = client.buildImage(request, BuildProgressListener.NOOP);
                assertThat(result.imageDigest()).isNotBlank();
                assertThat(result.exporterResponse()).containsKey("containerimage.digest");
            }
        }
    }

    @Test
    void exportsArtifactsToLocalOutputDirectory() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");

        try (GenericContainer<?> buildkit = new GenericContainer<>("moby/buildkit:v0.28.0")
                .withPrivilegedMode(true)
                .withExposedPorts(1234)
                .withCommand("--addr", "tcp://0.0.0.0:1234")
                .waitingFor(Wait.forListeningPort())) {
            buildkit.start();

            Path contextDir = Files.createDirectory(tempDir.resolve("context-local"));
            Path dockerfile = Files.writeString(contextDir.resolve("Dockerfile"), """
                    FROM busybox AS builder
                    RUN mkdir -p /out && \
                        printf 'hello from build\\n' > /out/myapp && \
                        printf 'coverage: 100%%\\n' > /out/coverage.txt

                    FROM scratch AS artifacts
                    COPY --from=builder /out/myapp /
                    COPY --from=builder /out/coverage.txt /
                    """);
            Path outputDir = tempDir.resolve("dist");

            BuildkitConnectionConfig config = new BuildkitConnectionConfig(
                    URI.create("tcp://" + buildkit.getHost() + ":" + buildkit.getMappedPort(1234)),
                    Duration.ofMinutes(2),
                    null);

            DockerfileBuildRequest request = DockerfileBuildRequest.builder(contextDir, dockerfile, "")
                    .outputMode(BuildOutputMode.LOCAL)
                    .localOutputDir(outputDir)
                    .target("artifacts")
                    .build();

            try (BuildkitClient client = new BuildkitClient(config)) {
                BuildResult result = client.buildImage(request, BuildProgressListener.NOOP);
                assertThat(result.exportedArchive()).isNull();
                assertThat(Files.readString(outputDir.resolve("myapp"))).isEqualTo("hello from build\n");
                assertThat(Files.readString(outputDir.resolve("coverage.txt"))).isEqualTo("coverage: 100%\n");
            }
        }
    }

    private static TlsTestMaterials tlsTestMaterialsFromResources() throws URISyntaxException {
        // To regenerate these fixtures:
        //   TLS_DIR=lib/src/test/resources/io/github/kper/buildkitcli/lib/tls
        //   mkdir -p "$TLS_DIR"
        //   openssl req -x509 -newkey rsa:2048 -nodes \
        //     -keyout "$TLS_DIR/ca-key.pem" -out "$TLS_DIR/ca.pem" \
        //     -subj "/CN=Buildkit Test CA" -days 3650
        //   openssl req -newkey rsa:2048 -nodes \
        //     -keyout "$TLS_DIR/server-key.pkcs1.pem" -out "$TLS_DIR/server.csr" \
        //     -subj "/CN=buildkitd.test"
        //   printf 'subjectAltName=DNS:buildkitd.test\nextendedKeyUsage=serverAuth\n' > "$TLS_DIR/server-ext.cnf"
        //   openssl x509 -req -in "$TLS_DIR/server.csr" \
        //     -CA "$TLS_DIR/ca.pem" -CAkey "$TLS_DIR/ca-key.pem" -CAcreateserial \
        //     -out "$TLS_DIR/server-cert.pem" -days 3650 -extfile "$TLS_DIR/server-ext.cnf"
        //   openssl pkcs8 -topk8 -nocrypt -in "$TLS_DIR/server-key.pkcs1.pem" -out "$TLS_DIR/server-key.pem"
        //   openssl req -newkey rsa:2048 -nodes \
        //     -keyout "$TLS_DIR/client-key.pkcs1.pem" -out "$TLS_DIR/client.csr" \
        //     -subj "/CN=buildkit-client"
        //   printf 'extendedKeyUsage=clientAuth\n' > "$TLS_DIR/client-ext.cnf"
        //   openssl x509 -req -in "$TLS_DIR/client.csr" \
        //     -CA "$TLS_DIR/ca.pem" -CAkey "$TLS_DIR/ca-key.pem" -CAcreateserial \
        //     -out "$TLS_DIR/client-cert.pem" -days 3650 -extfile "$TLS_DIR/client-ext.cnf"
        //   openssl pkcs8 -topk8 -nocrypt -in "$TLS_DIR/client-key.pkcs1.pem" -out "$TLS_DIR/client-key.pem"
        //   rm "$TLS_DIR"/ca-key.pem "$TLS_DIR"/ca.srl "$TLS_DIR"/server.csr "$TLS_DIR"/server-ext.cnf \
        //      "$TLS_DIR"/server-key.pkcs1.pem "$TLS_DIR"/client.csr "$TLS_DIR"/client-ext.cnf \
        //      "$TLS_DIR"/client-key.pkcs1.pem
        // Important: commit `server-key.pem` and `client-key.pem` in PKCS#8 PEM form (`BEGIN PRIVATE KEY`),
        // because Netty's key loader used by the client rejects PKCS#1 (`BEGIN RSA PRIVATE KEY`) here.
        return new TlsTestMaterials(
                resourcePath("tls/ca.pem"),
                resourcePath("tls/client-cert.pem"),
                resourcePath("tls/client-key.pem"),
                resourcePath("tls/server-cert.pem"),
                resourcePath("tls/server-key.pem"));
    }

    private static Path resourcePath(String resourceName) throws URISyntaxException {
        URI resource = Objects.requireNonNull(BuildkitClientIntegrationTest.class.getResource(resourceName), resourceName)
                .toURI();
        return Path.of(resource);
    }

    private record TlsTestMaterials(
            Path caCert, Path clientCert, Path clientKey, Path serverCert, Path serverKey) {
    }
}
