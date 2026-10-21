package gol.common;

import java.util.Locale;

/**
 * Parametrii simularii. Duratele sunt in milisecunde (timpul real e scalat
 * mult in jos fata de "viata" reala, ca simularea sa dureze secunde, nu ore).
 *
 * @param initialAsexual numar de celule asexuate la start
 * @param initialSexual  numar de celule sexuate la start
 * @param initialFood    unitati de hrana din rezervor la start
 * @param tFullMs        T_full: cat timp ii ajunge unei celule o unitate de hrana
 * @param tStarveMs      T_starve: cat poate sta flamanda inainte sa moara
 * @param minMeals       cate mese trebuie sa fi luat ca sa se poata inmulti
 * @param maxPopulation  plafon de fire simultane (protectie impotriva exploziei)
 * @param runSeconds     durata maxima a simularii
 */
public record Config(
        int initialAsexual,
        int initialSexual,
        int initialFood,
        long tFullMs,
        long tStarveMs,
        int minMeals,
        int maxPopulation,
        int runSeconds) {

    public Config {
        if (initialAsexual < 0 || initialSexual < 0) {
            throw new IllegalArgumentException("numarul initial de celule nu poate fi negativ");
        }
        if (initialFood < 0) {
            throw new IllegalArgumentException("hrana initiala nu poate fi negativa");
        }
        if (tFullMs < 1 || tStarveMs < 1) {
            throw new IllegalArgumentException("tfull si tstarve trebuie sa fie >= 1 ms");
        }
        if (minMeals < 1) {
            throw new IllegalArgumentException("meals trebuie sa fie >= 1");
        }
        if (maxPopulation < 1 || initialAsexual + initialSexual > maxPopulation) {
            throw new IllegalArgumentException("max (plafon populatie) trebuie sa fie >= celulele initiale");
        }
        if (runSeconds < 1) {
            throw new IllegalArgumentException("seconds trebuie sa fie >= 1");
        }
    }

    /** Valorile implicite folosite de toate variantele (fiecare le poate suprascrie). */
    public static Config defaults() {
        return new Config(10, 10, 1000, 100, 250, 10, 400, 15);
    }

    public Config withCells(int asexual, int sexual) {
        return new Config(asexual, sexual, initialFood, tFullMs, tStarveMs, minMeals, maxPopulation, runSeconds);
    }

    public Config withFood(int food) {
        return new Config(initialAsexual, initialSexual, food, tFullMs, tStarveMs, minMeals, maxPopulation, runSeconds);
    }

    public Config withSeconds(int seconds) {
        return new Config(initialAsexual, initialSexual, initialFood, tFullMs, tStarveMs, minMeals, maxPopulation, seconds);
    }

    /** Aplica peste aceasta configuratie argumentele de forma cheie=valoare (restul se ignora). */
    public Config overriddenBy(String[] args) {
        int a = initialAsexual, s = initialSexual, f = initialFood;
        int m = minMeals, max = maxPopulation, sec = runSeconds;
        long tf = tFullMs, ts = tStarveMs;
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = arg.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String val = arg.substring(eq + 1).trim();
            try {
                switch (key) {
                    case "asexual" -> a = Integer.parseInt(val);
                    case "sexual" -> s = Integer.parseInt(val);
                    case "food" -> f = Integer.parseInt(val);
                    case "tfull" -> tf = Long.parseLong(val);
                    case "tstarve" -> ts = Long.parseLong(val);
                    case "meals" -> m = Integer.parseInt(val);
                    case "max" -> max = Integer.parseInt(val);
                    case "seconds" -> sec = Integer.parseInt(val);
                    default -> throw new IllegalArgumentException("parametru necunoscut: " + key);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("valoare invalida pentru " + key + ": " + val);
            }
        }
        return new Config(a, s, f, tf, ts, m, max, sec);
    }

    /**
     * Varianta pentru metoda main: la argumente gresite afiseaza mesajul si
     * iese cu cod 64 (conventia "usage error").
     */
    public static Config resolve(Config base, String[] args) {
        try {
            return base.overriddenBy(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Eroare: " + e.getMessage());
            System.err.println(usage(base));
            System.exit(64);
            return base; // nu se ajunge aici
        }
    }

    public static String usage(Config base) {
        return "Parametri optionali (cheie=valoare), valorile afisate sunt cele implicite pentru aceasta varianta:\n"
                + "  asexual=" + base.initialAsexual + "   celule asexuate la start\n"
                + "  sexual=" + base.initialSexual + "    celule sexuate la start\n"
                + "  food=" + base.initialFood + "     unitati de hrana la start\n"
                + "  tfull=" + base.tFullMs + "     T_full in ms\n"
                + "  tstarve=" + base.tStarveMs + "   T_starve in ms\n"
                + "  meals=" + base.minMeals + "      mese necesare inainte de inmultire\n"
                + "  max=" + base.maxPopulation + "      plafon de populatie (fire simultane)\n"
                + "  seconds=" + base.runSeconds + "   durata simularii in secunde";
    }

    public String describe() {
        return String.format(Locale.ROOT,
                "asexuate=%d, sexuate=%d, hrana=%d, T_full=%dms, T_starve=%dms, mese_min=%d, plafon=%d, durata=%ds",
                initialAsexual, initialSexual, initialFood, tFullMs, tStarveMs, minMeals, maxPopulation, runSeconds);
    }
}
