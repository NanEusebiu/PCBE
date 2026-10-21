package gol.faulty.race;

import gol.common.Config;
import gol.common.FoodPool;
import gol.common.MatingPool;
import gol.common.Simulation;
import gol.common.Stats;

/**
 * Varianta cu ERORI #1: curse de date si inconsistenta datelor.
 * Rezultatul asteptat: invariantii din raportul final apar ca INCALCAT
 * (hrana creata/pierduta, celule nenumarate), eventual hrana negativa.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        Config base = Config.defaults().withCells(30, 30).withFood(3000).withSeconds(12);
        Config cfg = Config.resolve(base, args);

        FoodPool food = new RacyFoodPool(cfg.initialFood());
        MatingPool mating = new RacyMatingPool();
        Stats stats = new RacyStats();

        Simulation.Result result = Simulation.run(
                "Jocul vietii - varianta cu ERORI #1 (curse de date)", cfg, food, mating, stats, () -> "");
        System.out.println();
        System.out.println(result.invariantsHold()
                ? "Nota: de data aceasta invariantii s-au pastrat din intamplare - ruleaza din nou (cursele sunt nedeterministe)."
                : "Invarianti incalcati => date inconsistente cauzate de curse de date (vezi comentariile din RacyFoodPool / RacyStats / RacyMatingPool).");
        System.exit(0);
    }
}
