@echo off
rem See docs\combat\combat-test-gates.md for the authoritative gate contract.
setlocal

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "REPO_ROOT=%%~fI"
set "ANT_HOME=%REPO_ROOT%\tools\vendor\apache-ant-1.10.5"
set "ANT_RUNNER=%ANT_HOME%\bin\ant.bat"

if not exist "%ANT_RUNNER%" (
    >&2 echo ERROR: Bundled combat Ant launcher is missing: "%ANT_RUNNER%"
    exit /b 1
)

call "%ANT_RUNNER%" -f "%SCRIPT_DIR%build.xml" %* test_combat
set "COMBAT_TEST_EXIT=%ERRORLEVEL%"
endlocal & exit /b %COMBAT_TEST_EXIT%
