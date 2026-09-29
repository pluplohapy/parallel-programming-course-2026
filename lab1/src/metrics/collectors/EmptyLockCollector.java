package metrics.collectors;

import metrics.MetricsCollector;
import metrics.Snapshot;

import static metrics.Util.BUCKET_COUNT;

public final class EmptyLockCollector implements MetricsCollector {
    private final Object lock = new Object();

    @Override
    public void record(long value) {
        synchronized (lock) {}
    }

    @Override
    public Snapshot snapshot() {
        synchronized (lock) {
            return new Snapshot(new long[BUCKET_COUNT], 0, 0, 0, 0, 0, 0);
        }
    }
}