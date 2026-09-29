package metrics.collectors;

import metrics.MetricsCollector;
import metrics.Snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import static metrics.Util.BUCKET_COUNT;
import static metrics.Util.bucket;
import static metrics.Util.percentile;

public final class ThreadLocalCollector implements MetricsCollector {

    static final class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(BUCKET_COUNT);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);
    }

    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();

    private final ThreadLocal<ThreadState> myState = ThreadLocal.withInitial(() -> {
        ThreadState s = new ThreadState();
        synchronized (listLock) {
            allStates.add(s);
        }
        return s;
    });

    @Override
    public void record(long value) {
        ThreadState s = myState.get();
        int b = bucket(value);

        s.buckets.setRelease(b, s.buckets.getPlain(b) + 1);
        s.count.setRelease(s.count.getPlain() + 1);
        s.sum.setRelease(s.sum.getPlain() + value);
        if (value < s.min.getPlain()) s.min.setRelease(value);
        if (value > s.max.getPlain()) s.max.setRelease(value);
    }

    @Override
    public Snapshot snapshot() {
        List<ThreadState> copy;
        synchronized (listLock) {
            copy = new ArrayList<>(allStates);
        }

        long[] out = new long[BUCKET_COUNT];
        long totalCount = 0, totalSum = 0;
        long min = Long.MAX_VALUE, max = 0;

        for (ThreadState s : copy) {
            for (int i = 0; i < BUCKET_COUNT; i++) out[i] += s.buckets.get(i);
            totalCount += s.count.get();
            totalSum += s.sum.get();
            min = Math.min(min, s.min.get());
            max = Math.max(max, s.max.get());
        }

        if (totalCount == 0) min = 0;

        return new Snapshot(out, totalCount, totalSum, min, max,
                percentile(out, totalCount, 0.50), percentile(out, totalCount, 0.99));
    }
}