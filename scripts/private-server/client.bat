@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "ROOT_DIR=%%~fI"
set "ANT_HOME=%ROOT_DIR%\tools\vendor\apache-ant-1.10.5"
set "ANT_BIN=%ANT_HOME%\bin\ant.bat"

echo Spoiled Milk local client
echo This points the repo client at localhost:43615.
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install Java, then run this file again.
  pause
  exit /b 1
)

if not exist "%ANT_BIN%" (
  echo Missing bundled Ant launcher:
  echo %ANT_BIN%
  pause
  exit /b 1
)

> "%ROOT_DIR%\Client_Base\Cache\ip.txt" echo localhost
> "%ROOT_DIR%\Client_Base\Cache\port.txt" echo 43615

cd /d "%ROOT_DIR%\Client_Base"
call "%ANT_BIN%" compile-and-run
if errorlevel 1 (
  echo.
  echo The client stopped because of an error.
  pause
  exit /b 1
)

echo.
echo The client has closed.
pause
