package metrics.collectors;

import metrics.MetricsCollector;

import metrics.Snapshot;

import static metrics.Util.*;

public class SingleCollector implements MetricsCollector {
    private final long[] buckets = new long[BUCKET_COUNT];
    private long count = 0, sum = 0, min = Long.MAX_VALUE, max = 0;

    @Override
    public void record(long value) {
        int b = bucket(value);
        buckets[b]++;
        count++;
        sum += value;
        if (value < min) min = value;
        if (value > max) max = value;
    }

    @Override
    public Snapshot snapshot() {
        long[] copy = buckets.clone();
        long c = count, s = sum;
        long mn = c == 0 ? 0 : min;
        return new Snapshot(copy, c, s, mn, max,
                percentile(copy, c, 0.50), percentile(copy, c, 0.99));
    }
}
