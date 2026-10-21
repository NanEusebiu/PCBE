package gol.faulty.lock;

import gol.common.FoodPool;

import java.util.concurrent.locks.LockSupport;

/**
 * VARIANTA CU ERORI #2a (DEADLOCK): doua lock-uri luate in ordine INVERSA.
 *
 * Rezervorul tine doua lacate:
 *   - stockLock  : protejeaza stocul de hrana ('units');
 *   - ledgerLock : protejeaza un "registru contabil" (cate mese / cate unitati eliberate).
 *
 *   tryAcquire (o celula mananca) : stockLock  ->  ledgerLock
 *   release    (o celula moare)   : ledgerLock ->  stockLock      <-- ordine inversa!
 *
 * Daca o celula mananca exact cand alta moare, fiecare detine un lacat si
 * asteapta pe celalalt, pe vecie. Cele 4 conditii Coffman sunt indeplinite:
 * excludere mutuala, hold-and-wait, fara preemptiune, asteptare circulara.
 * Pauza 'pause()' doar largeste fereastra, ca deadlock-ul sa apara in secunde.
 *
 * Reparatie: ordine globala unica de luare a lacatelor (mereu stock -> ledger),
 * sau un singur lacat, sau (mai bine) fara registru sub lacat (LongAdder).
 */
public final class DeadlockFoodPool implements FoodPool {
    // clase dedicate doar ca sa apara nume clare ("...$StockLock") in raportul de deadlock
    private static final class StockLock { }
    private static final class LedgerLock { }

    private final Object stockLock = new StockLock();
    private final Object ledgerLock = new LedgerLock();

    private volatile int units;      // scris doar sub stockLock; volatile ca available() sa nu blocheze
    private long mealsLedger;        // sub ledgerLock
    private long releasedLedger;     // sub ledgerLock

    public DeadlockFoodPool(int initialUnits) {
        this.units = initialUnits;
    }

    @Override
    public boolean tryAcquire(long timeoutNanos) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutNanos;
        while (true) {
            synchronized (stockLock) {                 // 1) stock
                if (units > 0) {
                    pause();
                    synchronized (ledgerLock) {        // 2) apoi ledger
                        units--;
                        mealsLedger++;
                    }
                    return true;
                }
            }
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(2);
        }
    }

    @Override
    public void release(int n) {
        synchronized (ledgerLock) {                    // 1) ledger   <-- INVERS fata de tryAcquire
            releasedLedger += n;
            pause();
            synchronized (stockLock) {                 // 2) apoi stock
                units += n;
            }
        }
    }

    @Override
    public int available() {
        return units;
    }

    private static void pause() {
        LockSupport.parkNanos(1_000_000); // 1 ms cu lacatul tinut
    }
}
