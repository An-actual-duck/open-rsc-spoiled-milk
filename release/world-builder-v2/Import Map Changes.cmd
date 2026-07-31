@echo off
setlocal EnableExtensions
set "ROOT_DIR=%~dp0"
set "TARGET_ROOT=%~dp0.."
set "WORKSPACE=%~dp0workspace"
set "TOOLS_JAR=%~dp0builder-runtime\launcher\world-builder-tools.jar"
set "RELEASE_IDENTITY=%~dp0RELEASE-IDENTITY.json"

if defined WORLD_BUILDER_JAVA (
  set "JAVA_EXE=%WORLD_BUILDER_JAVA%"
) else if exist "%~dp0runtime\bin\java.exe" (
  set "JAVA_EXE=%~dp0runtime\bin\java.exe"
) else (
  set "JAVA_EXE=java"
)

if not exist "%TOOLS_JAR%" goto missing_tools
if not exist "%RELEASE_IDENTITY%" goto wrong_identity
findstr /C:"rsc-world-editor-v2" "%RELEASE_IDENTITY%" >nul
if errorlevel 1 goto wrong_identity
if not exist "%WORKSPACE%\project-source.json" goto missing_project
if not exist "%ROOT_DIR%VERSION.txt" goto missing_provenance
if not exist "%ROOT_DIR%SOURCE-COMMIT.txt" goto missing_provenance
set /p BUILDER_VERSION=<"%ROOT_DIR%VERSION.txt"
set /p SOURCE_COMMIT=<"%ROOT_DIR%SOURCE-COMMIT.txt"

"%JAVA_EXE%" -jar "%TOOLS_JAR%" export-import --workspace "%WORKSPACE%" --target-root "%TARGET_ROOT%" --builder-version "%BUILDER_VERSION%" --source-commit "%SOURCE_COMMIT%"
if errorlevel 1 goto failed
exit /b 0

:missing_tools
echo Map import could not start: the packaged launcher is missing.
goto failed

:wrong_identity
echo Map import could not start: this is not a World Builder 2 release.
goto failed

:missing_project
echo Map import could not start: run Start World Builder first.
goto failed

:missing_provenance
echo Map import could not start: release provenance files are missing.
goto failed

:failed
pause
exit /b 1
