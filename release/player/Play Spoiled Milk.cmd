@echo off
cd /d "%~dp0"
java -jar Spoiled_Milk_Client.jar
if errorlevel 1 pause
