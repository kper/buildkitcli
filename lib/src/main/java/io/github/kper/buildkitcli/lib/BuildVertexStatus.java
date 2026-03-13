package io.github.kper.buildkitcli.lib;

import java.time.Instant;

public record BuildVertexStatus(
        String id,
        String vertex,
        String name,
        long current,
        long total,
        Instant timestamp,
        Instant started,
        Instant completed) {}
