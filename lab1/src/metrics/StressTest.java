package metrics;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StressTest {
    private StressTest() {}

    public static void run(MetricsCollector collector, String name, int[] values, int writers, int snapshots)
            throws InterruptedException {
        AtomicBoolean stop = new AtomicBoolean(false);
        CountDownLatch ready = new CountDownLatch(writers);
        CountDownLatch start = new CountDownLatch(1);

        long[] ops = new long[writers];
        Thread[] workers = new Thread[writers];

        for (int k = 0; k < writers; k++) {
            final int id = k;
            workers[k] = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    ops[id] = 0;
                    return;
                }
                long localCount = 0;
                int index = (id * 1000) % values.length;
                while (!stop.get()) {
                    collector.record(values[index]);
                    localCount++;
                    index++;
                    if (index == values.length) index = 0;
                }
                ops[id] = localCount;
            });
            workers[k].start();
        }

        ready.await();
        start.countDown();

        long broken = 0, less = 0, greater = 0;
        for (int i = 0; i < snapshots; i++) {
            Snapshot s = collector.snapshot();
            long bucketSum = Util.sum(s.buckets());
            if (bucketSum != s.count()) {
                broken++;
                if (bucketSum < s.count()) less++;
                else greater++;
            }
        }

        stop.set(true);
        for (Thread w : workers) w.join();

        long totalCalls = Util.sum(ops);
        long finalCount = collector.snapshot().count();
        double percent = snapshots == 0 ? 0.0 : broken * 100.0 / snapshots;

        System.out.printf(Locale.US,
                "%s: broken=%.2f%% (%d snapshots), sum<count=%d, sum>count=%d, finalCount-totalCalls=%d%n",
                name, percent, broken, less, greater, finalCount - totalCalls);
    }
}