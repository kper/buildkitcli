package io.github.kper.buildkitcli.lib.internal.channel;

import io.github.kper.buildkitcli.lib.BuildkitConnectionConfig;
import io.github.kper.buildkitcli.lib.TlsConfig;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;

public final class BuildkitChannelFactory {
    private static final int MAX_MESSAGE_SIZE = 32 * 1024 * 1024;

    private BuildkitChannelFactory() {}

    public static ChannelResources create(BuildkitConnectionConfig config) throws IOException {
        URI address = config.address();
        return switch (address.getScheme().toLowerCase(Locale.ROOT)) {
            case "unix" -> createUnixChannel(config);
            case "tcp" -> createTcpChannel(config);
            default -> throw new IllegalArgumentException("Unsupported BuildKit address scheme: " + address.getScheme());
        };
    }

    private static ChannelResources createUnixChannel(BuildkitConnectionConfig config) {
        EventLoopGroup eventLoopGroup;
        NettyChannelBuilder builder;
        if (KQueue.isAvailable()) {
            eventLoopGroup = new KQueueEventLoopGroup(1);
            builder = NettyChannelBuilder.forAddress(new DomainSocketAddress(requireUnixPath(config.address()).toFile()))
                    .channelType(KQueueDomainSocketChannel.class)
                    .eventLoopGroup(eventLoopGroup);
        } else if (Epoll.isAvailable()) {
            eventLoopGroup = new EpollEventLoopGroup(1);
            builder = NettyChannelBuilder.forAddress(new DomainSocketAddress(requireUnixPath(config.address()).toFile()))
                    .channelType(EpollDomainSocketChannel.class)
                    .eventLoopGroup(eventLoopGroup);
        } else {
            throw new IllegalStateException("Unix domain sockets require kqueue or epoll support");
        }

        ManagedChannel channel = builder
                .usePlaintext()
                .overrideAuthority("localhost")
                .maxInboundMessageSize(MAX_MESSAGE_SIZE)
                .build();
        return new ChannelResources(channel, eventLoopGroup);
    }

    private static ChannelResources createTcpChannel(BuildkitConnectionConfig config) throws IOException {
        URI address = config.address();
        if (address.getHost() == null || address.getPort() < 0) {
            throw new IllegalArgumentException("tcp address must include host and port");
        }

        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(new InetSocketAddress(address.getHost(), address.getPort()))
                .maxInboundMessageSize(MAX_MESSAGE_SIZE);

        TlsConfig tlsConfig = config.tlsConfig();
        if (tlsConfig == null) {
            builder.usePlaintext();
        } else {
            SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient();
            if (tlsConfig.caCert() != null) {
                sslContextBuilder.trustManager(tlsConfig.caCert().toFile());
            }
            if (tlsConfig.clientCert() != null) {
                sslContextBuilder.keyManager(tlsConfig.clientCert().toFile(), tlsConfig.clientKey().toFile());
            }
            SslContext sslContext = sslContextBuilder.build();
            builder.sslContext(sslContext);
            if (tlsConfig.serverNameOverride() != null && !tlsConfig.serverNameOverride().isBlank()) {
                builder.overrideAuthority(tlsConfig.serverNameOverride());
            }
        }

        return new ChannelResources(builder.build(), null);
    }

    private static Path requireUnixPath(URI address) {
        String path = address.getPath();
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("unix address must include a socket path");
        }
        return Path.of(path);
    }
}
