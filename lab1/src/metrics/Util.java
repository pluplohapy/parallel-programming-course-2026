package metrics;

import java.util.Arrays;

public final class Util {
    public static final int BUCKET_COUNT = 256;

    public static int bucket(long value){
        return (int) Math.min(value / 4, 255);
    }

    public static long percentile(long[]buckets, long count, double q) {
        if (count == 0) return 0;
        double threshold = count * q;
        long accum = 0;
        for (int i = 0; i < BUCKET_COUNT; i++){
            accum += buckets[i];
            if (accum >= threshold) return i * 4L;
        }
        return 255 * 4L;
    }

    public static long sum(long[] array) {
        return Arrays.stream(array).sum();
    }
}
