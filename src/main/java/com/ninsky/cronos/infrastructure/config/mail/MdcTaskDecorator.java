package com.ninsky.cronos.infrastructure.config.mail;

import com.ninsky.cronos.eventing.application.TraceContext;
import com.ninsky.cronos.eventing.application.TraceContexts;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

import java.util.Map;

/**
 * Copies the calling thread's MDC (in particular {@code traceId}) onto the thread that actually
 * runs a task of the application executor, so asynchronous request work logs under the same traceId
 * as the HTTP request that triggered it. The {@link TraceContext} travels with it, so
 * events published from async work keep the request's correlation id.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        TraceContext callerTrace = TraceContexts.current().orElse(null);
        return () -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            if (callerContext != null) {
                MDC.setContextMap(callerContext);
            } else {
                MDC.clear();
            }
            try {
                if (callerTrace != null) {
                    TraceContexts.runWith(callerTrace, runnable);
                } else {
                    runnable.run();
                }
            } finally {
                if (previousContext != null) {
                    MDC.setContextMap(previousContext);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
