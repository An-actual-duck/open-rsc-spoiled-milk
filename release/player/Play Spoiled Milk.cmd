@echo off
cd /d "%~dp0"
if exist "%~dp0game-files\Spoiled_Milk_Client.jar" (
  if exist "%~dp0Spoiled_Milk_Client.jar" del /q "%~dp0Spoiled_Milk_Client.jar" >nul 2>nul
  if exist "%~dp0update-spoiled-milk.sh" del /q "%~dp0update-spoiled-milk.sh" >nul 2>nul
  if exist "%~dp0update-spoiled-milk.ps1" del /q "%~dp0update-spoiled-milk.ps1" >nul 2>nul
  if exist "%~dp0Update Spoiled Milk.cmd" del /q "%~dp0Update Spoiled Milk.cmd" >nul 2>nul
  if exist "%~dp0ASSET-SOURCES.txt" del /q "%~dp0ASSET-SOURCES.txt" >nul 2>nul
  if exist "%~dp0VERSION.txt" del /q "%~dp0VERSION.txt" >nul 2>nul
  if exist "%~dp0LICENSE" del /q "%~dp0LICENSE" >nul 2>nul
  if exist "%~dp0Cache" rmdir /s /q "%~dp0Cache" >nul 2>nul
  if exist "%~dp0runtime" rmdir /s /q "%~dp0runtime" >nul 2>nul
  if exist "%~dp0updates" rmdir /s /q "%~dp0updates" >nul 2>nul
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0game-files\update-spoiled-milk.ps1"
if errorlevel 1 (
  pause
  exit /b 1
)
cd /d "%~dp0game-files"
java ^
  -Xms512m ^
  -Xmx2g ^
  -Dspoiledmilk.releaseBuild=true ^
  -Dsun.java2d.opengl=true ^
  -Dspoiledmilk.directFramebuffer=true ^
  -Dspoiledmilk.openglPresenter=true ^
  -Dspoiledmilk.openglInput=true ^
  -Dspoiledmilk.openglPrimaryWindow=true ^
  -Dspoiledmilk.openglVsync=false ^
  -Dspoiledmilk.renderer3DGeometryCapture=true ^
  -Dspoiledmilk.openglWorldMesh=true ^
  -Dspoiledmilk.openglWorldMeshTexturedVisible=true ^
  -Dspoiledmilk.openglWorldMeshTexturedStaticVisible=true ^
  -Dspoiledmilk.openglWorldStaticTextures=true ^
  -Dspoiledmilk.openglWorldTexturedAlpha=1.0 ^
  -Dspoiledmilk.openglWorldChunksTexturedVisible=true ^
  -Dspoiledmilk.openglWorldChunksReplacementComposite=true ^
  -Dspoiledmilk.openglWorldChunksTrustedReplacement=true ^
  -Dspoiledmilk.openglWorldChunksResidentObjects=true ^
  -Dspoiledmilk.openglWorldChunksSpatialCull=true ^
  -Dspoiledmilk.openglWorldChunkUploadBudgetMs=3.0 ^
  -Dspoiledmilk.openglWorldChunksRemasterLightingShader=true ^
  -Dspoiledmilk.openglWorldSpritesVisible=true ^
  -Dspoiledmilk.skipLegacyWorldRaster=true ^
  -Dspoiledmilk.modernClientLoop=true ^
  -jar Spoiled_Milk_Client.jar
if errorlevel 1 pause
