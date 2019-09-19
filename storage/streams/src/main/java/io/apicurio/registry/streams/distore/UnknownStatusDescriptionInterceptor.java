package io.apicurio.registry.streams.distore;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * A gRPC {@link ServerInterceptor} that changes the {@link Status} of responses that have
 * {@link Status.Code#UNKNOWN} and don't have any {@link Status#getDescription()} but
 * do have the {@link Status#getCause()} such that the changed {@link Status} is obtained from
 * the provided map of {@code exception root classes -> statuses} augmented with the following:
 * <ul>
 * <li>{@link Status#withCause(Throwable) cause} is set to original status cause</li>
 * <li>{@link Status#withDescription(String) description} is set to original cause's stack trace</li>
 * </ul>
 */
public class UnknownStatusDescriptionInterceptor implements ServerInterceptor {

    private final Map<Class<? extends Throwable>, Status> throwableRootsStatuses;

    /**
     * @param throwableRootsStatuses the map of {@link Throwable} subclasses to {@link Status.Code}s
     */
    public UnknownStatusDescriptionInterceptor(Map<Class<? extends Throwable>, Status> throwableRootsStatuses) {
        this.throwableRootsStatuses = new HashMap<>(throwableRootsStatuses);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        ServerCall<ReqT, RespT> wrappedCall = new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            @Override
            public void sendMessage(RespT message) {
                super.sendMessage(message);
            }

            @Override
            public void close(Status status, Metadata trailers) {
                Throwable exception;
                Status newStatus;
                if (
                    status.getCode() == Status.Code.UNKNOWN
                    && status.getDescription() == null
                    && (exception = status.getCause()) != null
                    && (newStatus = statusForException(exception)) != null
                ) {
                    status = newStatus
                        .withCause(exception)
                        .withDescription(stacktraceToString(exception));
                }
                super.close(status, trailers);
            }
        };

        return next.startCall(wrappedCall, headers);
    }

    private Status statusForException(Throwable exception) {
        Class<?> excCls = exception.getClass();
        do {
            Status status = throwableRootsStatuses.get(excCls);
            if (status != null) return status;
            excCls = excCls.getSuperclass();
        } while (excCls != Object.class);
        return null;
    }

    private String stacktraceToString(Throwable e) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        e.printStackTrace(printWriter);
        return stringWriter.toString();
    }
}
