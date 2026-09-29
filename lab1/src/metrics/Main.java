package metrics;

import metrics.collectors.*;

public class Main {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "zipf" -> {
                int[] v = Generator.generate();
                long b0 = 0;
                for (int x : v) if (x < 4) b0++;
                System.out.println("values=" + v.length + ", bucket0=" + b0 + ", bucket0%=" + (b0 * 100.0 / v.length));
            }
            case "bench" -> {
                MetricsCollector c = create(args[1]);
                int t = Integer.parseInt(args[2]);
                double ops = Benchmark.measurePoint(c, Generator.generate(), t, 5);
                System.out.println(args[1] + "," + t + "," + (ops / 1e6) + "," + c.snapshot().count());
            }
            case "stress" -> {
                StressTest.run(create(args[1]), args[1], Generator.generate(), 4, 10000);
            }
        }
    }

    static MetricsCollector create(String name) {
        return switch (name) {
            case "single"        -> new SingleCollector();
            case "empty"         -> new EmptyLockCollector();
            case "mutex"         -> new MutexCollector();
            case "striped"       -> new StripedCollector();
            case "tls"           -> new ThreadLocalCollector();
            case "double"        -> new DoubleBufferCollector(true);
            case "double-broken" -> new DoubleBufferCollector(false);
            default              -> throw new IllegalArgumentException();
        };
    }
}