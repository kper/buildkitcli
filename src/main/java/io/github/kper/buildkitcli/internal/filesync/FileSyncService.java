package io.github.kper.buildkitcli.internal.filesync;

import com.google.protobuf.ByteString;
import fsutil.types.StatOuterClass;
import fsutil.types.Wire;
import io.github.kper.buildkitcli.internal.session.SessionMetadata;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import moby.filesync.v1.FileSyncGrpc;

public final class FileSyncService extends FileSyncGrpc.FileSyncImplBase {
    private final Map<String, Path> sharedDirs;

    public FileSyncService(Map<String, Path> sharedDirs) {
        this.sharedDirs = Map.copyOf(sharedDirs);
    }

    @Override
    public StreamObserver<Wire.Packet> diffCopy(StreamObserver<Wire.Packet> responseObserver) {
        Metadata metadata = SessionMetadata.current();
        FileSyncRequestOptions options = FileSyncRequestOptions.fromMetadata(metadata == null ? new Metadata() : metadata);
        Path dir = sharedDirs.get(options.dirName());
        if (dir == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("no access allowed to dir \"" + options.dirName() + "\"")
                    .asRuntimeException());
            return new NoopStreamObserver();
        }
        return new DiffCopyStream(dir, options, responseObserver);
    }

    private static final class DiffCopyStream implements StreamObserver<Wire.Packet> {
        private final Path root;
        private final FileSyncPatternMatcher matcher;
        private final StreamObserver<Wire.Packet> responseObserver;
        private final Map<Integer, Path> filesById = new ConcurrentHashMap<>();
        private final ExecutorService streamExecutor = Executors.newSingleThreadExecutor();
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private int nextId;

        private DiffCopyStream(Path root, FileSyncRequestOptions options, StreamObserver<Wire.Packet> responseObserver) {
            this.root = root;
            this.matcher = new FileSyncPatternMatcher(options);
            this.responseObserver = responseObserver;
            streamExecutor.submit(this::walkAndSend);
        }

        @Override
        public void onNext(Wire.Packet packet) {
            if (closed.get()) {
                return;
            }
            streamExecutor.submit(() -> {
                if (closed.get()) {
                    return;
                }
                try {
                    switch (packet.getType()) {
                        case PACKET_REQ -> sendRequestedFile(packet.getID());
                        case PACKET_FIN -> finish();
                        case PACKET_ERR -> fail(new IOException("receiver error: " + packet.getData().toStringUtf8()));
                        default -> {
                        }
                    }
                } catch (IOException e) {
                    fail(e);
                }
            });
        }

        @Override
        public void onError(Throwable t) {
            fail(t);
        }

        @Override
        public void onCompleted() {
            streamExecutor.submit(this::closeIfUnfinished);
        }

        private void walkAndSend() {
            try {
                sendDirectory(Path.of(""), root, false);
                sendPacket(Wire.Packet.newBuilder().setType(Wire.Packet.PacketType.PACKET_STAT).build());
            } catch (IOException e) {
                fail(e);
            }
        }

        private void sendDirectory(Path relativePath, Path directory, boolean emitCurrentDirectory) throws IOException {
            String normalized = normalize(relativePath);
            if (!normalized.isEmpty() && !matcher.shouldDescend(normalized)) {
                return;
            }

            if (emitCurrentDirectory) {
                sendStat(relativePath, directory);
            }

            List<Path> children = new ArrayList<>();
            try (var stream = Files.list(directory)) {
                stream.sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .forEach(children::add);
            }

            for (Path child : children) {
                if (closed.get()) {
                    return;
                }

                Path childRelative = relativePath.resolve(child.getFileName().toString());
                BasicFileAttributes attributes = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isDirectory()) {
                    sendDirectory(childRelative, child, true);
                } else if (isSupported(attributes) && matcher.shouldIncludeFile(normalize(childRelative))) {
                    sendStat(childRelative, child);
                } else if (!isSupported(attributes)) {
                    throw new IOException("unsupported file type in build context: " + child);
                }
            }
        }

        private void sendStat(Path relativePath, Path file) throws IOException {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            StatOuterClass.Stat.Builder stat = StatOuterClass.Stat.newBuilder()
                    .setPath(toWirePath(relativePath))
                    .setMode(GoFileMode.from(file, attributes))
                    .setSize(attributes.isDirectory() ? 0L : attributes.size())
                    .setModTime(attributes.lastModifiedTime().toInstant().toEpochMilli() * 1_000_000L);

            try {
                Object uid = Files.getAttribute(file, "unix:uid", LinkOption.NOFOLLOW_LINKS);
                Object gid = Files.getAttribute(file, "unix:gid", LinkOption.NOFOLLOW_LINKS);
                if (uid instanceof Number uidNumber) {
                    stat.setUid(uidNumber.intValue());
                }
                if (gid instanceof Number gidNumber) {
                    stat.setGid(gidNumber.intValue());
                }
            } catch (UnsupportedOperationException ignored) {
            }

            if (attributes.isSymbolicLink()) {
                stat.setLinkname(Files.readSymbolicLink(file).toString().replace('\\', '/'));
            }

            int id = nextId++;
            if (attributes.isRegularFile()) {
                filesById.put(id, file);
            }

            sendPacket(Wire.Packet.newBuilder()
                    .setType(Wire.Packet.PacketType.PACKET_STAT)
                    .setStat(stat)
                    .build());
        }

        private void sendRequestedFile(int id) throws IOException {
            Path file = filesById.remove(id);
            if (file == null) {
                throw new IOException("invalid file id " + id);
            }

            try (InputStream inputStream = Files.newInputStream(file)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = inputStream.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    sendPacket(Wire.Packet.newBuilder()
                            .setType(Wire.Packet.PacketType.PACKET_DATA)
                            .setID(id)
                            .setData(ByteString.copyFrom(buffer, 0, read))
                            .build());
                }
            }

            sendPacket(Wire.Packet.newBuilder()
                    .setType(Wire.Packet.PacketType.PACKET_DATA)
                    .setID(id)
                    .build());
        }

        private void finish() throws IOException {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            sendPacket(Wire.Packet.newBuilder().setType(Wire.Packet.PacketType.PACKET_FIN).build());
            responseObserver.onCompleted();
            streamExecutor.shutdown();
        }

        private void fail(Throwable throwable) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            responseObserver.onError(Status.INTERNAL.withDescription(throwable.getMessage()).withCause(throwable).asRuntimeException());
            streamExecutor.shutdownNow();
        }

        private void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            streamExecutor.shutdownNow();
        }

        private void sendPacket(Wire.Packet packet) {
            responseObserver.onNext(packet);
        }

        private void closeIfUnfinished() {
            if (closed.get()) {
                return;
            }
            close();
        }

        private static boolean isSupported(BasicFileAttributes attributes) {
            return attributes.isRegularFile() || attributes.isDirectory() || attributes.isSymbolicLink();
        }

        private static String normalize(Path relativePath) {
            return relativePath.toString().replace('\\', '/');
        }

        private static String toWirePath(Path relativePath) {
            return normalize(relativePath);
        }
    }

    private static final class NoopStreamObserver implements StreamObserver<Wire.Packet> {
        @Override
        public void onNext(Wire.Packet value) {}

        @Override
        public void onError(Throwable t) {}

        @Override
        public void onCompleted() {}
    }
}
