package gol.faulty.race;

import gol.common.CellType;
import gol.common.Stats;

/**
 * VARIANTA CU ERORI #1 (curse de date): contoare globale fara sincronizare.
 *
 * Bug: 'counter++' pe un camp obisnuit nu e o singura operatie, ci trei
 * (citeste, aduna, scrie). Daca doua fire il executa simultan, una din
 * incrementari se pierde (lost update). In plus campurile nu sunt volatile,
 * deci firul de raportare nu are nici garantie sa vada valorile recente.
 *
 * Reparatie: LongAdder / AtomicLong (vezi gol.common.AtomicStats).
 */
public final class RacyStats implements Stats {
    private long born, died, divided, mated, meals, foodReleased, crashed;

    @Override
    public void cellBorn(CellType type) {
        long t = born;
        Racy.window();
        born = t + 1;
    }

    @Override
    public void cellDied(int released) {
        long t = died;
        Racy.window();
        died = t + 1;
        foodReleased += released; // acelasi bug, fara fereastra largita
    }

    @Override
    public void cellDivided() {
        long t = divided;
        Racy.window();
        divided = t + 1;
    }

    @Override
    public void cellMated() {
        long t = mated;
        Racy.window();
        mated = t + 1;
    }

    @Override
    public void mealEaten() {
        long t = meals;
        Racy.window();
        meals = t + 1;
    }

    @Override
    public void cellCrashed() {
        crashed++;
    }

    @Override public long born()         { return born; }
    @Override public long died()         { return died; }
    @Override public long divided()      { return divided; }
    @Override public long mated()        { return mated; }
    @Override public long meals()        { return meals; }
    @Override public long foodReleased() { return foodReleased; }
    @Override public long crashed()      { return crashed; }
}
