package gol.correct;

import gol.common.AtomicStats;
import gol.common.Config;
import gol.common.FoodPool;
import gol.common.MatingPool;
import gol.common.Simulation;
import gol.common.Stats;

/** Varianta CORECTA: Semaphore + ReentrantLock/CountDownLatch + LongAdder. Cod de iesire 0 daca invariantii se pastreaza. */
public final class Main {
    public static void main(String[] args) throws Exception {
        Config cfg = Config.resolve(Config.defaults(), args);

        FoodPool food = new SafeFoodPool(cfg.initialFood());
        MatingPool mating = new SafeMatingPool();
        Stats stats = new AtomicStats();

        Simulation.Result result = Simulation.run(
                "Jocul vietii - varianta CORECTA", cfg, food, mating, stats, () -> "");
        System.exit(result.invariantsHold() ? 0 : 1);
    }
}
