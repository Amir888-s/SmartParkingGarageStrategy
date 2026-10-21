@echo off
cd /d "%~dp0"
call mvn clean package
if errorlevel 1 exit /b 1
if not exist dist mkdir dist
copy /y target\smart-parking-garage-1.0.0.jar dist\smart-parking-garage-1.0.0.jar
