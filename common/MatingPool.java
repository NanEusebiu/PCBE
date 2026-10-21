package gol.common;

/**
 * Locul de intalnire al celulelor sexuate. Doua celule care cauta partener
 * trebuie sa se gaseasca una pe alta; a doua venita devine LEADER si creeaza
 * copilul, prima devine FOLLOWER.
 */
public interface MatingPool {

    /**
     * Cauta un partener, asteptand cel mult {@code timeoutNanos}.
     *
     * Garantii (varianta corecta): fiecare celula e asociata cu cel mult un
     * partener, iar fiecarei perechi ii corespunde exact un LEADER si un FOLLOWER.
     */
    MateOutcome seekPartner(long timeoutNanos) throws InterruptedException;
}
