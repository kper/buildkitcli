package io.github.kper.buildkitcli.cli;

import io.github.kper.buildkitcli.lib.BuildOutputMode;
import io.github.kper.buildkitcli.lib.BuildResult;
import io.github.kper.buildkitcli.lib.BuildkitClient;
import io.github.kper.buildkitcli.lib.BuildkitConnectionConfig;
import io.github.kper.buildkitcli.lib.DockerfileBuildRequest;
import io.github.kper.buildkitcli.lib.TlsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BuildkitCli {
    public static void main(String[] args) throws Exception {
        Logger logger = LoggerFactory.getLogger(BuildkitCli.class);

        Map<String, String> options = new HashMap<>();
        List<String> buildArgs = new ArrayList<>();
        boolean push = false;
        boolean load = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--push" -> push = true;
                case "--load" -> load = true;
                case "--build-arg" -> buildArgs.add(args[++i]);
                default -> {
                    if (arg.startsWith("--")) {
                        options.put(arg.substring(2), args[++i]);
                    } else {
                        usageAndExit();
                    }
                }
            }
        }

        require(options, "addr");
        require(options, "context");
        require(options, "dockerfile");
        require(options, "image");
        if (push && load) {
            throw new IllegalArgumentException("--push and --load cannot be used together");
        }

        BuildkitConnectionConfig connectionConfig =
                new BuildkitConnectionConfig(
                        java.net.URI.create(options.get("addr")),
                        Duration.ofMinutes(10),
                        createTlsConfig(options));

        DockerfileBuildRequest.Builder requestBuilder = DockerfileBuildRequest.builder(
                        Path.of(options.get("context")),
                        Path.of(options.get("dockerfile")),
                        options.get("image"))
                .push(push)
                .outputMode(load ? BuildOutputMode.DOCKER : BuildOutputMode.IMAGE);

        for (String buildArg : buildArgs) {
            String[] parts = buildArg.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid build arg: " + buildArg);
            }
            requestBuilder.buildArg(parts[0], parts[1]);
        }

        try (BuildkitClient client = new BuildkitClient(connectionConfig)) {
            BuildResult result = client.buildImage(requestBuilder.build(), new io.github.kper.buildkitcli.lib.PrintingListener());
            if (load) {
                loadIntoDocker(result);
            }
            logger.atInfo().log("ref=" + result.ref());
            if (result.imageDigest() != null) {
                logger.atInfo().log("digest=" + result.imageDigest());
            }
        }
    }

    private static TlsConfig createTlsConfig(Map<String, String> options) {
        String ca = options.get("tlscacert");
        String cert = options.get("tlscert");
        String key = options.get("tlskey");
        String serverName = options.get("tlsservername");
        if (ca == null && cert == null && key == null && serverName == null) {
            return null;
        }
        return new TlsConfig(
                ca == null ? null : Path.of(ca),
                cert == null ? null : Path.of(cert),
                key == null ? null : Path.of(key),
                serverName);
    }

    private static void require(Map<String, String> options, String key) {
        if (!options.containsKey(key)) {
            usageAndExit();
        }
    }

    private static void usageAndExit() {
        throw new IllegalArgumentException(
                "Usage: --addr ADDR --context DIR --dockerfile FILE --image NAME [--push|--load] [--build-arg KEY=VALUE] "
                        + "[--tlscacert FILE --tlscert FILE --tlskey FILE --tlsservername NAME]");
    }

    private static void loadIntoDocker(BuildResult result) throws Exception {
        Path exportedArchive = result.exportedArchive();
        if (exportedArchive == null) {
            throw new IllegalStateException("Build result did not include a docker archive");
        }
        try {
            Process process = new ProcessBuilder("docker", "load", "-i", exportedArchive.toString())
                    .inheritIO()
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("docker load failed with exit code " + exitCode);
            }
        } finally {
            Files.deleteIfExists(exportedArchive);
        }
    }
}
