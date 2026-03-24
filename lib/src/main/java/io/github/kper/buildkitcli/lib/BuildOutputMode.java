package io.github.kper.buildkitcli.lib;

/**
 * Defines how the build is exported.
 */
public enum BuildOutputMode {
    IMAGE("image", false, true, true),
    DOCKER("docker", true, true, false),
    LOCAL("tar", true, false, false);

    private final String exporterType;
    private final boolean usesSessionExporter;
    private final boolean requiresImageName;
    private final boolean supportsPush;

    /**
     * Constructor.
     */
    BuildOutputMode(
            String exporterType,
            boolean usesSessionExporter,
            boolean requiresImageName,
            boolean supportsPush) {
        this.exporterType = exporterType;
        this.usesSessionExporter = usesSessionExporter;
        this.requiresImageName = requiresImageName;
        this.supportsPush = supportsPush;
    }

    public String exporterType() {
        return exporterType;
    }

    public boolean usesSessionExporter() {
        return usesSessionExporter;
    }

    public boolean requiresImageName() {
        return requiresImageName;
    }

    public boolean supportsPush() {
        return supportsPush;
    }
}
