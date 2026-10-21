package gol.faulty.lock;

import gol.common.MateOutcome;
import gol.common.MatingPool;

import java.util.ArrayDeque;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * VARIANTA CU ERORI #2b (LIVELOCK): doua celule "politicoase" care se tot cedeaza una alteia.
 *
 * Gasirea perechii e corecta (ca in SafeMatingPool). Bugul e in "strangerea de
 * mana" care urmeaza: fiecare celula isi blocheaza "dreptul propriu" si incearca
 * sa-l ia si pe al partenerului; daca nu reuseste, ELIBEREAZA politicos ce are
 * si reia de la capat. Ambele celule au exact acelasi ceas si acelasi protocol,
 * deci in fiecare runda:
 *     amandoua tin lacatul propriu  ->  amandoua incearca sa-l ia pe al celuilalt  ->  amandoua esueaza
 *     ->  amandoua cedeaza  ->  amandoua reincep.
 * (Simetria perfecta e modelata cu un CyclicBarrier; in practica apare cand
 * doua fire folosesc aceleasi pauze de back-off fixe.)
 *
 * Diferenta fata de deadlock: firele NU sunt blocate pe lacate; se misca
 * mereu (iau/elibereaza lacate, consuma CPU), dar nu fac nici un progres.
 * Contorul 'retries' creste la nesfarsit, iar 'imperecheri' ramane 0.
 *
 * Reparatie: back-off cu durata ALEATOARE (rupe simetria), sau un arbitru
 * (ordine fixa: se ia mai intai lacatul celulei cu id mai mic), sau pur si
 * simplu un singur lacat pentru toata perechea.
 */
public final class LivelockMatingPool implements MatingPool {

    private static final class Pair {
        final CyclicBarrier barrier = new CyclicBarrier(2);
        final ReentrantLock[] claims = { new ReentrantLock(), new ReentrantLock() };
    }

    private static final class Waiter {
        final CountDownLatch signal = new CountDownLatch(1);
        boolean matched; // protejat de lock
        Pair pair;       // setat sub lock, inainte de signal.countDown()
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayDeque<Waiter> waiting = new ArrayDeque<>(); // protejat de lock
    private final LongAdder pairs = new LongAdder();
    private final LongAdder retries = new LongAdder();

    public long pairsFormed() { return pairs.sum(); }
    public long failedHandshakes() { return retries.sum(); }

    @Override
    public MateOutcome seekPartner(long timeoutNanos) throws InterruptedException {
        Waiter me = null;
        Pair formed = null;
        lock.lock();
        try {
            Waiter other = waiting.pollFirst();
            if (other != null) {
                formed = new Pair();
                other.pair = formed;
                other.matched = true;
                other.signal.countDown();
            } else {
                me = new Waiter();
                waiting.addLast(me);
            }
        } finally {
            lock.unlock();
        }

        if (formed != null) {                       // a doua venita
            pairs.increment();
            politeHandshake(formed, 0);
            return MateOutcome.LEADER;              // nu se ajunge aici: handshake-ul nu se termina niciodata
        }

        boolean signalled;
        try {
            signalled = me.signal.await(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            if (withdraw(me)) {
                throw e;
            }
            Thread.currentThread().interrupt();     // era deja asociata; handshake-ul va arunca imediat
            signalled = true;
        }
        if (!signalled && withdraw(me)) {
            return MateOutcome.TIMED_OUT;
        }
        politeHandshake(me.pair, 1);
        return MateOutcome.FOLLOWER;
    }

    private boolean withdraw(Waiter me) {
        lock.lock();
        try {
            if (me.matched) {
                return false;
            }
            waiting.remove(me);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** "Dupa dumneavoastra!" - repetat la infinit de ambii parteneri. */
    private void politeHandshake(Pair p, int slot) throws InterruptedException {
        ReentrantLock mine = p.claims[slot];
        ReentrantLock theirs = p.claims[1 - slot];
        try {
            while (true) {
                mine.lock();
                try {
                    p.barrier.await();                  // ambii tin lacatul propriu
                    if (theirs.tryLock()) {             // nu reuseste niciodata: celalalt il tine
                        theirs.unlock();
                        return;                         // cod mort, doar pentru completitudine
                    }
                    retries.increment();
                    p.barrier.await();                  // ambii au incercat; abia acum cedeaza
                } finally {
                    mine.unlock();                      // cedare politicoasa -> runda urmatoare identica
                }
            }
        } catch (BrokenBarrierException e) {
            throw new InterruptedException("partenerul a fost oprit");
        }
    }
}
