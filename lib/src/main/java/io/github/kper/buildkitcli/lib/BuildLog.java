package io.github.kper.buildkitcli.lib;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;

public record BuildLog(String vertex, Instant timestamp, long stream, byte[] message) {
    public BuildLog {
        message = Arrays.copyOf(message, message.length);
    }

    public String utf8Message() {
        return new String(message, StandardCharsets.UTF_8);
    }
}
