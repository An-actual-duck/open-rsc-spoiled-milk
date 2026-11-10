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


def synchronous_module(source):
    """Return a reviewed synchronous variant or fail closed on source drift."""
    if source.count(MARKER) != 1:
        raise ValueError('Missing/ambiguous native dispatch capability marker')
    headers = list(re.finditer(r'^    async (Java_orsc_BrowserWebGLBridge_\w+)\(', source, re.M))
    names = [match.group(1) for match in headers]
    if len(names) != 20 or set(names) != set(SYNC_NATIVES) | {ASYNC_NATIVE}:
        raise ValueError('Native method inventory changed; review synchronous allowlist')
    for index, header in enumerate(headers):
        end = headers[index + 1].start() if index + 1 < len(headers) else source.index('\n  };', header.end())
        body = source[header.start():end]
        if header.group(1) in SYNC_NATIVES and re.search(r'\bawait\b', body):
            raise ValueError('Audited synchronous native now contains await: ' + header.group(1))
    generated = source
    for name in SYNC_NATIVES:
        needle = '    async ' + name + '('
        if generated.count(needle) != 1:
            raise ValueError('Ambiguous synchronous method: ' + name)
        generated = generated.replace(needle, '    ' + name + '(', 1)
    generated = generated.replace(MARKER, "export const BROWSER_NATIVE_CALL_DISPATCH = 'sync';", 1)
    digest = hashlib.sha256(source.encode('utf-8')).hexdigest()
    return '// Generated private sync-native variant; canonical SHA256 ' + digest + '\n' + generated
