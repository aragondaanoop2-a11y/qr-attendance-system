@echo off
setlocal
where mvn >nul 2>nul
if errorlevel 1 (
  echo Apache Maven 3.9+ is required. Install Maven, then run this file again.
  echo Java 21 or newer is also required.
  pause
  exit /b 1
)
cd /d "%~dp0"
call mvn -q javafx:run
if errorlevel 1 pause

