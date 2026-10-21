package gol.faulty.race;

import gol.common.MateOutcome;
import gol.common.MatingPool;

import java.util.ArrayDeque;

/**
 * VARIANTA CU ERORI #1 (curse de date): loc de intalnire fara sincronizare.
 *
 * Bugurile:
 *   1. 'waiting' este un ArrayDeque (NU e thread-safe) modificat de mai multe fire:
 *      structura interna se poate corupe (elemente pierdute, null-uri, exceptii).
 *   2. CHECK-THEN-ACT pe coada: "if (!isEmpty()) pollFirst()" - doua celule pot lua
 *      acelasi partener -> doi LEADER pentru acelasi FOLLOWER -> doi copii dintr-o singura pereche.
 *   3. 'matched' e un camp obisnuit citit in bucla de alt fir -> fara garantii de vizibilitate.
 *   4. Dupa expirarea timpului, retragerea din coada nu e atomica cu marcajul 'matched':
 *      un LEADER poate "imperechea" o celula care a plecat deja (copil fara al doilea parinte).
 *   5. Polling in loc de wait/notify.
 *
 * Reparatie: lock care sa acopere toata secventa verificare + modificare, si un
 * mecanism de notificare (vezi gol.correct.SafeMatingPool).
 */
public final class RacyMatingPool implements MatingPool {

    private static final class Waiter {
        boolean matched; // BUG 3: nici volatile, nici sincronizat
    }

    private final ArrayDeque<Waiter> waiting = new ArrayDeque<>(); // BUG 1

    @Override
    public MateOutcome seekPartner(long timeoutNanos) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutNanos;

        Waiter other = null;
        if (!waiting.isEmpty()) {          // BUG 2: verificare ...
            Racy.window();
            other = waiting.pollFirst();   // ... apoi actiune
        }
        if (other != null) {
            other.matched = true;
            return MateOutcome.LEADER;
        }

        Waiter me = new Waiter();
        waiting.addLast(me);
        while (System.nanoTime() < deadline) {
            if (me.matched) {
                return MateOutcome.FOLLOWER;
            }
            Thread.sleep(1);               // BUG 5: polling
        }
        waiting.remove(me);                // BUG 4: nu e atomic cu marcajul 'matched'
        return me.matched ? MateOutcome.FOLLOWER : MateOutcome.TIMED_OUT;
    }
}
