# Jocul vieții — simulare cu fire de execuție (Java)

Proiect PCBE, Etapa 1 (aplicații concurente cu Java Concurrency API), tema **Game of Life**.
Fiecare celulă este un fir de execuție distinct (`Thread`). Celulele concurează pentru un număr limitat de unități de hrană, mor de foame, se înmulțesc prin diviziune (asexuate) sau în pereche (sexuate).

Proiectul conține cele trei livrabile cerute:

| Livrabil | Pachet | Ce demonstrează |
|---|---|---|
| Soluția **corectă** | `gol.correct` | Semaphore, ReentrantLock, CountDownLatch, atomice, shutdown prin `interrupt()` |
| Soluția cu erori **#1** | `gol.faulty.race` | Curse de date și inconsistența datelor |
| Soluția cu erori **#2** | `gol.faulty.lock` | Deadlock, livelock și probleme de performanță (3 moduri de rulare) |

Cerințe: **JDK 17+** (testat cu JDK 21). Fără dependențe externe.

## Structura

```
game-of-life/
├── README.md
├── build.sh / build.bat       <- compilare
├── run.sh / run.bat           <- rulare
└── src/gol/
    ├── common/                <- nucleul comun tuturor variantelor
    │   ├── Config.java          parametri (T_full, T_starve, mese minime, ...)
    │   ├── Cell.java            celula = fir de execuție; ciclul de viață
    │   ├── Colony.java          creare celule, populație, plafon, oprire
    │   ├── Simulation.java      rulare, raport, detecție deadlock, verificare invarianți
    │   ├── FoodPool.java        interfață: rezervorul de hrană (resursa partajată)
    │   ├── MatingPool.java      interfață: locul de întâlnire al celulelor sexuate
    │   ├── Stats.java           interfață: contoare globale
    │   ├── AtomicStats.java     contoare thread-safe (LongAdder)
    │   ├── CellType.java, MateOutcome.java
    ├── correct/               <- varianta CORECTĂ
    │   ├── SafeFoodPool.java    Semaphore fair
    │   ├── SafeMatingPool.java  ReentrantLock + CountDownLatch
    │   └── Main.java
    └── faulty/
        ├── race/              <- varianta cu erori #1: curse de date
        │   ├── RacyFoodPool.java, RacyMatingPool.java, RacyStats.java, Racy.java
        │   └── Main.java
        └── lock/              <- varianta cu erori #2: deadlock / livelock / performanță
            ├── DeadlockFoodPool.java    (mod deadlock)
            ├── LivelockMatingPool.java  (mod livelock)
            ├── BigLockFoodPool.java     (mod contention)
            └── Main.java
```

Ideea de design: `Cell` și `Colony` sunt identice în toate variantele. Fiecare variantă înlocuiește doar cele trei puncte de sincronizare (`FoodPool`, `MatingPool`, `Stats`). Astfel fiecare bug e izolat într-un fișier mic și ușor de explicat.

## Compilare și rulare

Linux / macOS:

```bash
./build.sh
./run.sh correct
./run.sh race
./run.sh lock deadlock
./run.sh lock livelock
./run.sh lock contention
```

Windows: `build.bat`, apoi `run.bat correct` etc. (scripturile `.bat` nu au fost testate pe Windows; dacă dau probleme, comenzile directe sunt `javac -d out @sources.txt` după `dir /s /b src\*.java > sources.txt`, și `java -cp out gol.correct.Main`).

IntelliJ / Eclipse: marcați `src` ca *Sources Root* și rulați direct clasele `Main`.

Parametri opționali (`cheie=valoare`, la sfârșitul comenzii), de exemplu:

```bash
./run.sh correct asexual=20 sexual=20 food=3000 seconds=20
```

| Cheie | Sens | Implicit |
|---|---|---|
| `asexual`, `sexual` | celule la start | 10, 10 |
| `food` | unități de hrană la start | 1000 |
| `tfull` | T_full în ms | 100 |
| `tstarve` | T_starve în ms | 250 |
| `meals` | mese necesare înainte de înmulțire | 10 |
| `max` | plafon de fire simultane | 400 |
| `seconds` | durata simulării | 15 |

## Cum e modelată problema

| Din enunț | În cod |
|---|---|
| Unități limitate de hrană | `FoodPool` (în varianta corectă, un `Semaphore` cu un permis per unitate) |
| O unitate ajunge T_full | după ce mănâncă, celula doarme T_full (`Cell.lifeCycle`) |
| Dacă nu mănâncă T_starve, moare | `tryAcquire(timeout)` cu timeout = T_starve; la expirare celula moare |
| Moartea lasă 1–5 unități | `Cell.die()` → `release(random 1..5)` |
| Minim 10 mese, apoi se înmulțește înainte să-i fie foame | contor `meals`; când ajunge la `minMeals`, în faza „sătulă” se înmulțește |
| Asexuate: diviziune → două celule flămânde | mama dispare, `trySpawn(ASEXUAL, 2)` |
| Sexuate: caută altă celulă, rezultă o a treia, inițial flămândă | `MatingPool.seekPartner`: a doua venită (LEADER) creează copilul |
| Celulele = fire distincte | `Cell extends Thread` |


