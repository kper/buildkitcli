package io.github.kper.buildkitcli.lib.internal.filesync;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;

final class FileSyncPatternMatcher {
    private final List<String> includePatterns;
    private final List<String> excludePatterns;

    FileSyncPatternMatcher(FileSyncRequestOptions options) {
        this.includePatterns = options.includePatterns();
        this.excludePatterns = options.excludePatterns();
    }

    boolean shouldDescend(String relativePath) {
        if (!relativePath.isEmpty() && isExcluded(relativePath, true)) {
            return false;
        }
        if (includePatterns.isEmpty() || relativePath.isEmpty()) {
            return true;
        }
        String normalized = normalize(relativePath);
        return includePatterns.stream().map(FileSyncPatternMatcher::normalize).anyMatch(pattern ->
                pattern.equals(normalized)
                        || pattern.startsWith(normalized + "/")
                        || matchesPattern(pattern, normalized, true));
    }

    boolean shouldIncludeFile(String relativePath) {
        if (isExcluded(relativePath, false)) {
            return false;
        }
        if (includePatterns.isEmpty()) {
            return true;
        }
        return includePatterns.stream().anyMatch(pattern -> matchesPattern(pattern, relativePath, false));
    }

    private boolean isExcluded(String relativePath, boolean directory) {
        return excludePatterns.stream().anyMatch(pattern -> matchesPattern(pattern, relativePath, directory));
    }

    private static boolean matchesPattern(String pattern, String relativePath, boolean directory) {
        String normalizedPattern = normalize(pattern);
        String normalizedPath = normalize(relativePath);

        if (normalizedPattern.isEmpty()) {
            return false;
        }

        if (normalizedPattern.endsWith("/")) {
            String prefix = normalizedPattern.substring(0, normalizedPattern.length() - 1);
            return directory && (normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/"));
        }

        if (!normalizedPattern.contains("/")) {
            return basenameMatches(normalizedPattern, normalizedPath);
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);
        return matcher.matches(Path.of(normalizedPath.isEmpty() ? "." : normalizedPath));
    }

    private static boolean basenameMatches(String pattern, String relativePath) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        if (relativePath.isEmpty()) {
            return false;
        }
        String[] segments = relativePath.split("/");
        for (String segment : segments) {
            if (matcher.matches(Path.of(segment))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        String normalized = value.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
