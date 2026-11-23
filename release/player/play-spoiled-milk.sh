#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
GAME_DIR="$ROOT_DIR/game-files"
WORLD_STREAMING=false
if [ "${1:-}" = "--world-streaming" ]; then
  WORLD_STREAMING=true
fi

if [ -f "$GAME_DIR/Spoiled_Milk_Client.jar" ]; then
  rm -f \
    "$ROOT_DIR/Spoiled_Milk_Client.jar" \
    "$ROOT_DIR/update-spoiled-milk.sh" \
    "$ROOT_DIR/update-spoiled-milk.ps1" \
    "$ROOT_DIR/Update Spoiled Milk.cmd" \
    "$ROOT_DIR/ASSET-SOURCES.txt" \
    "$ROOT_DIR/VERSION.txt" \
    "$ROOT_DIR/LICENSE"
  rm -rf "$ROOT_DIR/Cache" "$ROOT_DIR/runtime" "$ROOT_DIR/updates"
fi

if [ -f "$GAME_DIR/update-spoiled-milk.sh" ]; then
  if ! sh "$GAME_DIR/update-spoiled-milk.sh"; then
    printf 'Update check failed; launching installed Spoiled Milk client.\n' >&2
  fi
fi

cd "$GAME_DIR"
exec java \
  -Xms512m \
  -Xmx2g \
  -Dspoiledmilk.releaseBuild=true \
  "-Dspoiledmilk.desktopWorldStreaming=$WORLD_STREAMING" \
  -Dsun.java2d.opengl=true \
  -Dspoiledmilk.directFramebuffer=true \
  -Dspoiledmilk.openglPresenter=true \
  -Dspoiledmilk.openglInput=true \
  -Dspoiledmilk.openglPrimaryWindow=true \
  -Dspoiledmilk.openglVsync=false \
  -Dspoiledmilk.renderer3DGeometryCapture=true \
  -Dspoiledmilk.openglWorldMesh=true \
  -Dspoiledmilk.openglWorldMeshTexturedVisible=true \
  -Dspoiledmilk.openglWorldMeshTexturedStaticVisible=true \
  -Dspoiledmilk.openglWorldStaticTextures=true \
  -Dspoiledmilk.openglWorldTexturedAlpha=1.0 \
  -Dspoiledmilk.openglWorldChunksTexturedVisible=true \
  -Dspoiledmilk.openglWorldChunksReplacementComposite=true \
  -Dspoiledmilk.openglWorldChunksTrustedReplacement=true \
  -Dspoiledmilk.openglWorldChunksResidentObjects=true \
  -Dspoiledmilk.openglWorldChunksSpatialCull=true \
  -Dspoiledmilk.openglWorldChunkUploadBudgetMs=3.0 \
  -Dspoiledmilk.openglWorldChunksRemasterLightingShader=true \
  -Dspoiledmilk.openglWorldSpritesVisible=true \
  -Dspoiledmilk.skipLegacyWorldRaster=true \
  -Dspoiledmilk.modernClientLoop=true \
  -jar "Spoiled_Milk_Client.jar"
