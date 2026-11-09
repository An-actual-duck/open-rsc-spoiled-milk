#!/usr/bin/env python3
"""Actual headless Scene/frame epoch regression and existing scope-branch ownership."""
import pathlib, subprocess
root=pathlib.Path(__file__).resolve().parents[2]
out=root/'output/tests/thin-shadow-stability/context-epoch-classes'
out.mkdir(parents=True,exist_ok=True)
source=(root/'Client_Base/src/orsc/mudclient.java').read_text()
start=source.index('public void applyLayeredSceneScope(')
end=source.index('private void resetLayeredSceneIdentityCaches()',start)
body=source[start:end]
assert body.count('advanceRendererShadowContext()')==1
assert 'if (scopeChanged) {\n            if (this.getScene() != null) this.getScene().advanceRendererShadowContext();' in body
jar=root/'Client_Base/Open_RSC_Client.jar'
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(out),str(root/'tests/myworld/ShadowContextEpochFixture.java')],check=True)
subprocess.run(['java','-Djava.awt.headless=true','-cp',f'{out}:{jar}','orsc.graphics.three.ShadowContextEpochFixture'],cwd=root,check=True)
