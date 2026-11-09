#!/usr/bin/env python3
"""Render-scope contracts against the built client plus producer/consumer ownership."""
import pathlib, subprocess
root=pathlib.Path(__file__).resolve().parents[2]
out=root/'output/tests/underground-light-normalization/cpu-classes';out.mkdir(parents=True,exist_ok=True)
jar=root/'Client_Base/Open_RSC_Client.jar'
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(out),str(root/'tests/myworld/UndergroundLightingFixture.java')],check=True)
subprocess.run(['java','-Djava.awt.headless=true','-cp',f'{out}:{jar}','orsc.UndergroundLightingFixture'],check=True,cwd=root)
presenter=(root/'PC_Client/src/orsc/OpenGLFramePresenter.java').read_text()
assert 'RendererFrameLighting.bind(frame.renderer3DFrame)' in presenter
assert 'finally {\n            RendererFrameLighting.restore(previous);' in presenter
mud=(root/'Client_Base/src/orsc/mudclient.java').read_text()
assert 'setRendererSignedWorldLevel(this.packetHandler.getLogicalWorldLevel(' in mud
assert mud.count('this.scene.setRendererSignedWorldLevel(0);')>=5
chunk=(root/'PC_Client/src/orsc/OpenGLWorldChunkRenderer.java').read_text()
assert 'frame == null || frame.isUnderground() || frame.getWorldChunkFrame()' in chunk
assert 'if (frame != null && frame.isUnderground()) return false;' in chunk
print('PASS scoped presentation ownership, all sunlight fallback guards and login producer reset')

packet=(root/'Client_Base/src/orsc/PacketHandler.java').read_text()
assert 'hadLayeredSceneContext && !result.isScopeChanged()' in packet
assert mud.index('} else if (this.layeredSceneActivationPending) {') < mud.index('setRendererSignedWorldLevel(this.packetHandler.getLogicalWorldLevel(')
assert 'this.drawLoadingPleaseWaitFrame();' in mud
