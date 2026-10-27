package lab2;

import java.util.List;
import java.util.ArrayList;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() { }

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.get(0);
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private final String trace;
        private int pos = 0;

        public Replay(String trace) { this.trace = trace; }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() { pos = 0; }

        public int pick(List<Integer> enabled, int last) {
            if (pos >= trace.length()) {
                throw new IllegalStateException("replay: trace '" + trace + "' is shorter than the run");
            }
            int thread = trace.charAt(pos) - 'A';
            if (!enabled.contains(thread)) {
                throw new IllegalStateException("replay: at step " + (pos + 1) + " thread "
                        + trace.charAt(pos) + " cannot run, enabled = " + enabled);
            }
            pos++;
            return thread;
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        private final java.util.Random rnd;

        public RandomPick(long seed) { rnd = new java.util.Random(seed); }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(rnd.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        private final int bound;                                      // максимум вытеснений
        private final List<List<Integer>> options = new ArrayList<>(); // варианты на каждой развилке пути
        private final List<Integer> chosen = new ArrayList<>();        // какой вариант выбран на развилке
        private int pos = 0;                                           // номер текущей развилки в этом прогоне
        private int preemptions = 0;                                  // вытеснений в этом прогоне
        public Dfs(int bound) { this.bound = bound; }

        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() {
            pos = 0;
            preemptions = 0;
        }

        private boolean isPreemption(int thread, List<Integer> enabled, int last) {
            return last >= 0 && thread != last && enabled.contains(last);
        }

        public int pick(List<Integer> enabled, int last) {
            if (pos == options.size()) {
                // новая развилка: запоминаем варианты, не превышающие bound
                List<Integer> allowed = new ArrayList<>();
                for (int t : enabled) {
                    int cost = isPreemption(t, enabled, last) ? 1 : 0;
                    if (preemptions + cost <= bound) allowed.add(t);
                }
                options.add(allowed);
                chosen.add(0);
            }
            int thread = options.get(pos).get(chosen.get(pos));
            if (isPreemption(thread, enabled, last)) preemptions++;
            pos++;
            return thread;
        }

        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            while (options.size() > 0) {
                int i = options.size() - 1;                 // самая глубокая развилка
                if (chosen.get(i) + 1 < options.get(i).size()) {
                    chosen.set(i, chosen.get(i) + 1);       // берём следующий вариант
                    return true;
                }
                options.remove(i);                          // тут всё перебрано, поднимаемся выше
                chosen.remove(i);
            }
            return false;
        }
    }
}
