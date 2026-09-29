package metrics;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class Benchmark {
    private static double run(MetricsCollector collector, int[] values, int threads, int seconds) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);
        long[] ops = new long[threads];
        Thread[] workers = new Thread[threads];

        for (int k = 0; k < threads; k++) {
            int id = k;
            workers[k] = new Thread(()->{
                long localCount = 0;
                int i = (id * 1000) % values.length;
                try {
                    start.await();
                } catch (InterruptedException e) {
                    ops[id] = 0;
                    return;
                }
                while (!stop.get()) {
                    collector.record(values[i]);
                    localCount++;
                    i++;
                    if (i == values.length) i = 0;
                }
                ops[id] = localCount;
            });
            workers[k].start();
        }
        long t0 = System.nanoTime();
        start.countDown();
        Thread.sleep(seconds * 1000L);
        stop.set(true);
        long t1 = System.nanoTime();

        for (Thread w : workers) w.join();

        return Arrays.stream(ops).sum() * 1e9 / (t1 - t0);
    }

    public static double measurePoint(MetricsCollector collector, int[] values, int threads, int seconds) throws InterruptedException {
        run(collector, values, threads, seconds);
        double[] results = new double[5];
        for (int i = 0; i < 5; i++) {
            results[i] = run(collector, values, threads, seconds);
        }
        System.out.println("snap count: " + collector.snapshot().count());
        Arrays.sort(results);
        return results[2];
    }
}
