package io.github.kper.buildkitcli.lib;

import java.nio.file.Path;
import java.util.Map;

/**
 * Build result datastructure.
 */
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
