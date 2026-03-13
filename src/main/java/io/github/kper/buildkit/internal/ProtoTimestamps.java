package io.github.kper.buildkit.internal;

import com.google.protobuf.Timestamp;
import java.time.Instant;

public final class ProtoTimestamps {
    private ProtoTimestamps() {}

    public static Instant toInstant(Timestamp value) {
        if (value == null || (value.getSeconds() == 0 && value.getNanos() == 0)) {
            return null;
        }
        return Instant.ofEpochSecond(value.getSeconds(), value.getNanos());
    }
}
