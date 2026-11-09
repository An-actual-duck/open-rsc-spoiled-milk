#!/usr/bin/env python3
"""Actual Scene/texture metadata regression and both production depth policy call sites."""
import pathlib,subprocess
root=pathlib.Path(__file__).resolve().parents[2]
out=pathlib.Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','thin-shadow-stability','--mkdir'],text=True).strip())/'animated-texture-classes'
out.mkdir(parents=True,exist_ok=True)
source=(root/'PC_Client/src/orsc/OpenGLWorldChunkRenderer.java').read_text()
start=source.index('private long geometryShadowStaticSignature(');end=source.index('private WorldChunkBuffer shadowChunkBuffer(',start)
assert 'if (!RendererShadowCasterPolicy.isFarCaster(frame.getTexture(material))) continue;' in source[start:end]
start=source.index('private void drawGeometryShadowCasters(');end=source.index('OpenGLGeometryShadows geometryShadowDiagnostics()',start)
assert 'if (staticOnly && !RendererShadowCasterPolicy.isFarCaster(frame.getTexture(material))) continue;' in source[start:end]
policy=(root/'PC_Client/src/orsc/RendererShadowCasterPolicy.java').read_text()
assert '17' not in policy and '!texture.hasAnimatedAlpha()' in policy
jar=root/'Client_Base/Open_RSC_Client.jar'
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(out),str(root/'tests/myworld/AnimatedTextureShadowFixture.java')],check=True)
subprocess.run(['java','-Djava.awt.headless=true','-cp',f'{out}:{jar}','orsc.graphics.three.AnimatedTextureShadowFixture'],cwd=root,check=True)
