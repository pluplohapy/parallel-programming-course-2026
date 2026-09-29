package metrics.collectors;

import metrics.MetricsCollector;
import metrics.Snapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static metrics.Util.BUCKET_COUNT;
import static metrics.Util.bucket;
import static metrics.Util.percentile;

public final class DoubleBufferCollector implements MetricsCollector {

    static final class ThreadBuffers {
        final long[][] buckets = new long[2][BUCKET_COUNT];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = {0, 0};
        final AtomicInteger inside = new AtomicInteger(-1);
    }

    private final boolean checkActive;

    private final Object lock = new Object();
    private final List<ThreadBuffers> allStates = new ArrayList<>();
    private volatile int active = 0;

    private final long[] globalBuckets = new long[BUCKET_COUNT];
    private long globalCount = 0, globalSum = 0;
    private long globalMin = Long.MAX_VALUE, globalMax = 0;

    private final ThreadLocal<ThreadBuffers> myState = ThreadLocal.withInitial(() -> {
        ThreadBuffers s = new ThreadBuffers();
        synchronized (lock) {
            allStates.add(s);
        }
        return s;
    });

    public DoubleBufferCollector(boolean checkActive) {
        this.checkActive = checkActive;
    }

    @Override
    public void record(long value) {
        ThreadBuffers s = myState.get();
        int b;

        while (true) {
            b = active;
            s.inside.set(b);
            if (!checkActive || active == b) break;
            s.inside.setRelease(-1);
            Thread.onSpinWait();
        }
        int idx = bucket(value);
        s.buckets[b][idx]++;
        s.count[b]++;
        s.sum[b] += value;
        if (value < s.min[b]) s.min[b] = value;
        if (value > s.max[b]) s.max[b] = value;

        s.inside.setRelease(-1);
    }

    @Override
    public Snapshot snapshot() {
        synchronized (lock) {
            int old = active;
            active = 1 - old;

            List<ThreadBuffers> copy = new ArrayList<>(allStates);

            for (ThreadBuffers s : copy) {
                while (s.inside.get() == old) {
                    Thread.onSpinWait();
                }
                long[] src = s.buckets[old];
                for (int i = 0; i < BUCKET_COUNT; i++) globalBuckets[i] += src[i];
                globalCount += s.count[old];
                globalSum += s.sum[old];
                if (s.min[old] < globalMin) globalMin = s.min[old];
                if (s.max[old] > globalMax) globalMax = s.max[old];
                Arrays.fill(src, 0);
                s.count[old] = 0;
                s.sum[old] = 0;
                s.min[old] = Long.MAX_VALUE;
                s.max[old] = 0;
            }

            long[] out = globalBuckets.clone();
            long c = globalCount, s2 = globalSum;
            long mn = c == 0 ? 0 : globalMin;
            return new Snapshot(out, c, s2, mn, globalMax,
                    percentile(out, c, 0.50), percentile(out, c, 0.99));
        }
    }
}