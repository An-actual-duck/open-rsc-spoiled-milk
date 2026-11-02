"""Supplemental source checks for the maintained client/server projectile carriers."""
import re


def integer_constant(source: str, name: str) -> int:
    match = re.search(r'\b' + re.escape(name) + r'\s*=\s*(\d+)\s*;', source)
    assert match, f'Missing numeric constant {name}'
    return int(match.group(1))


def validate_projectile_slots(client: str, entity_handler: str, server_projectiles: str) -> None:
    """The custom sprite range must cover the complete canonical wire enum."""
    block = entity_handler.split('public enum PROJECTILE_TYPES {', 1)[1].split('private final int value;', 1)[0]
    definitions = {name: int(value) for name, value in re.findall(r'^\s*([A-Z][A-Z_0-9]*)\((\d+)\)', block, re.MULTILINE)}
    assert definitions, 'Missing canonical projectile wire definitions'
    assert len(set(definitions.values())) == len(definitions), 'Duplicate canonical projectile wire IDs'
    first = integer_constant(client, 'CUSTOM_PROJECTILE_FIRST')
    count = integer_constant(client, 'CUSTOM_PROJECTILE_COUNT')
    assert first == definitions['BLANK'] + 1, 'Custom sprite boundary no longer follows native projectile IDs'
    custom = {value for value in definitions.values() if value >= first}
    assert custom == set(range(first, first + count)), 'Client sprite slots do not match all custom wire IDs'
    for name in ('ACID_ARMOR_PROC', 'ICE_SWORD_STAB', 'NAGA_SCIMITAR'):
        assert definitions[name] == integer_constant(server_projectiles, name), f'Client/server projectile mismatch: {name}'
    assert integer_constant(client, 'PROJECTILE_EFFECT_FRAME_SLOTS') == 36, 'Native per-projectile frame allocation changed'
    # Combat sprites precede projectile, mirrored and static-mirror allocations.
    end = (integer_constant(client, 'spriteCombatEffectBase')
           + integer_constant(client, 'COMBAT_EFFECT_COUNT') * integer_constant(client, 'COMBAT_EFFECT_FRAME_SLOTS')
           + 2 * count * integer_constant(client, 'PROJECTILE_EFFECT_FRAME_SLOTS')
           + integer_constant(client, 'PROJECTILE_STATIC_MIRROR_SLOTS'))
    assert end <= 65536, 'Effect sprite ranges exceed the 16-bit scene carrier'
