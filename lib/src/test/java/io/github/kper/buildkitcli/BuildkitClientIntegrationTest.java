package io.github.kper.buildkitcli;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

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
    void exportsDockerArchiveThatCanBeLoadedIntoDocker() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");

        try (GenericContainer<?> buildkit = new GenericContainer<>("moby/buildkit:v0.28.0")
                .withPrivilegedMode(true)
                .withExposedPorts(1234)
                .withCommand("--addr", "tcp://0.0.0.0:1234")
                .waitingFor(Wait.forListeningPort())) {
            buildkit.start();

            Path contextDir = Files.createDirectory(tempDir.resolve("context-load"));
            Files.writeString(contextDir.resolve("hello.txt"), "hello");
            Path dockerfile = Files.writeString(
                    contextDir.resolve("Dockerfile"),
                    "FROM scratch\nCOPY hello.txt /hello.txt\n");

            BuildkitConnectionConfig config = new BuildkitConnectionConfig(
                    URI.create("tcp://" + buildkit.getHost() + ":" + buildkit.getMappedPort(1234)),
                    Duration.ofMinutes(2),
                    null);

            String imageName = "buildkit-java-load:" + UUID.randomUUID().toString().substring(0, 12);
            DockerfileBuildRequest request = DockerfileBuildRequest.builder(contextDir, dockerfile, imageName)
                    .outputMode(BuildOutputMode.DOCKER)
                    .build();

            Path exportedArchive;
            try (BuildkitClient client = new BuildkitClient(config)) {
                BuildResult result = client.buildImage(request, BuildProgressListener.NOOP);
                exportedArchive = result.exportedArchive();
                assertThat(exportedArchive).isNotNull();
                assertThat(Files.size(exportedArchive)).isGreaterThan(0L);
            }

            try {
                runDockerCommand("load", "-i", exportedArchive.toString());
                String inspectOutput = runDockerCommand("image", "inspect", imageName, "--format", "{{.RepoTags}}");
                assertThat(inspectOutput).contains(imageName);
            } finally {
                Files.deleteIfExists(exportedArchive);
                runDockerCommandIgnoringFailure("image", "rm", imageName);
            }
        }
    }

    private static String runDockerCommand(String... args) throws Exception {
        Process process = new ProcessBuilder(commandWithDocker(args))
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        assertThat(exitCode)
                .withFailMessage("docker %s failed: %s", String.join(" ", args), output)
                .isZero();
        return output;
    }

    private static void runDockerCommandIgnoringFailure(String... args) throws Exception {
        Process process = new ProcessBuilder(commandWithDocker(args))
                .redirectErrorStream(true)
                .start();
        process.getInputStream().readAllBytes();
        process.waitFor();
    }

    private static String[] commandWithDocker(String... args) {
        String[] command = new String[args.length + 1];
        command[0] = "docker";
        System.arraycopy(args, 0, command, 1, args.length);
        return command;
    }
}
