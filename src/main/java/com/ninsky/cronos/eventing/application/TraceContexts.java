package com.ninsky.cronos.eventing.application;

import org.slf4j.MDC;

import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import com.ninsky.cronos.eventing.domain.TimeOrderedIds;

/**
 * Thread-bound {@link TraceContext}: set by the request filter, by the outbox relay before it invokes a
 * handler, and copied into async executors by the task decorator. Mirrors the ids into the logging MDC.
 */
public final class TraceContexts {

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_CAUSATION_ID = "causationId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_SESSION_ID = "sessionId";

    private static final ThreadLocal<TraceContext> CURRENT = new ThreadLocal<>();

    private TraceContexts() {
    }

    /** The context bound to this thread, if any. */
    public static Optional<TraceContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** The bound context, or a fresh root context for work that started outside a request (jobs, startup). */
    public static TraceContext currentOrNew(Clock clock) {
        TraceContext context = CURRENT.get();
        return context != null ? context : newRoot(clock);
    }

    /** A new chain: new correlation id and trace id, no actor. */
    public static TraceContext newRoot(Clock clock) {
        return new TraceContext(TimeOrderedIds.next(clock), null, newTraceId(), null, null);
    }

    /** Binds {@code context} (and its MDC keys) to the current thread until {@link #clear()}. */
    public static void bind(TraceContext context) {
        CURRENT.set(context);
        put(MDC_TRACE_ID, context.traceId());
        put(MDC_CORRELATION_ID, context.correlationId());
        put(MDC_CAUSATION_ID, context.causationId());
        put(MDC_USER_ID, context.actorId());
        put(MDC_SESSION_ID, context.sessionId());
    }

    /** Removes the context and its MDC keys from the current thread. */
    public static void clear() {
        CURRENT.remove();
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_CORRELATION_ID);
        MDC.remove(MDC_CAUSATION_ID);
        MDC.remove(MDC_USER_ID);
        MDC.remove(MDC_SESSION_ID);
    }

    /** Runs {@code work} with {@code context} bound, restoring whatever was bound before. */
    public static <T> T callWith(TraceContext context, Supplier<T> work) {
        TraceContext previous = CURRENT.get();
        bind(context);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                clear();
            } else {
                bind(previous);
            }
        }
    }

    /** {@link #callWith} for work without a result. */
    public static void runWith(TraceContext context, Runnable work) {
        callWith(context, () -> {
            work.run();
            return null;
        });
    }

    /** Wraps {@code task} so it runs with the context captured now (used by async executors). */
    public static <T> Callable<T> propagate(Callable<T> task) {
        TraceContext captured = CURRENT.get();
        if (captured == null) {
            return task;
        }
        return () -> {
            TraceContext previous = CURRENT.get();
            bind(captured);
            try {
                return task.call();
            } finally {
                if (previous == null) {
                    clear();
                } else {
                    bind(previous);
                }
            }
        };
    }

    /** A W3C trace id: 16 random bytes as 32 lower-case hex digits. */
    public static String newTraceId() {
        byte[] bytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static void put(String key, Object value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value.toString());
        }
    }
}
