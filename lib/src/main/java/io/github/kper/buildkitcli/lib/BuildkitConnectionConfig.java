package io.github.kper.buildkitcli.lib;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for the client.
 *
 * @param address   where the daemon listens to.
 * @param timeout   is the deadline during the building of images. Exceeding the timeout triggers an exception.
 * @param tlsConfig
 */
public record BuildkitConnectionConfig(URI address, Duration timeout, TlsConfig tlsConfig) {
    /**
     * Constructor.
     */
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

    /**
     * Create a configuration based on the address, e.g. {@code tcp://localhost:1234}.
     */
    public static BuildkitConnectionConfig of(String address) {
        return new BuildkitConnectionConfig(URI.create(address), Duration.ofMinutes(10), null);
    }

    /**
     * Creates a configuration with the existing {@code address} and the given {@code newTimeout}.
     */
    public BuildkitConnectionConfig withTimeout(Duration newTimeout) {
        return new BuildkitConnectionConfig(address, newTimeout, tlsConfig);
    }

    /**
     * Creates a configuration with the existing {@code address}, {@code timeout} and the given {@code newTlsConfig}.
     */
    public BuildkitConnectionConfig withTlsConfig(TlsConfig newTlsConfig) {
        return new BuildkitConnectionConfig(address, timeout, newTlsConfig);
    }
}
