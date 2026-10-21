package gol.common;

/** Cele doua tipuri de celule din simulare. */
public enum CellType {
    /** Se inmulteste prin diviziune: celula mama dispare, apar doua celule fiice flamande. */
    ASEXUAL,
    /** Se inmulteste doar impreuna cu un alt partener care cauta si el sa se reproduca. */
    SEXUAL
}
