package gol.correct;

import gol.common.FoodPool;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Rezervor de hrana thread-safe, construit pe un Semaphore.
 *
 * De ce Semaphore: "unitatile de hrana" sunt exact permisele unui semafor.
 *   - tryAcquire(1, timeout) = "ia o unitate sau asteapta cel mult T_starve"
 *   - release(n)             = "moare o celula si lasa n unitati"
 * Semaforul face singur verificarea si decrementarea intr-un pas atomic
 * (nu exista fereastra check-then-act) si trezeste firele care asteapta.
 *
 * Modul "fair" (FIFO): celulele care asteapta mai mult sunt servite primele,
 * deci nu apare starvation din cauza ca altele "taie coada".
 */
public final class SafeFoodPool implements FoodPool {
    private final Semaphore units;

    public SafeFoodPool(int initialUnits) {
        this.units = new Semaphore(initialUnits, true);
    }

    @Override
    public boolean tryAcquire(long timeoutNanos) throws InterruptedException {
        return units.tryAcquire(1, timeoutNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void release(int n) {
        units.release(n);
    }

    @Override
    public int available() {
        return units.availablePermits();
    }
}
