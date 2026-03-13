package io.github.kper.buildkitcli;

import java.nio.file.Files;
import java.nio.file.Path;

public record TlsConfig(Path caCert, Path clientCert, Path clientKey, String serverNameOverride) {
    public TlsConfig {
        if (caCert != null && !Files.isReadable(caCert)) {
            throw new IllegalArgumentException("CA certificate is not readable: " + caCert);
        }
        if ((clientCert == null) != (clientKey == null)) {
            throw new IllegalArgumentException("clientCert and clientKey must be set together");
        }
        if (clientCert != null && !Files.isReadable(clientCert)) {
            throw new IllegalArgumentException("Client certificate is not readable: " + clientCert);
        }
        if (clientKey != null && !Files.isReadable(clientKey)) {
            throw new IllegalArgumentException("Client key is not readable: " + clientKey);
        }
    }
}
