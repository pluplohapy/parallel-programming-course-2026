package lab2;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {
    final String name;
    final Scheduler sched;
    Scheduler.Worker owner = null;   // кто держит лок (null — свободен)

    /** Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s. */
    public SchedLock(Scheduler s, String name) {
        this.name = name;
        this.sched = s;
        s.locks.add(this);           // планировщик запоминает все локи
    }

    /** Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем. */
    public void lock() {
        Scheduler.Worker w = Scheduler.running;
        w.wantLock = this;           // «я хочу этот лок»: пока он занят, меня не выберут
        if (w.touched || owner != null) {
            sched.pause(w);
        }
        w.wantLock = null;
        owner = w;                   // если нас выбрали, лок точно свободен
        Scheduler.after(w, "lock " + name);
    }

    /** Пауза; освободить лок, ждавшие потоки снова могут быть выбраны. */
    public void unlock() {
        if (sched.aborted) return;   // прогон прерван, ничего не делаем
        Scheduler.Worker w = Scheduler.before();
        owner = null;
        Scheduler.after(w, "unlock " + name);
    }
}
