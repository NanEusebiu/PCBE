#!/usr/bin/env bash
# Utilizare:
#   ./run.sh correct [cheie=valoare ...]
#   ./run.sh race    [cheie=valoare ...]
#   ./run.sh lock {deadlock|livelock|contention} [cheie=valoare ...]
cd "$(dirname "$0")"
[ -d out ] || ./build.sh

variant="${1:-}"
[ $# -gt 0 ] && shift

case "$variant" in
  correct) exec java -cp out gol.correct.Main "$@" ;;
  race)    exec java -cp out gol.faulty.race.Main "$@" ;;
  lock)    exec java -cp out gol.faulty.lock.Main "$@" ;;
  *)
    echo "Utilizare: ./run.sh {correct|race|lock} [argumente]"
    echo "  lock necesita un mod: deadlock | livelock | contention"
    exit 64
    ;;
esac
