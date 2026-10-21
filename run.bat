@echo off
rem Utilizare:
rem   run.bat correct [cheie=valoare ...]
rem   run.bat race    [cheie=valoare ...]
rem   run.bat lock {deadlock^|livelock^|contention} [cheie=valoare ...]
cd /d "%~dp0"
if not exist out call build.bat

set VARIANT=%1
shift

if "%VARIANT%"=="correct" java -cp out gol.correct.Main %1 %2 %3 %4 %5 %6 %7 %8 %9 & goto :eof
if "%VARIANT%"=="race"    java -cp out gol.faulty.race.Main %1 %2 %3 %4 %5 %6 %7 %8 %9 & goto :eof
if "%VARIANT%"=="lock"    java -cp out gol.faulty.lock.Main %1 %2 %3 %4 %5 %6 %7 %8 %9 & goto :eof

echo Utilizare: run.bat {correct^|race^|lock} [argumente]
echo   lock necesita un mod: deadlock ^| livelock ^| contention
exit /b 64
