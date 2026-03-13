package io.github.kper.buildkit;

import java.nio.file.Path;
import java.util.Map;

public record BuildResult(
        String ref,
        Map<String, String> exporterResponse,
        String imageDigest,
        String imageDescriptorJson,
        Path exportedArchive) {

    public BuildResult {
        exporterResponse = Map.copyOf(exporterResponse);
    }
}
