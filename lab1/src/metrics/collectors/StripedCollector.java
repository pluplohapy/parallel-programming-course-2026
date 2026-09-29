package metrics.collectors;

import metrics.MetricsCollector;
import metrics.Snapshot;

import java.util.concurrent.atomic.AtomicLong;

import static metrics.Util.BUCKET_COUNT;
import static metrics.Util.bucket;
import static metrics.Util.percentile;

public final class StripedCollector implements MetricsCollector {
    private static final int STRIPES = 16;

    private final Object[] locks = new Object[STRIPES];
    private final long[] buckets = new long[BUCKET_COUNT];
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong sum = new AtomicLong();
    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong max = new AtomicLong(0);

    public StripedCollector() {
        for (int i = 0; i < STRIPES; i++) locks[i] = new Object();
    }

    @Override
    public void record(long value) {
        int b = bucket(value);
        int group = b % STRIPES;

        synchronized (locks[group]) {
            buckets[b]++;
        }

        count.incrementAndGet();
        sum.addAndGet(value);
        updateMin(value);
        updateMax(value);
    }

    @Override
    public Snapshot snapshot() {
        long[] out = new long[BUCKET_COUNT];
        for (int g = 0; g < STRIPES; g++) {
            synchronized (locks[g]) {
                for (int b = g; b < BUCKET_COUNT; b += STRIPES) {
                    out[b] = buckets[b];
                }
            }
        }
        long c = count.get();
        long s = sum.get();
        long mn = c == 0 ? 0 : min.get();
        return new Snapshot(out, c, s, mn, max.get(),
                percentile(out, c, 0.50), percentile(out, c, 0.99));
    }

    private void updateMin(long value) {
        while (true) {
            long cur = min.get();
            if (value >= cur) return;
            if (min.compareAndSet(cur, value)) return;
        }
    }

    private void updateMax(long value) {
        while (true) {
            long cur = max.get();
            if (value <= cur) return;
            if (max.compareAndSet(cur, value)) return;
        }
    }
}