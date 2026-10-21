package gol.common;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coloniile de celule: creeaza firele, tine evidenta populatiei si opreste
 * simularea. Partea de "infrastructura" e thread-safe in toate variantele
 * (variantele cu erori strica doar rezervorul de hrana, locul de intalnire
 * sau contoarele - ca bugurile sa fie usor de izolat).
 */
public final class Colony {
    private final Config cfg;
    private final FoodPool food;
    private final MatingPool mating;
    private final Stats stats;

    private final Set<Cell> cells = ConcurrentHashMap.newKeySet();
    private final AtomicInteger alive = new AtomicInteger();
    private final AtomicInteger[] aliveByType = new AtomicInteger[CellType.values().length];
    private final AtomicInteger peak = new AtomicInteger();
    private final AtomicInteger stopped = new AtomicInteger();
    private final AtomicInteger nextId = new AtomicInteger();
    private final AtomicInteger crashReports = new AtomicInteger();
    private volatile boolean stopping; // citit de multe fire, scris o singura data -> volatile

    public Colony(Config cfg, FoodPool food, MatingPool mating, Stats stats) {
        this.cfg = cfg;
        this.food = food;
        this.mating = mating;
        this.stats = stats;
        for (int i = 0; i < aliveByType.length; i++) {
            aliveByType[i] = new AtomicInteger();
        }
    }

    public Config config() { return cfg; }
    public FoodPool food() { return food; }
    public MatingPool mating() { return mating; }
    public Stats stats() { return stats; }

    public int alive() { return alive.get(); }
    public int alive(CellType type) { return aliveByType[type.ordinal()].get(); }
    public int peak() { return peak.get(); }
    /** Celule oprite de simulare (nu de foame, nu prin diviziune). */
    public int stopped() { return stopped.get(); }

    /**
     * Creeaza {@code count} celule noi (flamande), totul-sau-nimic: daca
     * plafonul de populatie ar fi depasit, nu creeaza nimic. Rezervarea locului
     * se face cu un ciclu compareAndSet, deci doua celule care se inmultesc
     * simultan nu pot depasi impreuna plafonul.
     */
    public boolean trySpawn(CellType type, int count) {
        if (stopping) {
            return false;
        }
        int current;
        do {
            current = alive.get();
            if (current + count > cfg.maxPopulation()) {
                return false;
            }
        } while (!alive.compareAndSet(current, current + count));

        peak.accumulateAndGet(current + count, Math::max);
        aliveByType[type.ordinal()].addAndGet(count);

        for (int i = 0; i < count; i++) {
            Cell cell = new Cell(this, type, nextId.incrementAndGet());
            stats.cellBorn(type);
            cells.add(cell);
            cell.start();
            if (stopping) {
                cell.interrupt(); // a pornit exact in timpul opririi
            }
        }
        return true;
    }

    // ---- apelate din firele celulelor (pachet-privat)

    void cellExited(Cell cell) {
        cells.remove(cell);
        alive.decrementAndGet();
        aliveByType[cell.type().ordinal()].decrementAndGet();
    }

    void cellStopped() {
        stopped.incrementAndGet();
    }

    void cellCrashed(Cell cell, Throwable t) {
        stats.cellCrashed();
        if (crashReports.incrementAndGet() <= 3) {
            System.err.println("!! " + cell.getName() + " s-a oprit cu eroare: " + t);
        }
    }

    // ---- oprirea simularii

    /** Opreste crearea de celule noi si intrerupe toate firele existente. */
    public void stopAll() {
        stopping = true;
        for (Cell cell : cells) {
            cell.interrupt();
        }
    }

    /** Asteapta terminarea firelor; false daca unele nu s-au oprit in timp (ex. blocate in deadlock). */
    public boolean awaitTermination(long timeoutMs) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (!cells.isEmpty()) {
            for (Cell cell : new ArrayList<>(cells)) {
                long leftMs = TimeUnit.NANOSECONDS.toMillis(end - System.nanoTime());
                if (leftMs <= 0) {
                    return false;
                }
                cell.join(leftMs);
            }
        }
        return true;
    }
}
