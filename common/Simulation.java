package gol.common;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Ruleaza o simulare: porneste celulele initiale, afiseaza starea in fiecare
 * secunda, detecteaza deadlock-uri, opreste totul la final si verifica doua
 * invariante care trebuie sa fie adevarate intr-o implementare corecta.
 *
 * Invariantele (verificate DOAR dupa ce toate firele s-au oprit):
 *   1. Conservarea hranei:   hrana_initiala + hrana_eliberata - mese == hrana_ramasa
 *   2. Conservarea celulelor: nascute == moarte + divizate + prabusite + oprite_de_simulare
 */
public final class Simulation {

    /** Rezultatul rularii. */
    public record Result(boolean deadlocked, boolean terminatedCleanly, boolean invariantsHold) { }

    private Simulation() { }

    public static Result run(String title, Config cfg, FoodPool food, MatingPool mating,
                             Stats stats, Supplier<String> extraInfo) throws InterruptedException {
        System.out.println("=== " + title + " ===");
        System.out.println("Parametri: " + cfg.describe());
        System.out.println();

        Colony colony = new Colony(cfg, food, mating, stats);
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();

        long t0 = System.nanoTime();
        colony.trySpawn(CellType.ASEXUAL, cfg.initialAsexual());
        colony.trySpawn(CellType.SEXUAL, cfg.initialSexual());

        long end = t0 + TimeUnit.SECONDS.toNanos(cfg.runSeconds());
        long nextReport = t0 + TimeUnit.SECONDS.toNanos(1);
        boolean deadlocked = false;
        String stopReason = "timpul a expirat";

        while (true) {
            Thread.sleep(200);
            long now = System.nanoTime();

            long[] deadlockedIds = threads.findDeadlockedThreads();
            if (deadlockedIds != null) {
                deadlocked = true;
                stopReason = "DEADLOCK detectat";
                printDeadlock(threads, deadlockedIds);
                break;
            }
            if (now >= nextReport) {
                printStatus(t0, colony, food, stats);
                nextReport += TimeUnit.SECONDS.toNanos(1);
            }
            if (colony.alive() == 0) {
                stopReason = "toate celulele au murit (extinctie)";
                break;
            }
            if (now >= end) {
                break;
            }
        }

        long elapsed = System.nanoTime() - t0;
        colony.stopAll();
        boolean clean = colony.awaitTermination(3000);

        // ------------------------------------------------------------ raport final
        double secs = elapsed / 1e9;
        System.out.println();
        System.out.println("--- RAPORT FINAL (" + stopReason + ") ---");
        System.out.printf(Locale.ROOT, "durata                : %.1f s%n", secs);
        System.out.printf(Locale.ROOT, "populatie maxima      : %d%n", colony.peak());
        System.out.printf(Locale.ROOT, "celule nascute        : %d%n", stats.born());
        System.out.printf(Locale.ROOT, "  morti de foame      : %d%n", stats.died());
        System.out.printf(Locale.ROOT, "  diviziuni (asexuat) : %d%n", stats.divided());
        System.out.printf(Locale.ROOT, "  imperecheri (sex.)  : %d%n", stats.mated());
        System.out.printf(Locale.ROOT, "  prabusite (erori)   : %d%n", stats.crashed());
        System.out.printf(Locale.ROOT, "mese consumate        : %d  (%.0f mese/s)%n", stats.meals(), stats.meals() / secs);
        System.out.printf(Locale.ROOT, "hrana: initial=%d, eliberata de moarte=%d, ramasa=%d%n",
                cfg.initialFood(), stats.foodReleased(), food.available());
        String extra = extraInfo.get();
        if (extra != null && !extra.isEmpty()) {
            System.out.println(extra);
        }

        boolean invariantsHold = false;
        System.out.println();
        if (!clean) {
            System.out.println("Invarianti: NEVERIFICABILI - unele fire nu s-au putut opri"
                    + " (monitoarele 'synchronized' nu pot fi intrerupte).");
        } else {
            long expectedFood = (long) cfg.initialFood() + stats.foodReleased() - stats.meals();
            boolean foodOk = expectedFood == food.available();
            long accounted = stats.died() + stats.divided() + stats.crashed() + colony.stopped();
            boolean cellsOk = stats.born() == accounted;
            invariantsHold = foodOk && cellsOk;

            System.out.println("Verificare invarianti:");
            System.out.printf(Locale.ROOT, "  [%s] conservarea hranei   : initial + eliberata - mese = %d, in rezervor = %d%n",
                    foodOk ? "OK" : "INCALCAT", expectedFood, food.available());
            System.out.printf(Locale.ROOT, "  [%s] conservarea celulelor: nascute = %d, morti+divizate+prabusite+oprite = %d%n",
                    cellsOk ? "OK" : "INCALCAT", stats.born(), accounted);
        }
        return new Result(deadlocked, clean, invariantsHold);
    }

    private static void printStatus(long t0, Colony colony, FoodPool food, Stats stats) {
        long sec = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - t0);
        System.out.printf(Locale.ROOT,
                "t=%3ds  vii=%4d (asex=%4d, sex=%4d)  hrana=%6d | nasteri=%5d morti=%5d diviziuni=%4d imperecheri=%4d mese=%7d%n",
                sec, colony.alive(), colony.alive(CellType.ASEXUAL), colony.alive(CellType.SEXUAL),
                food.available(), stats.born(), stats.died(), stats.divided(), stats.mated(), stats.meals());
    }

    private static void printDeadlock(ThreadMXBean threads, long[] ids) {
        System.out.println();
        System.out.println(">>> DEADLOCK: " + ids.length + " fire se blocheaza reciproc, niciunul nu mai poate avansa:");
        ThreadInfo[] infos = threads.getThreadInfo(ids, true, true);
        int shown = 0;
        for (ThreadInfo info : infos) {
            if (info == null) {
                continue;
            }
            if (shown++ >= 6) {
                System.out.println("    ... (si altele)");
                break;
            }
            System.out.printf("    %s asteapta %s, detinut de %s%n",
                    info.getThreadName(), info.getLockName(), info.getLockOwnerName());
        }
    }
}
