package io.github.kper.buildkit;

public interface BuildProgressListener {
    BuildProgressListener NOOP = new BuildProgressListener() {};

    default void onVertex(BuildVertex vertex) {}

    default void onVertexStatus(BuildVertexStatus status) {}

    default void onLog(BuildLog log) {}

    default void onWarning(BuildWarning warning) {}
}
