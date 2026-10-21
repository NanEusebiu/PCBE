package gol.faulty.lock;

import gol.common.AtomicStats;
import gol.common.Config;
import gol.common.FoodPool;
import gol.common.MatingPool;
import gol.common.Simulation;
import gol.common.Stats;
import gol.correct.SafeFoodPool;
import gol.correct.SafeMatingPool;

import java.util.ArrayList;
import java.util.List;

/**
 * Varianta cu ERORI #2: probleme de sincronizare si performanta.
 *
 * Utilizare:  java -cp out gol.faulty.lock.Main {deadlock|livelock|contention} [cheie=valoare ...]
 *
 *   deadlock    - doua lacate luate in ordine inversa; programul se blocheaza si e detectat
 *   livelock    - perechi de celule care isi cedeaza la infinit una alteia; nici o imperechere
 *   contention  - un lacat grosier tinut prea mult; celulele mor de foame cu hrana in rezervor
 *                 (parametru in plus: chew=<ms>, implicit 3)
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0].toLowerCase() : "";
        switch (mode) {
            case "deadlock" -> deadlock(args);
            case "livelock" -> livelock(args);
            case "contention" -> contention(args);
            default -> {
                System.err.println("Utilizare: gol.faulty.lock.Main {deadlock|livelock|contention} [cheie=valoare ...]");
                System.err.println(Config.usage(Config.defaults()));
                System.exit(64);
            }
        }
    }

    private static void deadlock(String[] args) throws Exception {
        // putina hrana + multe celule: primele mor repede si elibereaza hrana exact cand altele mananca
        Config cfg = Config.resolve(Config.defaults().withCells(30, 30).withFood(15).withSeconds(20), args);

        FoodPool food = new DeadlockFoodPool(cfg.initialFood());
        Simulation.Result r = Simulation.run("Jocul vietii - varianta cu ERORI #2 (DEADLOCK)", cfg,
                food, new SafeMatingPool(), new AtomicStats(), () -> "");
        System.out.println();
        System.out.println(r.deadlocked()
                ? "Program blocat definitiv din cauza ordinii inverse a lacatelor (vezi DeadlockFoodPool)."
                : "Deadlock-ul nu a aparut in aceasta rulare - incearca din nou sau mareste numarul de celule.");
        System.exit(r.deadlocked() ? 2 : 0);
    }

    private static void livelock(String[] args) throws Exception {
        // doar celule sexuate si hrana multa: toate ajung sa se caute si se prind in handshake
        Config cfg = Config.resolve(Config.defaults().withCells(0, 20).withFood(5000).withSeconds(10), args);

        LivelockMatingPool mating = new LivelockMatingPool();
        Simulation.run("Jocul vietii - varianta cu ERORI #2 (LIVELOCK)", cfg,
                new SafeFoodPool(cfg.initialFood()), mating, new AtomicStats(),
                () -> String.format("perechi formate=%d, handshake-uri esuate (reincercari)=%d  <-- cresc mereu, dar imperecheri=0",
                        mating.pairsFormed(), mating.failedHandshakes()));
        System.out.println();
        System.out.println("Celulele sunt ACTIVE (fac reincercari non-stop), dar nu progreseaza: asta e livelock, nu deadlock.");
        System.exit(0);
    }

    private static void contention(String[] args) throws Exception {
        // hrana practic nelimitata: orice deces din aceasta rulare se datoreaza DOAR asteptarii la lacat
        long chewMs = 3;
        List<String> rest = new ArrayList<>();
        for (String a : args) {
            if (a.toLowerCase().startsWith("chew=")) {
                chewMs = Long.parseLong(a.substring(5).trim());
            } else {
                rest.add(a);
            }
        }
        Config cfg = Config.resolve(Config.defaults().withCells(150, 100).withFood(1_000_000).withSeconds(10),
                rest.toArray(new String[0]));

        Simulation.run("Jocul vietii - varianta cu ERORI #2 (CONTENTIE, chew=" + chewMs + "ms)", cfg,
                new BigLockFoodPool(cfg.initialFood(), chewMs), new SafeMatingPool(), new AtomicStats(), () -> "");
        System.out.println();
        System.out.println("Compara cu varianta corecta, aceiasi parametri:");
        System.out.println("  ./run.sh correct asexual=" + cfg.initialAsexual() + " sexual=" + cfg.initialSexual()
                + " food=" + cfg.initialFood() + " seconds=" + cfg.runSeconds());
        System.exit(0);
    }
}
