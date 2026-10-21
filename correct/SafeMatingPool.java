package gol.correct;

import gol.common.MateOutcome;
import gol.common.MatingPool;

import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Loc de intalnire thread-safe pentru celulele sexuate.
 *
 * Protocol (rendezvous):
 *   - sub lock, o celula care soseste verifica coada:
 *       * daca exista un partener care asteapta -> il scoate din coada, il
 *         marcheaza "matched", il trezeste si devine LEADER (creeaza copilul);
 *       * altfel se pune ea in coada si asteapta (cu timeout) semnalul.
 *   - cea care asteapta devine FOLLOWER cand e trezita.
 *
 * Cazul subtil: timeout-ul expira in acelasi timp cu sosirea partenerului.
 * De aceea, dupa timeout/intrerupere celula reia lock-ul si verifica sub lock
 * daca nu a fost deja asociata ("withdraw"); doar daca nu a fost, se retrage
 * din coada. Altfel am avea un LEADER care crede ca are partener, in timp ce
 * celalalt a plecat deja.
 */
public final class SafeMatingPool implements MatingPool {

    private static final class Waiter {
        final CountDownLatch signal = new CountDownLatch(1);
        boolean matched; // protejat de lock
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayDeque<Waiter> waiting = new ArrayDeque<>(); // protejat de lock

    @Override
    public MateOutcome seekPartner(long timeoutNanos) throws InterruptedException {
        Waiter me;
        lock.lock();
        try {
            Waiter other = waiting.pollFirst();
            if (other != null) {
                other.matched = true;
                other.signal.countDown();
                return MateOutcome.LEADER;
            }
            me = new Waiter();
            waiting.addLast(me);
        } finally {
            lock.unlock();
        }

        boolean signalled;
        try {
            signalled = me.signal.await(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            if (withdraw(me)) {
                throw e; // nu fusese asociata: iese curat din coada si propaga intreruperea
            }
            // fusese deja asociata: perechea exista, deci ramane FOLLOWER; pastram flag-ul de intrerupere
            Thread.currentThread().interrupt();
            return MateOutcome.FOLLOWER;
        }
        if (signalled) {
            return MateOutcome.FOLLOWER;
        }
        return withdraw(me) ? MateOutcome.TIMED_OUT : MateOutcome.FOLLOWER;
    }

    /** @return true daca s-a retras din coada (nu avea partener); false daca fusese deja asociata. */
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
}
