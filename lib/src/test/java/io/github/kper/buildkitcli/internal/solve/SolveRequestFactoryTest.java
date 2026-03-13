package io.github.kper.buildkitcli.internal.solve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kper.buildkitcli.BuildOutputMode;
import io.github.kper.buildkitcli.DockerfileBuildRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SolveRequestFactoryTest {
    @TempDir
    Path tempDir;

    @Test
    void createsSolveRequestForNonDefaultDockerfile() throws Exception {
        Path contextDir = Files.createDirectory(tempDir.resolve("context"));
        Path dockerfileDir = Files.createDirectory(tempDir.resolve("docker"));
        Files.writeString(contextDir.resolve("hello.txt"), "hello");
        Path dockerfile = Files.writeString(dockerfileDir.resolve("Dockerfile.custom"), "FROM scratch\nCOPY hello.txt /hello.txt\n");

        DockerfileBuildRequest request = DockerfileBuildRequest.builder(contextDir, dockerfile, "example.com/test:latest")
                .push(true)
                .buildArg("FOO", "bar")
                .target("release")
                .build();

        var solveRequest = SolveRequestFactory.create("ref123", "session123", request);

        assertThat(solveRequest.getRef()).isEqualTo("ref123");
        assertThat(solveRequest.getSession()).isEqualTo("session123");
        assertThat(solveRequest.getFrontend()).isEqualTo("dockerfile.v0");
        assertThat(solveRequest.getFrontendAttrsMap())
                .containsEntry("filename", "Dockerfile.custom")
                .containsEntry("target", "release")
                .containsEntry("build-arg:FOO", "bar");
        assertThat(solveRequest.getExportersList()).singleElement().satisfies(exporter -> {
            assertThat(exporter.getType()).isEqualTo("image");
            assertThat(exporter.getAttrsMap()).containsEntry("name", "example.com/test:latest");
            assertThat(exporter.getAttrsMap()).containsEntry("push", "true");
        });
        assertThat(solveRequest.getExporterDeprecated()).isEqualTo("image");
        assertThat(solveRequest.getExporterAttrsDeprecatedMap()).containsEntry("name", "example.com/test:latest");
    }

    @Test
    void rejectsReservedOverrides() throws Exception {
        Path contextDir = Files.createDirectory(tempDir.resolve("context2"));
        Path dockerfile = Files.writeString(tempDir.resolve("Dockerfile"), "FROM scratch\n");

        DockerfileBuildRequest request = DockerfileBuildRequest.builder(contextDir, dockerfile, "example.com/test:latest")
                .frontendAttr("filename", "Override")
                .build();

        assertThatThrownBy(() -> SolveRequestFactory.create("ref", "session", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved key");
    }

    @Test
    void createsSolveRequestForDockerExporter() throws Exception {
        Path contextDir = Files.createDirectory(tempDir.resolve("context3"));
        Path dockerfile = Files.writeString(tempDir.resolve("Dockerfile.load"), "FROM scratch\n");

        DockerfileBuildRequest request = DockerfileBuildRequest.builder(contextDir, dockerfile, "local/test:load")
                .outputMode(BuildOutputMode.DOCKER)
                .build();

        var solveRequest = SolveRequestFactory.create("ref456", "session456", request);

        assertThat(solveRequest.getExportersList()).singleElement().satisfies(exporter -> {
            assertThat(exporter.getType()).isEqualTo("docker");
            assertThat(exporter.getAttrsMap()).containsEntry("name", "local/test:load");
        });
        assertThat(solveRequest.getEnableSessionExporter()).isTrue();
        assertThat(solveRequest.getExporterDeprecated()).isEmpty();
        assertThat(solveRequest.getExporterAttrsDeprecatedMap()).isEmpty();
    }

    @Test
    void rejectsPushWithDockerOutputMode() throws Exception {
        Path contextDir = Files.createDirectory(tempDir.resolve("context4"));
        Path dockerfile = Files.writeString(tempDir.resolve("Dockerfile.push"), "FROM scratch\n");

        assertThatThrownBy(() -> DockerfileBuildRequest.builder(contextDir, dockerfile, "local/test:push")
                        .outputMode(BuildOutputMode.DOCKER)
                        .push(true)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("push is not supported");
    }
}
