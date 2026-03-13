package io.github.kper.buildkitcli.internal.filesync;

import io.grpc.Metadata;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record FileSyncRequestOptions(
        String dirName,
        List<String> includePatterns,
        List<String> excludePatterns,
        List<String> followPaths) {
    private static final String DIR_NAME = "dir-name";
    private static final String INCLUDE_PATTERNS = "include-patterns";
    private static final String EXCLUDE_PATTERNS = "exclude-patterns";
    private static final String FOLLOW_PATHS = "followpaths";

    public static FileSyncRequestOptions fromMetadata(Metadata metadata) {
        return new FileSyncRequestOptions(
                firstValue(metadata, DIR_NAME),
                decodeValues(metadata, INCLUDE_PATTERNS),
                decodeValues(metadata, EXCLUDE_PATTERNS),
                decodeValues(metadata, FOLLOW_PATHS));
    }

    private static String firstValue(Metadata metadata, String key) {
        List<String> values = decodeValues(metadata, key);
        return values.isEmpty() ? "" : values.getFirst();
    }

    private static List<String> decodeValues(Metadata metadata, String key) {
        Metadata.Key<String> metadataKey = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
        Iterable<String> rawValues = metadata.getAll(metadataKey);
        if (rawValues == null) {
            return List.of();
        }

        boolean encoded = false;
        Metadata.Key<String> encodedKey = Metadata.Key.of(key + "-encoded", Metadata.ASCII_STRING_MARSHALLER);
        Iterable<String> encodedValues = metadata.getAll(encodedKey);
        if (encodedValues != null) {
            for (String value : encodedValues) {
                if ("1".equals(value) || Boolean.parseBoolean(value)) {
                    encoded = true;
                    break;
                }
            }
        }

        List<String> values = new ArrayList<>();
        for (String rawValue : rawValues) {
            values.add(encoded ? URLDecoder.decode(rawValue, StandardCharsets.UTF_8) : rawValue);
        }
        return Collections.unmodifiableList(values);
    }
}
