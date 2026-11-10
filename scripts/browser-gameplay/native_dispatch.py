"""Build the private synchronous backend from its one maintained authority.

No runtime eval, no duplicated maintained GL body, and no Promise adapter in the
literal async control. Unknown native methods or awaits require explicit review.
The ordinary prepare tool owns destination/manifest handling.
"""
import hashlib
import re

PREFIX = 'Java_orsc_BrowserWebGLBridge_'
ASYNC_NATIVE = PREFIX + 'initialize'
SYNC_NATIVES = tuple(PREFIX + name for name in (
    'worldProgress', 'regionTransition', 'holdWorld', 'initializeLighting',
    'uploadLightingMask', 'lightingFrame', 'initializeSky', 'uploadSkyGeometry',
    'skyFrame', 'overlayFrame', 'uploadChunk', 'replaceChunk', 'deleteTextures',
    'uploadTexture', 'present', 'frameWork', 'deleteChunks', 'planWorldSprites',
    'commitWorldSprites',
))
MARKER = "export const BROWSER_NATIVE_CALL_DISPATCH = 'async';"
# Exact audited segments, including the common coherent-sampling receipt.
# A future pure-looking Promise/callback change also needs review, not merely an
# await-token check. Whitespace changes intentionally fail closed too.
SYNC_BODY_SHA256 = {
    'worldProgress': '3da654dfd9b5f992c14124b5179ebfe3cc37b21908d2ec4eed67b88279010fdd',
    'regionTransition': '28cf951fba3bfbde9d9b42e64a37aa766797060956ebc23637cab1584e645917',
    'holdWorld': 'a5c55ec6e627e70b69426f6c85b21a92737f96f6e2253b151352cb1eb23abcb1',
    'initializeLighting': 'fbce3ad5968a5ff8305c3f64fc932abc1ef82ea4b899221ff232617c49b0bf12',
    'uploadLightingMask': '04e1e31af2d459c8ec26a757052167a83911292ecf3f7839a27716bddba1f3e1',
    'lightingFrame': '3bc77cca848fe4ec96c687d76b629311d97b218887568f16ab23be2bafa7d15e',
    'initializeSky': 'd155893bee9cb553ee3b4ed8a96ac54d9903b5e1c0c0e8187b513b2c15106d47',
    'uploadSkyGeometry': '02899d756f959f7e5de9f77d5b551eef4202af5580bf352bdd1b74755b0e4389',
    'skyFrame': 'fc64d012ab0cd7c1ea26732b958c950c5e4412ba35882f378f0c0210acae2540',
    'overlayFrame': '2c500ffe0ad8a2b31fdc2fd86dc4ae02efe09f187ec3ad97bcf59d46bfcaadc6',
    'uploadChunk': 'c9d52823479289a964a39d5f0ca95f860d77f16b8e9d1a29fb46ee0175cbc4a6',
    'replaceChunk': '20d5202ceb1f9912c79c8eca2e7c292d60a894657c18fc78a575f89601a09039',
    'deleteTextures': 'e3597571ec095abfe88febf1bdbd109b8b6e9bd68e015019523dfc1c69648ecc',
    'uploadTexture': '335cd65b80e845f68f5a6e8651c6df176a00065a11a8036ef2115d910f90b1f5',
    'present': '74025b9cb435de71a722c73083fe668751f9cf97c5418b003380a949dcaac285',
    'frameWork': '08f54da8674334a4647a6558032e9f24d73c2b2c62e80cdca85b71d47a489d77',
    'deleteChunks': '24c59f0472df153be8944f80d110f1c85e885f7cadc38798e3a00342b749752b',
    'planWorldSprites': 'eb123681e75c1921498d15c1648d63501b68513a852e2f13af4ff94b6e35fa76',
    'commitWorldSprites': 'c6024352f4130105eb876cea273af507971b68eb99191b4d1f463956fc1bd07d',
}


def synchronous_module(source):
    """Return a reviewed synchronous variant or fail closed on source drift."""
    if source.count(MARKER) != 1:
        raise ValueError('Missing/ambiguous native dispatch capability marker')
    headers = list(re.finditer(r'^    async (Java_orsc_BrowserWebGLBridge_\w+)\(', source, re.M))
    all_headers = re.findall(r'^    (?:async )?(Java_orsc_BrowserWebGLBridge_\w+)\(', source, re.M)
    names = [match.group(1) for match in headers]
    if len(names) != 20 or all_headers != names or set(names) != set(SYNC_NATIVES) | {ASYNC_NATIVE}:
        raise ValueError('Native method inventory changed; review synchronous allowlist')
    for index, header in enumerate(headers):
        end = headers[index + 1].start() if index + 1 < len(headers) else source.index('\n  };', header.end())
        body = source[header.start():end]
        if header.group(1) in SYNC_NATIVES and re.search(r'\bawait\b', body):
            raise ValueError('Audited synchronous native now contains await: ' + header.group(1))
        if header.group(1) in SYNC_NATIVES and hashlib.sha256(body.encode()).hexdigest() != SYNC_BODY_SHA256.get(header.group(1)[len(PREFIX):]):
            raise ValueError('Audited synchronous native body changed: ' + header.group(1))
    generated = source
    for name in SYNC_NATIVES:
        needle = '    async ' + name + '('
        if generated.count(needle) != 1:
            raise ValueError('Ambiguous synchronous method: ' + name)
        generated = generated.replace(needle, '    ' + name + '(', 1)
    generated = generated.replace(MARKER, "export const BROWSER_NATIVE_CALL_DISPATCH = 'sync';", 1)
    digest = hashlib.sha256(source.encode('utf-8')).hexdigest()
    return '// Generated private sync-native variant; canonical SHA256 ' + digest + '\n' + generated
