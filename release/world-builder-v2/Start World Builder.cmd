@echo off
setlocal EnableExtensions
set "ROOT_DIR=%~dp0"
set "TARGET_ROOT=%~dp0.."
set "RUNTIME_ROOT=%~dp0builder-runtime"
set "WORKSPACE=%~dp0workspace"
set "TOOLS_JAR=%~dp0builder-runtime\launcher\world-builder-tools.jar"
set "LAYERED_PACKAGE=%~dp0builder-runtime\layered-world\package"
set "RELEASE_IDENTITY=%~dp0RELEASE-IDENTITY.json"

if defined WORLD_BUILDER_JAVA (
  set "JAVA_EXE=%WORLD_BUILDER_JAVA%"
) else if exist "%~dp0runtime\bin\java.exe" (
  set "JAVA_EXE=%~dp0runtime\bin\java.exe"
) else (
  set "JAVA_EXE=java"
)

if not exist "%TOOLS_JAR%" goto missing_tools
if not exist "%LAYERED_PACKAGE%\manifest.json" goto missing_layered_package
if not exist "%RELEASE_IDENTITY%" goto missing_identity
findstr /C:"rsc-world-editor-v2" "%RELEASE_IDENTITY%" >nul
if errorlevel 1 goto wrong_identity
"%JAVA_EXE%" -version >nul 2>&1
if errorlevel 1 goto missing_java

if exist "%WORKSPACE%\project-source.json" goto validate_existing
if exist "%WORKSPACE%" goto incomplete_workspace

if not defined WORLD_BUILDER_PORT set "WORLD_BUILDER_PORT=43615"
"%JAVA_EXE%" -jar "%TOOLS_JAR%" launch --server-root "%TARGET_ROOT%" --runtime-root "%RUNTIME_ROOT%" --workspace "%WORKSPACE%" --port "%WORLD_BUILDER_PORT%" --config server/myworld.conf --runtime-config server/myworld.conf --layered-package "%LAYERED_PACKAGE%" --layered-profile spoiled-milk-replacement
goto finished

:validate_existing
if not exist "%WORKSPACE%\layered-review.json" goto legacy_workspace

:run_existing
"%JAVA_EXE%" -jar "%TOOLS_JAR%" run --workspace "%WORKSPACE%"
goto finished

:missing_tools
echo World Builder 2 could not start: the packaged launcher is missing.
goto failed

:missing_layered_package
echo World Builder 2 could not start: the packaged signed-layered map is missing.
goto failed

:missing_identity
echo World Builder 2 could not start: release identity is missing.
goto failed

:wrong_identity
echo World Builder 2 could not start: this package has the wrong product identity.
goto failed

:missing_java
echo World Builder could not start: Java 17 or newer was not found.
goto failed

:incomplete_workspace
echo World Builder could not start: the workspace folder exists but is incomplete.
echo Preserve it and review its contents before retrying.
goto failed

:legacy_workspace
echo World Builder 2 could not start: the existing workspace is legacy or unidentified.
echo World Builder 2 will not open or migrate a World Editor v1 workspace.
goto failed

:finished
if errorlevel 1 goto failed
exit /b 0

:failed
pause
exit /b 1
