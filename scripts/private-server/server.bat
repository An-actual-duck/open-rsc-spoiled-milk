@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "ROOT_DIR=%%~fI"
set "ANT_HOME=%ROOT_DIR%\tools\vendor\apache-ant-1.10.5"
set "ANT_BIN=%ANT_HOME%\bin\ant.bat"
set "DB_PATH=%ROOT_DIR%\server\inc\sqlite\myworld_dev.db"
set "SEED_DB_PATH=%ROOT_DIR%\server\inc\sqlite\myworld_seed.db"

echo Spoiled Milk private server
echo Keep this window open while people are playing.
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

if not exist "%DB_PATH%" (
  if not exist "%SEED_DB_PATH%" (
    echo Missing seed database:
    echo %SEED_DB_PATH%
    pause
    exit /b 1
  )
  echo Creating a fresh local save database...
  copy "%SEED_DB_PATH%" "%DB_PATH%" >nul
) else (
  echo Using existing local save database.
)

> "%ROOT_DIR%\Client_Base\Cache\ip.txt" echo localhost
> "%ROOT_DIR%\Client_Base\Cache\port.txt" echo 43615

echo Building and starting the server...
echo.
cd /d "%ROOT_DIR%\server"
call "%ANT_BIN%" compile-and-run -DconfFile=myworld
if errorlevel 1 (
  echo.
  echo The server stopped because of an error.
  pause
  exit /b 1
)

echo.
echo The server has stopped.
pause
