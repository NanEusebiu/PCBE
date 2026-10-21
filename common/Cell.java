package gol.common;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * O celula vie = un fir de executie. Ciclul de viata:
 *
 * <pre>
 *   FLAMANDA --(gaseste hrana in cel mult T_starve)--> SATULA (T_full) --> FLAMANDA ...
 *       |                                                  |
 *       +-- nu gaseste hrana in T_starve --> MOARE         +-- daca a mancat >= minMeals --> se inmulteste
 *           (elibereaza 1..5 unitati de hrana)
 * </pre>
 *
 * Starea celulei (numarul de mese) e atinsa doar de firul propriu, deci nu are
 * nevoie de sincronizare. Tot ce e partajat (hrana, locul de intalnire,
 * contoarele, populatia) se acceseaza prin FoodPool / MatingPool / Stats / Colony.
 */
public final class Cell extends Thread {
    private final Colony colony;
    private final CellType type;
    private int meals; // doar firul propriu il atinge -> nu necesita sincronizare

    Cell(Colony colony, CellType type, int id) {
        super("cell-" + id + (type == CellType.ASEXUAL ? "-A" : "-S"));
        this.colony = colony;
        this.type = type;
        setDaemon(true); // un fir blocat nu trebuie sa tina JVM-ul in viata
    }

    public CellType type() {
        return type;
    }

    @Override
    public void run() {
        try {
            lifeCycle();
        } catch (InterruptedException e) {
            // simularea s-a oprit: iesire controlata, nu e moarte de foame
            colony.cellStopped();
        } catch (Throwable t) {
            // o celula nu trebuie sa dispara in tacere (relevant pentru variantele cu erori)
            colony.cellCrashed(this, t);
        } finally {
            colony.cellExited(this);
        }
    }

    private void lifeCycle() throws InterruptedException {
        Config cfg = colony.config();
        long tFull = TimeUnit.MILLISECONDS.toNanos(cfg.tFullMs());
        long tStarve = TimeUnit.MILLISECONDS.toNanos(cfg.tStarveMs());
        long hungrySince = System.nanoTime(); // celulele se nasc flamande

        while (true) {
            // ---- faza FLAMANDA: are cel mult T_starve sa gaseasca hrana
            long starveAt = hungrySince + tStarve;
            long wait = Math.max(0L, starveAt - System.nanoTime());
            if (!colony.food().tryAcquire(wait)) {
                die();
                return;
            }
            meals++;
            colony.stats().mealEaten();
            long fullUntil = System.nanoTime() + tFull;

            // ---- faza SATULA: daca a mancat destul, se inmulteste inainte sa-i fie foame
            if (meals >= cfg.minMeals()) {
                if (type == CellType.ASEXUAL) {
                    if (divide()) {
                        return; // celula mama inceteaza sa existe: au aparut doua fiice
                    }
                } else {
                    reproduceSexually(fullUntil);
                }
            }
            sleepUntil(fullUntil);
            hungrySince = fullUntil; // foamea incepe exact cand expira T_full
        }
    }

    /** Moartea de foame: celula lasa in urma 1..5 unitati de hrana. */
    private void die() {
        int released = ThreadLocalRandom.current().nextInt(1, 6);
        colony.food().release(released);
        colony.stats().cellDied(released);
    }

    /** Diviziune: celula mama este inlocuita de doua celule fiice flamande. */
    private boolean divide() {
        if (!colony.trySpawn(CellType.ASEXUAL, 2)) {
            return false; // plafonul de populatie e atins; va incerca din nou data viitoare
        }
        colony.stats().cellDivided();
        return true;
    }

    /**
     * Reproducere sexuata: cauta un partener cel mult pana expira T_full.
     * Ambii parinti supravietuiesc si trebuie sa manance din nou minMeals mese
     * inainte de urmatoarea reproducere; copilul (al treilea) se naste flamand.
     */
    private void reproduceSexually(long fullUntil) throws InterruptedException {
        long budget = fullUntil - System.nanoTime();
        if (budget <= 0) {
            return;
        }
        MateOutcome outcome = colony.mating().seekPartner(budget);
        switch (outcome) {
            case LEADER -> {
                if (colony.trySpawn(CellType.SEXUAL, 1)) {
                    colony.stats().cellMated();
                }
                meals = 0;
            }
            case FOLLOWER -> meals = 0;
            case TIMED_OUT -> { /* ramane cu meals >= minMeals si va incerca in ciclul urmator */ }
        }
    }

    private static void sleepUntil(long deadlineNanos) throws InterruptedException {
        long left = deadlineNanos - System.nanoTime();
        if (left > 0) {
            TimeUnit.NANOSECONDS.sleep(left);
        }
    }
}
