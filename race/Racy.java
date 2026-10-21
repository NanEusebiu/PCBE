package gol.faulty.race;

import java.util.concurrent.locks.LockSupport;

/**
 * Ajutor pentru varianta cu curse de date.
 *
 * Bugurile din acest pachet exista INDIFERENT de aceasta clasa: un
 * read-modify-write fara sincronizare nu e atomic. Dar la ritmul real al
 * simularii (cateva sute de operatii pe secunda) doua fire ar nimeri aceeasi
 * fereastra de cateva nanosecunde foarte rar - ai astepta minute sau ore ca sa
 * vezi o eroare. De aceea largim INTENTIONAT fereastra dintre citire si
 * scriere cu o pauza scurta, ca eroarea sa apara in cateva secunde.
 * (Tehnica obisnuita pentru a reproduce o cursa intr-un test.)
 */
final class Racy {
    private static final long WINDOW_NANOS = 200_000; // 0,2 ms

    private Racy() { }

    static void window() {
        LockSupport.parkNanos(WINDOW_NANOS);
    }
}
