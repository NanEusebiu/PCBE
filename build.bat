@echo off
rem Compileaza tot proiectul in .\out  (necesita JDK 17+)
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
if errorlevel 1 exit /b 1
echo Compilat in .\out
