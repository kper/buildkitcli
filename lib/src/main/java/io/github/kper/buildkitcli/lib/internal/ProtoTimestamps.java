package io.github.kper.buildkitcli.lib.internal;

import com.google.protobuf.Timestamp;

import java.time.Instant;

/**
 * Abstraction for timestamps.
 */
public final class ProtoTimestamps {
    private ProtoTimestamps() {
    }

    /**
     * Transform a {@link Timestamp} to an {@link Instant}.
     */
    public static Instant toInstant(Timestamp value) {
        if (value == null || (value.getSeconds() == 0 && value.getNanos() == 0)) {
            return null;
        }
        return Instant.ofEpochSecond(value.getSeconds(), value.getNanos());
    }
}
