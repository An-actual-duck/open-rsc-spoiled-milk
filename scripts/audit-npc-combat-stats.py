#!/usr/bin/env python3
"""Read source/JSON only; report current MyWorld NPC stat provenance.

Default: print summary without writing. --write emits a reproducible bundle only
under output/diagnostics/npc-stat-inventory/. Never starts Java, accesses a DB,
changes definitions, or treats observed values as approved balance.
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import io
import json
import math
from pathlib import Path
import re
import subprocess
from collections import Counter

ROOT = Path(__file__).resolve().parents[1]
DEFS = 'server/conf/server/defs/'
NPC_DIR = 'server/src/com/openrsc/server/model/entity/npc/'
STYLES = ('melee', 'ranged', 'magic')
DIRECT = tuple(s + kind for kind in ('Offense', 'Defense') for s in STYLES)
MULTIPLIERS = tuple(s + 'DefenseMultiplier' for s in STYLES)
DIVISORS = tuple(s + 'DefenseDivisor' for s in STYLES)
DEFAULT_DEF = dict(zip(STYLES, (1.0, 0.5, 0.5)))
FILES = ('NpcDefs.json', 'NpcDefsCustom.json', 'MonsterSlayerNpcDefs.json',
         'VisualTestNpcDefs.json', 'MyWorldNpcDefs.json',
         'SlayerMovementPreviewNpcDefs.json', 'MonsterSlayerTowerNpcDefs.json',
         'GemDragonNpcDefs.json', 'StirgeNpcDefs.json')
CONFIGS = ('server/myworld.conf', 'server/myworld-host.conf')
PLACEMENTS = (DEFS + 'locs/NpcLocs.json', DEFS + 'locs/MyWorldNpcLocs.json',
              DEFS + 'locs/MyWorldNpcRemovals.json')
ZERO_RESERVED = 2147483647
RESERVE_FIELDS = {'attack', 'strength', 'hits', 'defense', 'combatlvl',
                  'hairColour', 'topColour', 'bottomColour', 'skinColour', *DIRECT}
# Fail closed if traced implementation changes. Review source, then deliberately
# update the translation and these fingerprints; there is no --ignore-guards.
SOURCE_GUARDS = {
    'server/src/com/openrsc/server/external/EntityHandler.java': 'e86ed73832007c9d6488b25d38b433ec9512c3d3ebb819d606ba364a8b2f58d1',
    'server/src/com/openrsc/server/external/NPCDef.java': '140e5873f7f4b883c9d03b5499e96cf40e5ca5bd564ad1617f7745803f542830',
    NPC_DIR + 'Npc.java': '4d60d36714679e4cdca98f625b6161b8d12567fa771ecb96d684a5fad1d4c160',
    NPC_DIR + 'NpcAttackStyleProfile.java': 'faa0a153a5a9d26c22c8b8d5a33b9cc76fee1d5c68896469b70e333df48c40b1',
    NPC_DIR + 'NpcCombatProfile.java': 'd0099887b4d384a0d7aacb0a72a5a1436bfa7b41998536f23505a83a6995ee74',
    'server/src/com/openrsc/server/constants/NpcId.java': '0f488b60fb46e59db0f161f83e1cbdc0117b3c4ce88ab746efab5e4128a6a938',
    'server/src/com/openrsc/server/content/monsterslayer/BansheeCombat.java': '7f3c8cc12800a70727e9e2af69407b43cee9ed87732d17bdf3c1e9313d05d820',
    'server/src/com/openrsc/server/content/monsterslayer/NagaCombat.java': '0aad2966439ee0cce037fb0bc6eacb2eaa87d21e03772bbdf7d9e1c3956bd6ec',
    'server/src/com/openrsc/server/content/monsterslayer/GiantFrogCombat.java': '2e9d8da24a07e038e3ebe45e2206d0fa8fb034d7f8706cc3c2c02db98d4e95ab',
    'server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java': '03d4c5544e64866c6a4b64ffb166ff6947d04640d76a8737daf370b1e64a033b',
    'server/src/com/openrsc/server/plugins/Functions.java': '7e1429c6d8ed3acf903a764c230a9556643c64fb19106f744bc58802a40f9ad2',
    'tests/myworld/test-slayer-movement-preview.py': 'f973737df85564ac1c23ca41e6430002d4b1bf0d9a9ea10c4128c4ff7bfd697f',
    'tests/myworld/test-gorak-visual-npc.py': '770b87f4443692032c708a8493e39406866735ded9ee72ff91fc08a38b3f6211',
}
# Explicit audit rules, not game balance limits and not automatic bug judgments.
FLAG_RULES = {
    'legacy_attack_dominates_projectile_fallback': 'Active general projectile power is inferred from Attack greater than Strength.',
    'extreme_attack_strength_gap': 'Attack >= 2*max(1,Strength) and Attack-Strength >=50 with active projectile fallback. Diagnostic threshold only.',
    'high_generic_magic_ceiling': 'Generic profile magic raw maximum >=50. Diagnostic threshold only; no target or approved cap.',
    'high_melee_ceiling': 'Ordinary melee raw maximum if used >=50. Diagnostic threshold only.',
    'all_defenses_below_legacy': 'Positive old Defense exceeds all three resolved defenses. Review against historical independent-defense rationale; may be intentional.',
    'defense_multiplier_above_one': 'At least one configured multiplier >1. May be an approved family exception.',
    'explicit_zero_uses_fallback': 'A present direct field is zero, which the current >0 getter treats as unset.',
    'positive_power_outside_general_profile': 'Positive direct ranged/magic power outside general projectile profile; dedicated scripts may consume it.',
    'legacy_zero_on_attackable': 'At least one old A/S/D value is zero. Not proof of a broken modern value.',
    'nonpositive_hp_on_attackable': 'Configured attackable definition has Hits <=0.',
    'reserved_sentinel_loaded': 'A loaded stat/HP/level equals Integer.MAX_VALUE; zero-override hazard.',
    'special_script_review': 'Known sampled encounter/summon context; ordinary getter/profile inventory is not full encounter simulation.',
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git(*args: str) -> str:
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def load_array(relative: str, expected_key: str | None = None):
    path = ROOT / relative
    text = path.read_text(encoding='utf-8')
    data = json.loads(text)
    require(isinstance(data, dict) and len(data) == 1, 'Expected one array: ' + relative)
    key = next(iter(data))
    require(expected_key is None or key == expected_key, 'Wrong array key: ' + relative)
    require(isinstance(data[key], list), 'Expected array: ' + relative)
    return data[key], text


def reference(relative: str, text: str, npc_id: int) -> str:
    matches = list(re.finditer(r'"id"\s*:\s*' + str(npc_id) + r'(?=\s*[,}])', text))
    require(len(matches) == 1, 'Ambiguous definition ID reference: ' + relative + ':' + str(npc_id))
    return relative + ':' + str(text.count('\n', 0, matches[0].start()) + 1)


def verify_source():
    for path, expected in SOURCE_GUARDS.items():
        require(digest(ROOT / path) == expected, 'Traced source changed; review before regenerating: ' + path)
    config_values = {}
    for path in CONFIGS:
        text = (ROOT / path).read_text()
        values = {}
        for key, expected in [('want_myworld', 'true'), ('based_config_data', '85'),
                              ('osrs_combat_melee', 'false'), ('osrs_combat_ranged', 'false')]:
            found = re.findall(r'^\s*' + key + r':\s*([^\s#]+)', text, re.M)
            require(found == [expected], 'Unsupported config ' + path + ':' + key)
            values[key] = found[0]
        config_values[path] = values
    handler = (ROOT / 'server/src/com/openrsc/server/external/EntityHandler.java').read_text()
    load = handler.split('public void load() {', 1)[1].split('customNpcConditions();', 1)[0]
    uncommented = re.sub(r'//[^\n]*', '', load)
    filenames = re.findall(r'loadNpcs\([^\n]+/defs/([^"/]+)"\);', uncommented)
    require(filenames == list(FILES), 'Load order differs from traced source')
    allow = handler.split('MYWORLD_NPC_OVERRIDE_FIELDS', 1)[1].split('));', 1)[0]
    return config_values, set(re.findall(r'"([^"]+)"', allow))


def profile(npc_id: int, name: str):
    # Exact forNpc rule precedence, NpcAttackStyleProfile.java:379-473.
    special = {865: 'MELEE_MAGIC', 866: 'MELEE_RANGED', 863: 'PURE_RANGED',
               713: 'PURE_MAGIC', 757: 'MELEE_FREQUENT_MAGIC',
               758: 'MELEE_FREQUENT_MAGIC', 759: 'MELEE_FREQUENT_MAGIC',
               760: 'MELEE_RARE_MAGIC'}
    if npc_id in special:
        return special[npc_id], 'special ID ' + str(npc_id)
    n = name.lower()
    if npc_id in (196, 201, 202, 203, 291, 477) or 'dragon' in n:
        return 'MELEE_MAGIC', 'dragon ID/name rule'
    if 'wizard' in n:
        return 'PURE_MAGIC', 'name contains wizard'
    if n == 'monk' or 'priest' in n:
        return 'PURE_MAGIC', 'monk/priest name rule'
    if any(k in n for k in ('black knight', 'white knight', 'grey knight')):
        return 'MELEE_RARE_MAGIC', 'god-knight name rule'
    # Read the exact name switch from the guarded source, avoiding stale copies.
    text = (ROOT / (NPC_DIR + 'NpcAttackStyleProfile.java')).read_text()
    switch = text.split('public static NpcAttackStyleProfile forNpc', 1)[1].split('switch (name)', 1)[1]
    for cases, result in re.findall(r'((?:\s*case "[^"]+":)+)\s*return (\w+);', switch):
        if n in re.findall(r'case "([^"]+)":', cases):
            return result, 'exact name switch: ' + n
    return 'MELEE', 'default'


def resolve_power(d: dict, style: str, enabled: bool):
    key = style + 'Offense'
    if d[key] > 0:
        return d[key], 'explicit_positive', [key]
    if style == 'melee':
        return d['strength'], 'legacy_strength', ['strength']
    if enabled:
        return max(1, d['attack'], d['strength']), 'profile_max_1_attack_strength', ['attack', 'strength']
    if style == 'ranged':
        return d['ranged'], 'legacy_ranged_boolean', ['ranged']
    return 0, 'profile_has_no_magic', []


def resolve_defense(d: dict, style: str):
    key = style + 'Defense'
    if d[key] > 0:
        return d[key], 'explicit_positive', [key], None
    if d['defense'] <= 0:
        return 0, 'nonpositive_legacy_defense', ['defense'], None
    m, div = d[style + 'DefenseMultiplier'], d[style + 'DefenseDivisor']
    if m >= 0:
        mode, dependencies = 'legacy_times_multiplier', ['defense', style + 'DefenseMultiplier']
    elif div > 0:
        m = 1.0 / div
        mode, dependencies = 'legacy_divisor', ['defense', style + 'DefenseDivisor']
    else:
        m = DEFAULT_DEF[style]
        mode, dependencies = 'legacy_default_multiplier', ['defense']
    # Java casts floor(double) to int with saturation. Preserve that edge case.
    value = max(0, min(ZERO_RESERVED, math.floor(d['defense'] * m)))
    return value, mode, dependencies, m


def context(npc_id: int, source: str):
    if npc_id == 861:
        return 'developer_visual_fixture', 'tests/myworld/test-gorak-visual-npc.py:2,46-62,80-83'
    if npc_id == 851:
        return 'reserved_compatibility_definition', DEFS + 'MonsterSlayerNpcDefs.json:7'
    if 863 <= npc_id <= 870:
        return 'production_slayer_encounter_despite_preview_filename', 'tests/myworld/test-slayer-movement-preview.py:35-37'
    if 846 <= npc_id <= 860 or 871 <= npc_id <= 876:
        return 'slayer_contact_supplier_or_associate', source
    return 'ordinary_definition_role_not_exhaustively_classified', source


def self_test():
    d = {'attack': 999, 'strength': 250, 'defense': 80, 'ranged': 0}
    for key in DIRECT: d[key] = 0
    for key in MULTIPLIERS + DIVISORS: d[key] = -1.0
    for s, m in zip(STYLES, (.75, .5, 1.0)): d[s + 'DefenseMultiplier'] = m
    require([resolve_defense(d, s)[0] for s in STYLES] == [60, 40, 80], 'Defense case')
    require(resolve_power(d, 'magic', True)[0] == 999, 'Projectile fallback case')
    require(math.ceil(999 / 12.0) + math.floor(999 / 7.0) == 226, 'Balrog raw maximum case')
    d['meleeDefenseMultiplier'] = -1.0; d['meleeDefenseDivisor'] = -1.0
    require(resolve_defense(d, 'melee')[0] == 80, 'Default multiplier case')
    d['defense'] = 0
    require(resolve_defense(d, 'melee')[0] == 0, 'Nonpositive legacy defense case')
    d['defense'] = 80
    d['meleeDefenseDivisor'] = 4
    require(resolve_defense(d, 'melee')[0] == 20, 'Divisor case')
    d['meleeDefenseMultiplier'] = 0
    require(resolve_defense(d, 'melee')[0] == 0, 'Zero multiplier precedes divisor')
    d['meleeDefense'] = 31
    require(resolve_defense(d, 'melee')[0] == 31, 'Direct positive precedence')
    d['magicOffense'] = 0
    require(resolve_power(d, 'magic', True)[0] == 999, 'Zero direct falls back')
    d['magicOffense'] = ZERO_RESERVED
    require(resolve_power(d, 'magic', True)[0] == ZERO_RESERVED, 'Sentinel remains positive')
    require(profile(865, 'renamed')[0] == 'MELEE_MAGIC', 'Special ID precedence')
    require(profile(760, 'wizard')[0] == 'MELEE_RARE_MAGIC', 'Kolodion precedence')
    require(profile(0, 'Wizard dragon')[0] == 'MELEE_MAGIC', 'Dragon precedence')
    require(profile(869, 'Dark beast')[0] == 'MELEE', 'Special-script caveat case')
    return ['direct/fallback precedence', 'multiplier/divisor/default and zero precedence',
            'positive sentinel hazard', 'profile precedence', 'Balrog raw arithmetic']


def build():
    inputs = [*SOURCE_GUARDS, *(DEFS + f for f in FILES), DEFS + 'NpcDefsMyWorld.json', *CONFIGS, *PLACEMENTS]
    initial_hashes = {p: digest(ROOT / p) for p in sorted(inputs)}
    initial_head = git('rev-parse', 'HEAD')
    configs, allowed = verify_source()
    checks = self_test()
    raw, sources, file_counts = [], [], []
    for filename in FILES:
        relative = DEFS + filename
        records, text = load_array(relative, 'npcs')
        start = len(raw)
        for record in records:
            require(record['id'] == len(raw), 'JSON ID differs from append identity in ' + relative)
            require(record['name'].isascii(), 'Review Java/Python lowercase semantics for non-ASCII name')
            raw.append(record)
            sources.append(reference(relative, text, record['id']))
        file_counts.append({'file': relative, 'first_id': start, 'count': len(records),
                            'attackable': sum(x['attackable'] == 1 for x in records)})
    overrides, text = load_array(DEFS + 'NpcDefsMyWorld.json', 'npcs')
    by_override, override_refs = {}, {}
    for ov in overrides:
        npc_id = ov['id']
        require(npc_id not in by_override and 0 <= npc_id < len(raw), 'Duplicate/unknown override ID')
        require(not (set(ov) - allowed), 'Unknown override keys')
        by_override[npc_id] = ov
        override_refs[npc_id] = reference(DEFS + 'NpcDefsMyWorld.json', text, npc_id)
    placements, placement_summary = {}, []
    for relative in PLACEMENTS:
        data, _ = load_array(relative)
        counts = Counter(x['id'] for x in data)
        placements[relative] = counts
        placement_summary.append({'file': relative, 'rows': len(data), 'distinct_ids': len(counts),
                                  'kind': 'removal_reference' if 'Removals' in relative else 'placement_reference'})
    rows = []
    for npc_id, base in enumerate(raw):
        d = dict(base)
        for key in DIRECT: d.setdefault(key, 0)
        for key in MULTIPLIERS + DIVISORS: d.setdefault(key, -1.0)
        d.setdefault('projectileRange', 0)
        d['ranged'] = 1 if base['ranged'] else 0
        origin = {k: sources[npc_id] if k in base else 'loader default' for k in d}
        ov = by_override.get(npc_id, {})
        for key, value in ov.items():
            if key == 'id': continue
            if key in RESERVE_FIELDS and value == 0: value = ZERO_RESERVED
            if key == 'ranged': value = int(bool(value))
            d[key] = value
            origin[key] = override_refs[npc_id]
        if 'projectileRange' in base or 'projectileRange' in ov:
            require(1 <= d['projectileRange'] <= 15, 'Invalid projectileRange')
        require(all(isinstance(d[k], int) and not isinstance(d[k], bool) for k in DIRECT), 'Non-integer direct stat')
        require(all(math.isfinite(d[k]) for k in MULTIPLIERS + DIVISORS), 'Nonfinite multiplier/divisor')
        p, reason = profile(npc_id, d['name'])
        enabled = {'melee': True, 'ranged': p in ('PURE_RANGED', 'MELEE_RANGED'),
                   'magic': p in ('PURE_MAGIC', 'MELEE_FREQUENT_MAGIC', 'MELEE_MAGIC', 'MELEE_RARE_MAGIC')}
        stats = {}
        for style in STYLES:
            value, mode, deps = resolve_power(d, style, enabled[style])
            stats[style + '_power'] = {'value': value, 'resolution': mode,
                'inputs': {k: {'value': d[k], 'source': origin[k]} for k in deps}}
            value, mode, deps, m = resolve_defense(d, style)
            stats[style + '_defense'] = {'value': value, 'resolution': mode, 'multiplier_used': m,
                'inputs': {k: {'value': d[k], 'source': origin[k]} for k in deps}}
        maximums = {s: max(1, 1 + math.floor(stats[s + '_power']['value'] / 7.0)) for s in ('melee', 'ranged')}
        mp = stats['magic_power']['value']
        maximums['magic'] = math.ceil(max(1.0, mp / 12.0)) + max(0, math.floor(mp / 7.0))
        flags = []
        fallback = any(enabled[s] and stats[s + '_power']['resolution'] == 'profile_max_1_attack_strength' for s in ('ranged', 'magic'))
        if fallback and d['attack'] > d['strength']: flags.append('legacy_attack_dominates_projectile_fallback')
        if fallback and d['attack'] >= 2 * max(1, d['strength']) and d['attack'] - d['strength'] >= 50: flags.append('extreme_attack_strength_gap')
        if enabled['magic'] and maximums['magic'] >= 50: flags.append('high_generic_magic_ceiling')
        if maximums['melee'] >= 50: flags.append('high_melee_ceiling')
        if d['defense'] > 0 and all(stats[s + '_defense']['value'] < d['defense'] for s in STYLES): flags.append('all_defenses_below_legacy')
        if any(d[k] > 1 for k in MULTIPLIERS): flags.append('defense_multiplier_above_one')
        if any(k in {**base, **ov} and d[k] == 0 for k in DIRECT): flags.append('explicit_zero_uses_fallback')
        if any(d[s + 'Offense'] > 0 and not enabled[s] for s in ('ranged', 'magic')): flags.append('positive_power_outside_general_profile')
        if d['attackable'] == 1 and any(d[k] == 0 for k in ('attack', 'strength', 'defense')): flags.append('legacy_zero_on_attackable')
        if d['attackable'] == 1 and d['hits'] <= 0: flags.append('nonpositive_hp_on_attackable')
        if any(d[k] == ZERO_RESERVED for k in (*DIRECT, 'attack', 'strength', 'defense', 'hits', 'combatlvl')): flags.append('reserved_sentinel_loaded')
        known_special = npc_id in (809, 844, 845, 838, 713, 757, 758, 759, 760) or 863 <= npc_id <= 870 or 877 <= npc_id <= 882
        if known_special: flags.append('special_script_review')
        role, role_ref = context(npc_id, sources[npc_id])
        rows.append({'id': npc_id, 'authored_json_id': base['id'], 'name': d['name'],
            'base_name': base['name'], 'description': d['description'], 'definition_source': sources[npc_id],
            'attackable': d['attackable'] == 1, 'aggressive': d['aggressive'] == 1,
            'members': d['isMembers'] == 1, 'hits': d['hits'], 'displayed_combat_level': d['combatlvl'],
            'respawn_time': d['respawnTime'], 'role': role, 'role_evidence': role_ref,
            'holy_power': None, 'holy_status': 'unsupported_npc_stat_not_zero',
            'general_profile': p, 'profile_rule': reason,
            'profile_uses_ranged': enabled['ranged'], 'profile_uses_magic': enabled['magic'],
            'projectile_range': d['projectileRange'] if d['projectileRange'] > 0 else 5,
            'projectile_range_source': origin['projectileRange'] if d['projectileRange'] > 0 else 'profile default 5',
            'stats': stats, 'ordinary_raw_max_if_used': maximums,
            'maxima_caveat': 'formula-only before mitigation/modifiers; magic assumes generic profile spellPower; does not enable any style or prove real encounter maximum',
            'base_authored_stat_fields': {k: base[k] for k in ('attack', 'strength', 'defense', 'hits', 'combatlvl', 'ranged', *DIRECT, *MULTIPLIERS, *DIVISORS) if k in base},
            'override_source': override_refs.get(npc_id), 'override_fields': ov,
            'loaded_stat_fields': {k: {'value': d[k], 'source': origin[k], 'present_in_authored_record': k in base or k in ov} for k in ('attack', 'strength', 'defense', 'ranged', *DIRECT, *MULTIPLIERS, *DIVISORS)},
            'static_reference_counts': {path: counts[npc_id] for path, counts in placements.items()},
            'review_flags': flags})
    attackable = [r for r in rows if r['attackable']]
    direct_counts = {s + '_' + kind: sum(r['stats'][s + '_' + kind]['resolution'] == 'explicit_positive' for r in attackable) for kind in ('power', 'defense') for s in STYLES}
    summary = {'definitions': len(rows), 'attackable': len(attackable), 'nonattackable': len(rows) - len(attackable),
        'file_counts': file_counts, 'override_records': len(overrides),
        'positive_direct_stat_counts_attackable': direct_counts,
        'all_six_positive_direct_ids': [r['id'] for r in attackable if all(v['resolution'] == 'explicit_positive' for v in r['stats'].values())],
        'all_three_defenses_fallback_attackable': sum(all(r['stats'][s + '_defense']['resolution'] != 'explicit_positive' for s in STYLES) for r in attackable),
        'general_profiles_attackable': dict(sorted(Counter(r['general_profile'] for r in attackable).items())),
        'flag_counts_attackable': {k: sum(k in r['review_flags'] for r in attackable) for k in FLAG_RULES},
        'explicit_direct_zero_ids': [r['id'] for r in rows if 'explicit_zero_uses_fallback' in r['review_flags']],
        'placement_sources': placement_summary,
        'holy_supported_npc_definitions': 0,
        'checks': checks + ['append ID validation', 'override key/ID validation', 'configured assumptions', 'source fingerprint guards']}
    hashes = {p: digest(ROOT / p) for p in sorted(inputs)}
    require(hashes == initial_hashes and git('rev-parse', 'HEAD') == initial_head,
            'Inputs or HEAD changed during inventory; rerun against a stable snapshot')
    tree_hash = hashlib.sha256(json.dumps(hashes, sort_keys=True).encode()).hexdigest()
    metadata = {'schema_version': 1, 'head': git('rev-parse', 'HEAD'), 'branch': git('branch', '--show-current'),
        'input_tree_sha256': tree_hash, 'source_hashes': hashes, 'generator_sha256': digest(Path(__file__)),
        'tracked_input_changes_from_head': git('diff', 'HEAD', '--name-only', '--', *inputs).splitlines(),
        'config_assumptions': configs, 'config_dir_assumption': DEFS.removesuffix('/defs/'),
        'scope': 'All definitions loaded by checked-in MyWorld settings; static generic-Npc baseline before transient effects, not live population or encounter simulation.',
        'exclusions': ['live deployment and DB', 'temporary effects and runtime mutations', 'full script/quest/summon simulation',
                       'other configuration profiles', 'exact dynamic placement population', 'approved future stat values'],
        'custom_npc_conditions': 'Only commands change in traced EntityHandler:540-562; omitted from this stat inventory.',
        'review_flag_rules': FLAG_RULES}
    return {'metadata': metadata, 'summary': summary, 'npcs': rows}


def make_csv(rows):
    flat = []
    for r in rows:
        x = {k: r[k] for k in ('id', 'name', 'attackable', 'role', 'general_profile', 'profile_rule',
            'profile_uses_ranged', 'profile_uses_magic', 'hits', 'displayed_combat_level', 'aggressive', 'members', 'respawn_time', 'definition_source', 'override_source', 'holy_status', 'maxima_caveat')}
        for s in STYLES:
            for kind in ('power', 'defense'):
                key = s + '_' + kind; v = r['stats'][key]
                x[key] = v['value']; x[key + '_resolution'] = v['resolution']
                x[key + '_inputs'] = '; '.join(k + '=' + str(t['value']) + ' @ ' + t['source'] for k, t in v['inputs'].items())
            x[s + '_raw_max_if_used'] = r['ordinary_raw_max_if_used'][s]
        for k in ('attack', 'strength', 'defense', 'ranged'):
            x['loaded_legacy_' + k] = r['loaded_stat_fields'][k]['value']
        x['override_fields'] = json.dumps(r['override_fields'], sort_keys=True, separators=(',', ':'))
        x['review_flags'] = ';'.join(r['review_flags'])
        for p, n in r['static_reference_counts'].items(): x[Path(p).stem + '_raw_references'] = n
        flat.append(x)
    output = io.StringIO(newline='')
    writer = csv.DictWriter(output, fieldnames=list(flat[0]), lineterminator='\n')
    writer.writeheader(); writer.writerows(flat)
    return output.getvalue()


def output_files(data):
    return {'inventory.json': json.dumps(data, indent=2, ensure_ascii=False) + '\n',
            'all-npcs.csv': make_csv(data['npcs']),
            'attackable-npcs.csv': make_csv([r for r in data['npcs'] if r['attackable']]),
            'review-flags.csv': make_csv([r for r in data['npcs'] if r['attackable'] and r['review_flags']]),
            'summary.json': json.dumps({'metadata': data['metadata'], 'summary': data['summary']}, indent=2) + '\n'}


def output_directory(label):
    require(bool(re.fullmatch(r'[a-zA-Z0-9_-]+', label)), 'Unsafe output label')
    result = subprocess.check_output(['python3', 'scripts/workspace-layout.py', 'path',
                'diagnostics', 'npc-stat-inventory', label], cwd=ROOT, text=True).strip()
    path = Path(result)
    require(path.resolve().is_relative_to((ROOT / 'output/diagnostics/npc-stat-inventory').resolve()), 'Output escaped category')
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--write', metavar='RUN', help='write only new/identical files under the diagnostics category')
    group.add_argument('--check', metavar='RUN', help='read-only byte comparison with an existing bundle')
    args = parser.parse_args()
    data = build()
    if args.check:
        archived = json.loads((output_directory(args.check) / 'inventory.json').read_text())
        # Generation HEAD/branch are historical provenance, not data inputs.
        # Keep their captured values when reproducing the archived bytes, and
        # explicitly report the currently selected checkout. Every other field,
        # input fingerprint, generator fingerprint and output remains strict.
        for key in ('head', 'branch'):
            current, captured = data['metadata'][key], archived['metadata'][key]
            if current != captured:
                print('PROVENANCE ' + key + ': captured=' + str(captured) + '; current=' + str(current))
            data['metadata'][key] = captured
    files = output_files(data)
    if args.write or args.check:
        target = output_directory(args.write or args.check)
        if args.write:
            # Reject nonidentical existing output; preserve earlier evidence.
            for name, content in files.items():
                dest = target / name
                require(not dest.is_symlink(), 'Output symlink rejected')
                require(not dest.exists() or dest.read_bytes() == content.encode(), 'Existing output differs; use a new run label: ' + str(dest))
            subprocess.run(['python3', 'scripts/workspace-layout.py', 'path', 'diagnostics',
                            'npc-stat-inventory', args.write, '--mkdir'], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
            for name, content in files.items():
                dest = target / name
                if not dest.exists(): dest.write_bytes(content.encode())
        else:
            for name, content in files.items():
                require((target / name).read_bytes() == content.encode(), 'Reproduction mismatch: ' + name)
        print(('WROTE/VERIFIED ' if args.write else 'REPRODUCED ') + str(target))
        for name in files: print(name, hashlib.sha256(files[name].encode()).hexdigest())
    print(json.dumps(data['summary'], indent=2))

if __name__ == '__main__':
    main()
