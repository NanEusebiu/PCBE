package gol.common;

import java.util.concurrent.atomic.LongAdder;

/**
 * Contoare thread-safe. LongAdder e potrivit aici: foarte multe scrieri
 * (fiecare celula incrementeaza), citiri rare (o data pe secunda) - LongAdder
 * reduce contentia fata de un AtomicLong, pentru ca imparte contorul in celule.
 */
public final class AtomicStats implements Stats {
    private final LongAdder born = new LongAdder();
    private final LongAdder died = new LongAdder();
    private final LongAdder divided = new LongAdder();
    private final LongAdder mated = new LongAdder();
    private final LongAdder meals = new LongAdder();
    private final LongAdder foodReleased = new LongAdder();
    private final LongAdder crashed = new LongAdder();

    @Override public void cellBorn(CellType type) { born.increment(); }
    @Override public void cellDied(int released)  { died.increment(); foodReleased.add(released); }
    @Override public void cellDivided()           { divided.increment(); }
    @Override public void cellMated()             { mated.increment(); }
    @Override public void mealEaten()             { meals.increment(); }
    @Override public void cellCrashed()           { crashed.increment(); }

    @Override public long born()         { return born.sum(); }
    @Override public long died()         { return died.sum(); }
    @Override public long divided()      { return divided.sum(); }
    @Override public long mated()        { return mated.sum(); }
    @Override public long meals()        { return meals.sum(); }
    @Override public long foodReleased() { return foodReleased.sum(); }
    @Override public long crashed()      { return crashed.sum(); }
}
