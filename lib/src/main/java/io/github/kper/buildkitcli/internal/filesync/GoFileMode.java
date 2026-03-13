package io.github.kper.buildkitcli.internal.filesync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

final class GoFileMode {
    private static final int MODE_DIR = 1 << 31;
    private static final int MODE_SYMLINK = 1 << 27;

    private GoFileMode() {}

    static int from(Path path, BasicFileAttributes attributes) throws IOException {
        int permissions = 0644;
        try {
            Object unixMode = Files.getAttribute(path, "unix:mode", LinkOption.NOFOLLOW_LINKS);
            if (unixMode instanceof Number number) {
                permissions = number.intValue() & 0777;
            }
        } catch (UnsupportedOperationException ignored) {
        }

        int mode = permissions;
        if (attributes.isDirectory()) {
            mode |= MODE_DIR;
        } else if (attributes.isSymbolicLink()) {
            mode |= MODE_SYMLINK;
        }
        return mode;
    }
}
