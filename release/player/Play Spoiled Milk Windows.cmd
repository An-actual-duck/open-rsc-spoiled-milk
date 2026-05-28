@echo off
cd /d "%~dp0"
runtime\bin\java.exe -jar Spoiled_Milk_Client.jar
if errorlevel 1 pause
