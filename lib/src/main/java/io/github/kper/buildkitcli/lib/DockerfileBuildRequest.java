package io.github.kper.buildkitcli.lib;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Request to build docker image from a docker file.
 */
public final class DockerfileBuildRequest {
    private final Path contextDir;
    private final Path dockerfile;
    private final String imageName;
    private final boolean push;
    private final BuildOutputMode outputMode;
    private final Path localOutputDir;
    private final String target;
    private final Map<String, String> buildArgs;
    private final boolean noCache;
    private final Map<String, String> extraFrontendAttrs;
    private final Map<String, String> extraExporterAttrs;

    private DockerfileBuildRequest(Builder builder) {
        this.contextDir = builder.contextDir.toAbsolutePath().normalize();
        this.dockerfile = builder.dockerfile.toAbsolutePath().normalize();
        this.imageName = builder.imageName;
        this.push = builder.push;
        this.outputMode = builder.outputMode;
        this.localOutputDir = builder.localOutputDir == null ? null : builder.localOutputDir.toAbsolutePath().normalize();
        this.target = builder.target;
        this.buildArgs = Map.copyOf(builder.buildArgs);
        this.noCache = builder.noCache;
        this.extraFrontendAttrs = Map.copyOf(builder.extraFrontendAttrs);
        this.extraExporterAttrs = Map.copyOf(builder.extraExporterAttrs);
        validate();
    }

    public static Builder builder(Path contextDir, Path dockerfile, String imageName) {
        return new Builder(contextDir, dockerfile, imageName);
    }

    private void validate() {
        if (!Files.isDirectory(contextDir)) {
            throw new IllegalArgumentException("contextDir must be an existing directory: " + contextDir);
        }
        if (!Files.isRegularFile(dockerfile)) {
            throw new IllegalArgumentException("dockerfile must be an existing file: " + dockerfile);
        }
        if (outputMode.requiresImageName() && imageName.isBlank()) {
            throw new IllegalArgumentException("imageName must not be blank");
        }
        if (!outputMode.supportsPush() && push) {
            throw new IllegalArgumentException("push is not supported with " + outputMode.name().toLowerCase() + " output mode");
        }
        if (outputMode == BuildOutputMode.LOCAL && localOutputDir == null) {
            throw new IllegalArgumentException("localOutputDir must be set with local output mode");
        }
        if (outputMode != BuildOutputMode.LOCAL && localOutputDir != null) {
            throw new IllegalArgumentException("localOutputDir is only supported with local output mode");
        }
        if (localOutputDir != null && Files.exists(localOutputDir) && !Files.isDirectory(localOutputDir)) {
            throw new IllegalArgumentException("localOutputDir must be a directory when it exists: " + localOutputDir);
        }
    }

    public Path contextDir() {
        return contextDir;
    }

    public Path dockerfile() {
        return dockerfile;
    }

    public String imageName() {
        return imageName;
    }

    public boolean push() {
        return push;
    }

    public BuildOutputMode outputMode() {
        return outputMode;
    }

    public Path localOutputDir() {
        return localOutputDir;
    }

    public String target() {
        return target;
    }

    public Map<String, String> buildArgs() {
        return buildArgs;
    }

    public boolean noCache() {
        return noCache;
    }

    public Map<String, String> extraFrontendAttrs() {
        return extraFrontendAttrs;
    }

    public Map<String, String> extraExporterAttrs() {
        return extraExporterAttrs;
    }

    public static final class Builder {
        private final Path contextDir;
        private final Path dockerfile;
        private final String imageName;
        private boolean push;
        private BuildOutputMode outputMode = BuildOutputMode.IMAGE;
        private Path localOutputDir;
        private String target;
        private final Map<String, String> buildArgs = new LinkedHashMap<>();
        private boolean noCache;
        private final Map<String, String> extraFrontendAttrs = new LinkedHashMap<>();
        private final Map<String, String> extraExporterAttrs = new LinkedHashMap<>();

        private Builder(Path contextDir, Path dockerfile, String imageName) {
            this.contextDir = Objects.requireNonNull(contextDir, "contextDir");
            this.dockerfile = Objects.requireNonNull(dockerfile, "dockerfile");
            this.imageName = Objects.requireNonNull(imageName, "imageName");
        }

        public Builder push(boolean value) {
            this.push = value;
            return this;
        }

        public Builder outputMode(BuildOutputMode value) {
            this.outputMode = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder localOutputDir(Path value) {
            this.localOutputDir = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder target(String value) {
            this.target = value;
            return this;
        }

        public Builder buildArg(String key, String value) {
            buildArgs.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder buildArgs(Map<String, String> values) {
            values.forEach(this::buildArg);
            return this;
        }

        public Builder noCache(boolean value) {
            this.noCache = value;
            return this;
        }

        public Builder frontendAttr(String key, String value) {
            extraFrontendAttrs.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder exporterAttr(String key, String value) {
            extraExporterAttrs.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public DockerfileBuildRequest build() {
            return new DockerfileBuildRequest(this);
        }
    }
}
