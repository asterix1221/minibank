package com.minibank.common.metrics;

import com.minibank.transaction.entity.TransactionStatus;
import com.minibank.transaction.entity.TransactionType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Single place for the business metrics called out in the stage-2 spec, on top of the
 * automatic Actuator/Micrometer ones (HTTP, JVM, CPU, Hikari - those need no code, they
 * just need to reach Prometheus).
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry meterRegistry;

    public BusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** Incremented once a transaction reaches a terminal status (COMPLETED/FAILED). */
    public void recordTransactionOutcome(TransactionType type, TransactionStatus status) {
        Counter.builder("transactions_total")
                .tag("type", type.name())
                .tag("status", status.name())
                .register(meterRegistry)
                .increment();
    }

    /** Incremented on every login-confirm attempt (not on the idempotent already-confirmed replay). */
    public void recordAuthAttempt(String result) {
        Counter.builder("auth_attempts_total")
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    /** Wraps a service-layer initiate/confirm/execute call and records its duration. */
    public <T> T timed(String operation, TransactionType type, Supplier<T> action) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return action.get();
        } finally {
            sample.stop(Timer.builder("operation_duration_seconds")
                    .tag("operation", operation)
                    .tag("type", type.name())
                    .register(meterRegistry));
        }
    }
}
