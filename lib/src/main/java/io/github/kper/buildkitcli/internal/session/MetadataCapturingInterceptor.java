package io.github.kper.buildkitcli.internal.session;

import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

public final class MetadataCapturingInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> io.grpc.ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        return Contexts.interceptCall(SessionMetadata.withMetadata(io.grpc.Context.current(), headers), call, headers, next);
    }
}
