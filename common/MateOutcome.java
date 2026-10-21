package gol.common;

/** Rezultatul cautarii unui partener de reproducere. */
public enum MateOutcome {
    /** Nu s-a gasit nici un partener in timpul disponibil. */
    TIMED_OUT,
    /** S-a format o pereche; aceasta celula (a doua venita) creeaza copilul. */
    LEADER,
    /** S-a format o pereche; copilul este creat de celalalt partener. */
    FOLLOWER
}
