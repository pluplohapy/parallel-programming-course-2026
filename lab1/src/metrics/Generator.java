package metrics;

import java.util.Arrays;
import java.util.Random;

public class Generator {
    public static final int VALUES = 1 << 20;
    public static final int SEED = 42;
    public static final int MAX_K = 1023;
    public static final double S = 1.15;

    public static int[] generate() {
        Random random = new Random(SEED);
        double[] cdf = new double[MAX_K + 1]; // Cumulative Distribution Function
        double total = 0.0;

        for (int k = 1; k <= MAX_K; k++) {
            total += 1.0 / Math.pow(k, S);
            cdf[k] = total;
        }

        int[] values = new int[VALUES];
        for (int i = 0; i < VALUES; i++) {
            double x = random.nextDouble() * total;
            int idx = Arrays.binarySearch(cdf, x);
            if (idx < 0) idx = -idx - 1;
            values[i] = Math.clamp(idx, 1, MAX_K);
        }
        return values;
    }
}
