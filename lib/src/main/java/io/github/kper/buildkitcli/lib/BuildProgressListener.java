package io.github.kper.buildkitcli.lib;

/**
 * Interface to report the build progress in the client.
 */
public interface BuildProgressListener {
    BuildProgressListener NOOP = new BuildProgressListener() {
    };

    default void onVertex(BuildVertex vertex) {
    }

    default void onVertexStatus(BuildVertexStatus status) {
    }

    default void onLog(BuildLog log) {
    }

    default void onWarning(BuildWarning warning) {
    }
}
