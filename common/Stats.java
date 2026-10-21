package gol.common;

/**
 * Contoare globale ale simularii, scrise de toate firele-celula.
 *
 * Implementarea corecta foloseste LongAdder (AtomicStats); varianta cu curse
 * de date foloseste campuri obisnuite (RacyStats).
 */
public interface Stats {

    // ---- evenimente (apelate din firele celulelor)
    void cellBorn(CellType type);
    void cellDied(int foodReleased);
    void cellDivided();
    void cellMated();
    void mealEaten();
    void cellCrashed();

    // ---- citiri (apelate din firul de raportare)
    long born();
    long died();
    long divided();
    long mated();
    long meals();
    long foodReleased();
    long crashed();
}
