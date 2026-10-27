package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.concurrent.Semaphore;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;

    static Worker running;

    final List<Worker> workers = new ArrayList<>();
    final List<SchedLock> locks = new ArrayList<>();
    final Semaphore schedulerSem = new Semaphore(0);   // сюда потоки возвращают ход
    final StringBuilder trace = new StringBuilder();   // трасса: AAAABB
    volatile boolean aborted = false;

    static class Worker {
        Scheduler sched;
        int id;
        char name;
        Runnable body;
        Semaphore sem = new Semaphore(0);
        Thread thread;
        boolean done = false;
        boolean touched = true;       // трогал ли поток общее с прошлой паузы (в начале true: пауза перед 1-й операцией)
        SchedLock wantLock = null;    // лок, которого поток ждёт
        BooleanSupplier wantCond = null; // условие, которого поток ждёт в await
        Throwable failure = null;     // если тело потока упало с ошибкой
    }

    static class StopThread extends RuntimeException { }

    // Поток, который запускает тело Worker'а.
    static class WorkerThread extends Thread {
        Worker w;
        WorkerThread(Worker w) { this.w = w; }

        public void run() {
            try {
                w.body.run();
            } catch (StopThread e) {
                // нас закрыли после тупика, это нормально
            } catch (Throwable t) {
                if (!w.sched.aborted) w.failure = t;
            }
            w.done = true;
            w.sched.schedulerSem.release();   // поток закончился: возвращаем ход планировщику
        }
    }

    public Scheduler() { }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        Worker w = new Worker();
        w.sched = this;
        w.id = workers.size();
        w.name = (char) ('A' + w.id);
        w.body = body;
        workers.add(w);
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last). */
    public void run(Strategy strategy) {

        Throwable failure = null;
        try {
            // 1. Запускаем потоки по одному; каждый доходит до своей первой паузы и возвращает ход.
            for (Worker w : workers) {
                running = w;
                w.thread = new WorkerThread(w);
                w.thread.start();
                schedulerSem.acquireUninterruptibly();
                if (w.failure != null) { failure = w.failure; break; }
            }

            // 2. Главный цикл.
            int last = -1;
            while (failure == null) {
                if (allDone()) break;

                List<Integer> enabled = getEnabled();
                if (enabled.isEmpty()) {          // живые потоки есть, а ходить некому
                    deadlock = heldLocksText();
                    break;
                }

                int pick = strategy.pick(enabled, last);
                if (!enabled.contains(pick)) {
                    throw new IllegalStateException("strategy picked " + pick + ", enabled = " + enabled);
                }
                trace.append((char) ('A' + pick));
                last = pick;

                Worker w = workers.get(pick);
                running = w;
                w.sem.release();                       // разбудили выбранный поток
                schedulerSem.acquireUninterruptibly(); // ждём, пока он отдаст ход
                if (w.failure != null) failure = w.failure;
            }
        } finally {
            stopAllThreads();
            running = null;
        }
        if (failure != null) throw new RuntimeException("thread failed: " + failure, failure);
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB". */
    public String traceString() {
        return trace.toString();
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        after(running, "window");
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        Worker w = running;
        if (w.touched || !cond.getAsBoolean()) {
            w.wantCond = cond;
            w.sched.pause(w);
            w.wantCond = null;
        }
        after(w, "await done");
    }

    private boolean allDone() {
        for (Worker w : workers) {
            if (!w.done) return false;
        }
        return true;
    }

    // Кто может сделать шаг прямо сейчас.
    private List<Integer> getEnabled() {
        List<Integer> result = new ArrayList<>();
        for (Worker w : workers) {
            if (w.done) continue;
            if (w.wantLock != null && w.wantLock.owner != null) continue;       // лок занят
            if (w.wantCond != null && !w.wantCond.getAsBoolean()) continue;     // условие ложно
            result.add(w.id);
        }
        return result;
    }

    // Строка вида held={a:A, b:B}
    private String heldLocksText() {
        String s = "";
        for (SchedLock l : locks) {
            if (l.owner == null) continue;
            if (!s.isEmpty()) s += ", ";
            s += l.name + ":" + l.owner.name;
        }
        return "held={" + s + "}";
    }

    // Будим все незавершённые потоки: они увидят aborted и выйдут. Потом ждём их.
    private void stopAllThreads() {
        aborted = true;
        for (Worker w : workers) {
            if (w.thread != null && !w.done) w.sem.release();
        }
        for (Worker w : workers) {
            if (w.thread == null) continue;
            try {
                w.thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Поток отдаёт ход планировщику и засыпает, пока его снова не выберут.
    void pause(Worker w) {
        schedulerSem.release();
        w.sem.acquireUninterruptibly();
        if (aborted) throw new StopThread();
        w.touched = false;
    }

    // Вызывается ПЕРЕД операцией с общими данными: если что-то трогали с прошлой паузы, встаём на паузу.
    static Worker before() {
        Worker w = running;
        if (w.touched) w.sched.pause(w);
        return w;
    }

    // Вызывается ПОСЛЕ операции: помечаем, что поток трогал общее, и пишем строку в лог.
    static void after(Worker w, String text) {
        w.touched = true;
        if (w.sched.logging && !w.sched.aborted) {
            w.sched.log.add(w.name + "  " + text);
        }
    }
}
