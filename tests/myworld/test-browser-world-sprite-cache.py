#!/usr/bin/env python3
"""Compile current helper and compositor, not a mock cache or frozen JAR implementation."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get('JAVA_HOME', '/home/justin/jdk8u482-b08')) / 'bin'
JAR = ROOT / 'Client_Base/Open_RSC_Client.jar'
SOURCES = [ROOT / 'PC_Client/src/orsc' / name for name in (
    'BrowserWorldSpriteCache.java', 'OpenGLSpriteTextureBuilder.java', 'OpenGLPixelWorkspace.java',
    'CompositeWorldSpriteTexture.java', 'DynamicTextureData.java', 'OpenGLSpriteAlpha.java')]
SOURCES += [ROOT / 'Client_Base/src/orsc/graphics' / name for name in (
    'RendererSpriteTransform.java', 'RendererTransparency.java')]
SOURCES.append(ROOT / 'Client_Base/src/com/openrsc/client/model/Sprite.java')
SOURCES.append(ROOT / 'tests/myworld/BrowserWorldSpriteCacheFixture.java')
with tempfile.TemporaryDirectory(prefix='browser-world-sprite-cache-') as temp:
    subprocess.run([str(JAVA / 'javac'), '-source', '8', '-target', '8', '-cp', str(JAR),
                    '-d', temp, *map(str, SOURCES)], check=True)
    subprocess.run([str(JAVA / 'java'), '-cp', temp + os.pathsep + str(JAR),
                    'orsc.BrowserWorldSpriteCacheFixture'], check=True)
