package gol.common;

/**
 * Rezervorul comun de hrana. Este resursa partajata principala a simularii:
 * toate celulele concureaza pentru unitatile de aici.
 *
 * Contractul pe care il respecta varianta corecta (si pe care variantele cu
 * erori il incalca in feluri diferite):
 *   - o unitate este data cel mult unei celule (nu se creeaza hrana din nimic);
 *   - release(n) nu pierde unitati;
 *   - tryAcquire asteapta cel mult timeoutNanos, apoi renunta.
 */
public interface FoodPool {

    /**
     * Incearca sa ia o unitate de hrana, asteptand cel mult {@code timeoutNanos}.
     *
     * @return true daca a primit hrana, false daca a expirat timpul
     */
    boolean tryAcquire(long timeoutNanos) throws InterruptedException;

    /** Pune {@code units} unitati de hrana inapoi in rezervor (cand moare o celula). */
    void release(int units);

    /** Numarul curent de unitati disponibile (folosit doar pentru raportare). */
    int available();
}
