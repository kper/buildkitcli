package io.github.kper.buildkitcli;

public enum BuildOutputMode {
    IMAGE("image", false),
    DOCKER("docker", true);

    private final String exporterType;
    private final boolean usesSessionExporter;

    BuildOutputMode(String exporterType, boolean usesSessionExporter) {
        this.exporterType = exporterType;
        this.usesSessionExporter = usesSessionExporter;
    }

    public String exporterType() {
        return exporterType;
    }

    public boolean usesSessionExporter() {
        return usesSessionExporter;
    }
}
