package com.openrsc.server.constants;

import com.openrsc.server.model.entity.EntityType;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpellDamages {

	public enum MagicType {
		GOODEVILMAGIC,
		F2PONLYMAGIC,
		MODERNMAGIC
	}

	private final Map<Spells, ArrayList<Pair<EntityType, Double>>> dividedMagicProjectiles = new HashMap<Spells, ArrayList<Pair<EntityType, Double>>>() {{
		put(Spells.CHILL_BOLT, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 3.0)); add(Pair.of(EntityType.NPC, 6.0));
		}});
		put(Spells.SHOCK_BOLT, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 3.5)); add(Pair.of(EntityType.NPC, 7.0));
		}});
		put(Spells.WIND_BOLT_R, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 4.0)); add(Pair.of(EntityType.NPC, 8.0));
		}});
		put(Spells.ELEMENTAL_BOLT, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 5.0)); add(Pair.of(EntityType.NPC, 10.0));
		}});
	}};

	private final Map<Spells, ArrayList<Pair<EntityType, Double>>> f2pOnlyMagicProjectiles = new HashMap<Spells, ArrayList<Pair<EntityType, Double>>>() {{
		put(Spells.WIND_STRIKE, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 4.0)); add(Pair.of(EntityType.NPC, 4.0));
		}});
		put(Spells.WATER_STRIKE, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 4.0)); add(Pair.of(EntityType.NPC, 4.0));
		}});
		put(Spells.EARTH_STRIKE, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 4.0)); add(Pair.of(EntityType.NPC, 4.0));
		}});
		put(Spells.FIRE_STRIKE, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 4.0)); add(Pair.of(EntityType.NPC, 4.0));
		}});
		put(Spells.THUNDER_BALL, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 4.8)); add(Pair.of(EntityType.NPC, 4.8));
		}});
		put(Spells.ICICLE_SHOT, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 4.8)); add(Pair.of(EntityType.NPC, 4.8));
		}});
		put(Spells.ACID_DROP, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 4.8)); add(Pair.of(EntityType.NPC, 4.8));
		}});
		put(Spells.BRANCH_SPORE, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 4.8)); add(Pair.of(EntityType.NPC, 4.8));
		}});
		put(Spells.WIND_BOLT, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 6.0)); add(Pair.of(EntityType.NPC, 6.0));
		}});
		put(Spells.WATER_BOLT, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 6.0)); add(Pair.of(EntityType.NPC, 6.0));
		}});
		put(Spells.EARTH_BOLT, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 6.0)); add(Pair.of(EntityType.NPC, 6.0));
		}});
		put(Spells.FIRE_BOLT, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 6.0)); add(Pair.of(EntityType.NPC, 6.0));
		}});
		put(Spells.THUNDER_SPLASH, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 7.2)); add(Pair.of(EntityType.NPC, 7.2));
		}});
		put(Spells.ICE_BURST, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 7.2)); add(Pair.of(EntityType.NPC, 7.2));
		}});
		put(Spells.ACID_FROG, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 7.2)); add(Pair.of(EntityType.NPC, 7.2));
		}});
		put(Spells.WOOD_DRILL, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 7.2)); add(Pair.of(EntityType.NPC, 7.2));
		}});
		put(Spells.WIND_BLAST, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 8.0)); add(Pair.of(EntityType.NPC, 8.0));
		}});
		put(Spells.WATER_BLAST, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 8.0)); add(Pair.of(EntityType.NPC, 8.0));
		}});
		put(Spells.EARTH_BLAST, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 8.0)); add(Pair.of(EntityType.NPC, 8.0));
		}});
		put(Spells.FIRE_BLAST, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 8.0)); add(Pair.of(EntityType.NPC, 8.0));
		}});
	}};

	private final Map<Spells, ArrayList<Pair<EntityType, Double>>> modernMagicProjectiles = new HashMap<Spells, ArrayList<Pair<EntityType, Double>>>() {{
		put(Spells.WIND_STRIKE, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 4.0)); add(Pair.of(EntityType.NPC, 4.0));
		}});
		put(Spells.WATER_STRIKE, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 4.0)); add(Pair.of(EntityType.NPC, 4.0));
		}});
		put(Spells.EARTH_STRIKE, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 4.0)); add(Pair.of(EntityType.NPC, 4.0));
		}});
		put(Spells.FIRE_STRIKE, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 4.0)); add(Pair.of(EntityType.NPC, 4.0));
		}});
		put(Spells.WIND_BOLT, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 6.0)); add(Pair.of(EntityType.NPC, 6.0));
		}});
		put(Spells.WATER_BOLT, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 6.0)); add(Pair.of(EntityType.NPC, 6.0));
		}});
		put(Spells.EARTH_BOLT, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 6.0)); add(Pair.of(EntityType.NPC, 6.0));
		}});
		put(Spells.FIRE_BOLT, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 6.0)); add(Pair.of(EntityType.NPC, 6.0));
		}});
		put(Spells.WIND_BLAST, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 8.0)); add(Pair.of(EntityType.NPC, 8.0));
		}});
		put(Spells.WATER_BLAST, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 8.0)); add(Pair.of(EntityType.NPC, 8.0));
		}});
		put(Spells.EARTH_BLAST, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 8.0)); add(Pair.of(EntityType.NPC, 8.0));
		}});
		put(Spells.FIRE_BLAST, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 8.0)); add(Pair.of(EntityType.NPC, 8.0));
		}});
		put(Spells.THUNDER_STRIKE, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 9.6)); add(Pair.of(EntityType.NPC, 9.6));
		}});
		put(Spells.ICE_CRYSTAL, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 9.6)); add(Pair.of(EntityType.NPC, 9.6));
		}});
		put(Spells.ACID_GUSH, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 9.6)); add(Pair.of(EntityType.NPC, 9.6));
		}});
		put(Spells.BATTERING_RAM, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 9.6)); add(Pair.of(EntityType.NPC, 9.6));
		}});
		put(Spells.WIND_WAVE, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 10.0)); add(Pair.of(EntityType.NPC, 10.0));
		}});
		put(Spells.WATER_WAVE, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 10.0)); add(Pair.of(EntityType.NPC, 10.0));
		}});
		put(Spells.EARTH_WAVE, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 10.0)); add(Pair.of(EntityType.NPC, 10.0));
		}});
		put(Spells.FIRE_WAVE, new ArrayList<Pair<EntityType, Double>>(){{
			add(Pair.of(EntityType.PLAYER, 10.0)); add(Pair.of(EntityType.NPC, 10.0));
		}});
	}};

	public double getSpellDamage(Spells spell, EntityType entityType, MagicType magicType) {
		double damage = -1.0;

		switch(magicType) {
			case GOODEVILMAGIC:
				if (dividedMagicProjectiles.containsKey(spell)) {
					damage = getSpellDamage(entityType, dividedMagicProjectiles.get(spell));
				}
				break;
			case F2PONLYMAGIC:
				if (f2pOnlyMagicProjectiles.containsKey(spell)) {
					damage = getSpellDamage(entityType, f2pOnlyMagicProjectiles.get(spell));
				}
				break;
			case MODERNMAGIC:
				if (modernMagicProjectiles.containsKey(spell)) {
					damage = getSpellDamage(entityType, modernMagicProjectiles.get(spell));
				}
				break;
		}

		return damage;
	}

	private Double getSpellDamage(EntityType entityType, List<Pair<EntityType, Double>> fromSpellDamages) {
		double damage = 0;

		for (Pair<EntityType, Double> spellDamage : fromSpellDamages) {
			if (spellDamage.getKey() == entityType) {
				damage = spellDamage.getValue();
				break;
			}
		}

		return damage;
	}
}
