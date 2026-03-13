package io.github.kper.buildkitcli.lib.internal.filesync;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kper.buildkitcli.lib.internal.session.MetadataCapturingInterceptor;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyServerBuilder;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoFsutilCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void isCompatibleWithGoFsutilReceiver() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("buildkit.goCompat"), "Go compatibility check not requested");
        Assumptions.assumeTrue(commandSucceeds("go", "version"), "Go toolchain is not available");

        Path contextDir = Files.createDirectory(tempDir.resolve("context"));
        Files.writeString(contextDir.resolve("Dockerfile.custom"), "FROM scratch\nCOPY hello.txt /hello.txt\n");
        Files.writeString(contextDir.resolve("hello.txt"), "hello\n");
        Path receivedDir = Files.createDirectory(tempDir.resolve("received"));

        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        Server server = NettyServerBuilder.forAddress(address)
                .addService(ServerInterceptors.intercept(
                        new FileSyncService(Map.of("context", contextDir)),
                        new MetadataCapturingInterceptor()))
                .build()
                .start();

        try {
            InetSocketAddress boundAddress = (InetSocketAddress) server.getListenSockets().getFirst();
            Path goModuleDir = Files.createDirectory(tempDir.resolve("gomodule"));
            Files.writeString(goModuleDir.resolve("go.mod"), """
                    module compatcheck

                    go 1.24

                    require (
                        github.com/moby/buildkit v0.28.0
                        github.com/tonistiigi/fsutil v0.0.0-20250605211050-586307ad4527
                        google.golang.org/grpc v1.76.0
                    )
                    """);
            Files.writeString(goModuleDir.resolve("main.go"), """
                    package main

                    import (
                        "context"
                        "fmt"
                        "os"

                        "github.com/tonistiigi/fsutil"
                        "github.com/moby/buildkit/session/filesync"
                        "google.golang.org/grpc"
                        "google.golang.org/grpc/credentials/insecure"
                        "google.golang.org/grpc/metadata"
                    )

                    func main() {
                        if len(os.Args) != 3 {
                            panic("usage: main addr dest")
                        }
                        conn, err := grpc.Dial(os.Args[1], grpc.WithTransportCredentials(insecure.NewCredentials()))
                        if err != nil {
                            panic(err)
                        }
                        defer conn.Close()

                        client := filesync.NewFileSyncClient(conn)
                        ctx := metadata.NewOutgoingContext(context.Background(), metadata.Pairs("dir-name", "context"))
                        stream, err := client.DiffCopy(ctx)
                        if err != nil {
                            panic(err)
                        }
                        if err := fsutil.Receive(ctx, stream, os.Args[2], fsutil.ReceiveOpt{}); err != nil {
                            panic(err)
                        }
                        fmt.Println("ok")
                    }
                    """);

            Process process = new ProcessBuilder(
                            "go",
                            "run",
                            ".",
                            boundAddress.getHostString() + ":" + boundAddress.getPort(),
                            receivedDir.toString())
                    .directory(goModuleDir.toFile())
                    .inheritIO()
                    .start();

            int exitCode = process.waitFor();
            assertThat(exitCode).isZero();
            assertThat(Files.readString(receivedDir.resolve("Dockerfile.custom")))
                    .isEqualTo("FROM scratch\nCOPY hello.txt /hello.txt\n");
            assertThat(Files.readString(receivedDir.resolve("hello.txt"))).isEqualTo("hello\n");
        } finally {
            server.shutdownNow();
            server.awaitTermination();
        }
    }

    private static boolean commandSucceeds(String... command) {
        try {
            Process process = new ProcessBuilder(command).start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