## Varianta corectă (`gol.correct`)

| Problemă de concurență | Soluția aleasă | De ce |
|---|---|---|
| Mai multe celule iau din același stoc | `Semaphore(units, fair=true)` | `tryAcquire` verifică și decrementează atomic (nu există check-then-act); suportă timeout (= T_starve); `fair` evită starvation-ul |
| Găsirea perechii | `ReentrantLock` + coadă + `CountDownLatch` | secvența „verific coada, scot partenerul sau mă pun în coadă” se face sub un singur lock |
| Cursa dintre timeout și sosirea partenerului | `withdraw()` reia lock-ul și verifică `matched` | fără el, un LEADER ar crea copil cu un partener care a plecat deja |
| Contoare globale | `LongAdder` | multe scrieri, citiri rare |
| Plafon de populație | `AtomicInteger` + ciclu `compareAndSet` | două diviziuni simultane nu pot depăși împreună plafonul |
| Evidența firelor | `ConcurrentHashMap.newKeySet()` | set thread-safe fără lock global |
| Oprirea simulării | `volatile boolean stopping` + `Thread.interrupt()` | firele blocate în `sleep`/`tryAcquire`/`await` ies cu `InterruptedException` |
| Starea unei celule (`meals`) | nesincronizată, intenționat | e atinsă doar de firul propriu (confinare la fir) |


1. **Conservarea hranei:** `hrană_inițială + hrană_eliberată − mese == hrană_rămasă`
2. **Conservarea celulelor:** `născute == moarte + divizate + prăbușite + oprite_de_simulare`

În testele noastre, varianta corectă a trecut pe toate rulările (inclusiv cu parametri agresivi: `tfull=20 tstarve=50 meals=3`).

## Variantele cu erori

Erorile sunt intenționate și comentate în cod (`BUG 1`, `BUG 2`, …).

### Eroarea #1 — curse de date (`gol.faulty.race`)

| Fișier | Bug | Simptom |
|---|---|---|
| `RacyFoodPool` | câmp `units` nesincronizat; `if (units > 0) … units--` (check-then-act); read-modify-write neatomic | doi mănâncă dintr-o unitate, hrană negativă sau pierdută |
| `RacyStats` | `counter++` pe câmpuri obișnuite | actualizări pierdute (lost updates) |
| `RacyMatingPool` | `ArrayDeque` nesincronizat; check-then-act pe coadă; `matched` nevolatil | doi LEADER pentru același partener, perechi „fantomă” |

Rezultat tipic (rulare reală): `[INCALCAT] conservarea hranei: așteptat = 1555, în rezervor = 0` și `[INCALCAT] conservarea celulelor: născute = 506, contabilizate = 656`.

Despre `Racy.window()`: bugurile există și fără ea (un `++` nesincronizat nu e atomic), dar la ritmul real al simulării două fire ar nimeri aceeași fereastră de nanosecunde foarte rar. Pauza de 0,2 ms între citire și scriere doar **mărește probabilitatea** ca eroarea să apară în câteva secunde. Dacă vă întreabă cineva, e o tehnică standard de reproducere a curselor în teste.

### Eroarea #2 — deadlock, livelock, performanță (`gol.faulty.lock`)

| Mod | Fișier | Bug | Simptom |
|---|---|---|---|
| `deadlock` | `DeadlockFoodPool` | două lock-uri luate în ordine inversă (`stock→ledger` la mâncat, `ledger→stock` la moarte) | programul se blochează; `Simulation` îl detectează cu `ThreadMXBean.findDeadlockedThreads()` și afișează ce fir așteaptă ce lock și cine îl ține; cod de ieșire 2 |
| `livelock` | `LivelockMatingPool` | perechile se „cedează politicos” la infinit (fiecare ține lock-ul propriu, încearcă pe al celuilalt, eșuează, eliberează, repetă) | firele sunt active (milioane de reîncercări), dar `imperecheri = 0` pentru totdeauna |
| `contention` | `BigLockFoodPool` | secțiune critică prea lungă (sleep sub `synchronized`), lock grosier, `notifyAll()` | cu ~1 milion de unități de hrană în rezervor, sute de celule mor de foame așteptând la lock; ~310 mese/s față de ~3900 în varianta corectă cu aceiași parametri |

