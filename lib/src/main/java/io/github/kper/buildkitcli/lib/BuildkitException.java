package io.github.kper.buildkitcli.lib;

public class BuildkitException extends Exception {
    public BuildkitException(String message) {
        super(message);
    }

    public BuildkitException(String message, Throwable cause) {
        super(message, cause);
    }
}
