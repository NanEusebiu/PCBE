package gol.faulty.race;

import gol.common.FoodPool;

/**
 * VARIANTA CU ERORI #1 (curse de date / consistenta datelor): rezervor de hrana
 * FARA nicio sincronizare.
 *
 * Bugurile (toate pe campul partajat 'units'):
 *   1. Camp partajat nici volatile, nici protejat de lock -> fara garantii de vizibilitate.
 *   2. CHECK-THEN-ACT: "if (units > 0) ... units--" - intre test si decrementare
 *      alta celula poate lua aceeasi unitate -> doua celule mananca dintr-o unitate,
 *      iar 'units' poate deveni NEGATIV.
 *   3. READ-MODIFY-WRITE neatomic ('units = t - 1', 'units = t + n'): doua scrieri
 *      simultane se suprascriu -> unitati create din nimic sau pierdute (lost update).
 *   4. Asteptare prin polling (sleep in bucla) in loc de un mecanism de notificare.
 *
 * Simptom vizibil: invariantul "conservarea hranei" e incalcat, iar hrana
 * ramasa poate fi negativa.
 *
 * Reparatie: Semaphore (vezi gol.correct.SafeFoodPool) sau lock + conditie.
 */
public final class RacyFoodPool implements FoodPool {
    private int units; // BUG 1

    public RacyFoodPool(int initialUnits) {
        this.units = initialUnits;
    }

    @Override
    public boolean tryAcquire(long timeoutNanos) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutNanos;
        while (true) {
            if (units > 0) {            // BUG 2: verificare ...
                int current = units;
                Racy.window();
                units = current - 1;    // BUG 3: ... apoi scriere, fara atomicitate
                return true;
            }
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(1);            // BUG 4: polling
        }
    }

    @Override
    public void release(int n) {
        int current = units;
        Racy.window();
        units = current + n;            // BUG 3: lost update
    }

    @Override
    public int available() {
        return units;
    }
}
