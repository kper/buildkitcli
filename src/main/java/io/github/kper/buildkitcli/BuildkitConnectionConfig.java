package io.github.kper.buildkitcli;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public record BuildkitConnectionConfig(URI address, Duration timeout, TlsConfig tlsConfig) {
    public BuildkitConnectionConfig {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(timeout, "timeout");
        if (address.getScheme() == null || address.getScheme().isBlank()) {
            throw new IllegalArgumentException("address must include a scheme");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public static BuildkitConnectionConfig of(String address) {
        return new BuildkitConnectionConfig(URI.create(address), Duration.ofMinutes(10), null);
    }

    public BuildkitConnectionConfig withTimeout(Duration newTimeout) {
        return new BuildkitConnectionConfig(address, newTimeout, tlsConfig);
    }

    public BuildkitConnectionConfig withTlsConfig(TlsConfig newTlsConfig) {
        return new BuildkitConnectionConfig(address, timeout, newTlsConfig);
    }
}
