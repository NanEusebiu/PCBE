package gol.faulty.lock;

import gol.common.FoodPool;

import java.util.concurrent.TimeUnit;

/**
 * VARIANTA CU ERORI #2c (PERFORMANTA / CONTENTIE): corecta logic, dar inutil de lenta.
 *
 * Problemele:
 *   1. Sectiune critica PREA LUNGA: celula "mesteca" (sleep) cat tine monitorul
 *      rezervorului. Cat mesteca una, toate celelalte - inclusiv cele care vor
 *      doar sa elibereze hrana - stau la coada. Debitul total e limitat la
 *      1/chewMillis mese pe secunda, indiferent cate celule sau nuclee exista.
 *   2. notifyAll() trezeste TOATE celulele care asteapta la fiecare eliberare,
 *      desi poate fi disponibila hrana doar pentru una (thundering herd).
 *   3. Un singur lacat grosier pentru tot rezervorul.
 *
 * Efect vizibil: chiar daca rezervorul are hrana din belsug, celulele mor de
 * foame ASTEPTAND la lacat - mese/s foarte mici si multe morti, comparativ cu
 * varianta corecta rulata cu aceiasi parametri.
 *
 * Reparatie: "mestecatul" in afara lacatului (sectiunea critica doar
 * verificare + decrementare), Semaphore sau Lock/Condition cu signal() in loc de notifyAll().
 */
public final class BigLockFoodPool implements FoodPool {
    private volatile int units; // scris doar sub monitor; volatile ca available() sa nu astepte lacatul
    private final long chewNanos;

    public BigLockFoodPool(int initialUnits, long chewMillis) {
        this.units = initialUnits;
        this.chewNanos = TimeUnit.MILLISECONDS.toNanos(chewMillis);
    }

    @Override
    public boolean tryAcquire(long timeoutNanos) throws InterruptedException {
        // termenul se calculeaza INAINTE de a intra in monitor: intrarea in 'synchronized'
        // nu are timeout, deci timpul petrecut la coada trebuie scazut manual din T_starve
        long deadline = System.nanoTime() + timeoutNanos;
        synchronized (this) {
            while (units == 0 || System.nanoTime() >= deadline) {
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    return false;                       // a asteptat prea mult (la usa sau pentru hrana)
                }
                TimeUnit.NANOSECONDS.timedWait(this, left); // elibereaza monitorul cat asteapta
            }
            TimeUnit.NANOSECONDS.sleep(chewNanos);      // BUG 1: munca lunga cu monitorul TINUT
            units--;                                    // (decrementarea vine dupa pauza: o intrerupere nu pierde hrana)
            return true;
        }
    }

    @Override
    public synchronized void release(int n) {
        units += n;
        notifyAll();                                    // BUG 2: trezeste toate firele
    }

    @Override
    public int available() {
        return units;
    }
}
