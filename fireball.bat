@echo off
setlocal EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "JAR=%SCRIPT_DIR%fireball-1.0.0.jar"

if not exist "%JAR%" (
    set "JAR="
    for %%F in ("%SCRIPT_DIR%fireball-1.*.jar") do (
        set "JAR=%%~fF"
        goto :found
    )
)
:found

if not defined JAR (
    echo Error: fireball-*.jar not found next to this script.
    echo Looked in: %SCRIPT_DIR%
    exit /b 1
)
if not exist "%JAR%" (
    echo Error: fireball-*.jar not found next to this script.
    echo Looked in: %SCRIPT_DIR%
    exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
    echo Error: Java is not installed or not on PATH.
    echo Install Java 17+ and try again.
    exit /b 1
)

java -jar "%JAR%" %*
exit /b %ERRORLEVEL%
