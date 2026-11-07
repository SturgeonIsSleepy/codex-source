package cp.player.engine;

import java.util.concurrent.atomic.AtomicLong;

/** Short reader leases avoid metering invisible artwork; grants match its existing 30 FPS. */
final class PulseDemand {
    static final long HOLD_MS = 1500;
    static final long INTERVAL_MS = 33;
    private final AtomicLong expires = new AtomicLong(-1);
    private final AtomicLong next = new AtomicLong(Long.MIN_VALUE);

    void request(long now) { expires.accumulateAndGet(now + HOLD_MS, Math::max); }
    boolean tryMeasure(long now) {
        if (now > expires.get()) return false;
        long scheduled = next.get();
        return now >= scheduled && next.compareAndSet(scheduled, now + INTERVAL_MS);
    }
}
