package io.github.kper.buildkitcli.lib.internal.solve;

import io.github.kper.buildkitcli.lib.BuildOutputMode;
import io.github.kper.buildkitcli.lib.DockerfileBuildRequest;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import moby.buildkit.v1.ControlOuterClass;

/**
 * Factory to create {@link moby.buildkit.v1.ControlOuterClass.SolveRequest}.
 */
public final class SolveRequestFactory {
    private static final Set<String> RESERVED_FRONTEND_KEYS = Set.of("filename", "target", "no-cache");
    private static final Set<String> RESERVED_EXPORTER_KEYS = Set.of("name", "push");

    private SolveRequestFactory() {
    }

    /**
     * Create a request.
     */
    public static ControlOuterClass.SolveRequest create(String ref, String sessionId, DockerfileBuildRequest request) {
        Map<String, String> frontendAttrs = new LinkedHashMap<>();
        frontendAttrs.put("filename", dockerfileName(request.dockerfile()));
        if (request.target() != null && !request.target().isBlank()) {
            frontendAttrs.put("target", request.target());
        }
        if (request.noCache()) {
            frontendAttrs.put("no-cache", "");
        }
        request.buildArgs().forEach((key, value) -> frontendAttrs.put("build-arg:" + key, value));
        validateExtraFrontendAttrs(request.extraFrontendAttrs());
        frontendAttrs.putAll(request.extraFrontendAttrs());

        Map<String, String> exporterAttrs = new LinkedHashMap<>();
        exporterAttrs.put("name", request.imageName());
        if (request.push()) {
            exporterAttrs.put("push", "true");
        }
        validateExtraExporterAttrs(request.extraExporterAttrs());
        exporterAttrs.putAll(request.extraExporterAttrs());

        ControlOuterClass.Exporter exporter = ControlOuterClass.Exporter.newBuilder()
                .setType(request.outputMode().exporterType())
                .putAllAttrs(exporterAttrs)
                .build();

        ControlOuterClass.SolveRequest.Builder builder = ControlOuterClass.SolveRequest.newBuilder()
                .setRef(ref)
                .setSession(sessionId)
                .setFrontend("dockerfile.v0")
                .putAllFrontendAttrs(frontendAttrs)
                .addExporters(exporter)
                .setEnableSessionExporter(request.outputMode().usesSessionExporter());
        if (request.outputMode() == BuildOutputMode.IMAGE) {
            builder.setExporterDeprecated("image").putAllExporterAttrsDeprecated(exporterAttrs);
        }
        return builder.build();
    }

    private static String dockerfileName(Path dockerfile) {
        return dockerfile.getFileName().toString();
    }

    private static void validateExtraFrontendAttrs(Map<String, String> attrs) {
        for (String key : attrs.keySet()) {
            if (RESERVED_FRONTEND_KEYS.contains(key) || key.startsWith("build-arg:")) {
                throw new IllegalArgumentException("frontend attr overrides reserved key: " + key);
            }
        }
    }

    private static void validateExtraExporterAttrs(Map<String, String> attrs) {
        for (String key : attrs.keySet()) {
            if (RESERVED_EXPORTER_KEYS.contains(key)) {
                throw new IllegalArgumentException("exporter attr overrides reserved key: " + key);
            }
        }
    }
}
