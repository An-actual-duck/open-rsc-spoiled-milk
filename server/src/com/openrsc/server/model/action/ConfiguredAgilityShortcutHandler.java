package com.openrsc.server.model.action;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.SingleTickEvent;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.io.AgilityShortcut;
import com.openrsc.server.io.AgilityShortcutLanding;
import com.openrsc.server.io.NativeLayeredSceneryPlacement;
import com.openrsc.server.io.NativeLayeredWorldPackage;
import com.openrsc.server.io.NativeLayeredPlacementSet;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.NativeLayeredGameObjectIdentity;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.region.RegionManager;
import java.util.concurrent.ThreadLocalRandom;

/** Explicit generic action only; existing shortcuts and all other plugin commands are untouched. */
public final class ConfiguredAgilityShortcutHandler {
	private ConfiguredAgilityShortcutHandler() {}
	public static boolean handle(final Player player, final GameObject object, String command) {
		if (AgilityShortcut.Template.forAction(command) == null) return false;
		final RegionManager regions = player.getWorld().getRegionManager();
		final NativeLayeredGameObjectIdentity identity = object.getLoc().getNativeLayeredGameObjectIdentity();
		final NativeLayeredSceneryPlacement placement = find(regions, identity);
		if (placement == null || placement.getAgilityShortcut() == null || !placement.getAgilityShortcut().getTemplate().action.equals(command)) {
			player.message("This shortcut has not been configured yet."); return true;
		}
		final NativeLayeredWorldPackage owner = regions.findNativeLayeredWorldPackage(identity.getPackageId()).orElse(null);
		ConfiguredAgilityAction.begin(new ConfiguredAgilityAction.Actor() {
			public boolean available() { return player.isLoggedIn() && !player.isRemoved()
				&& player.getSkills().getLevel(Skill.HITS.id()) > 0 && !player.inCombat() && !player.getDuel().isDueling(); }
			public boolean busy() { return player.isBusy(); }
			public void busy(boolean value) { player.setBusy(value); }
			public int effectiveAgility() { return player.getSkills().getLevel(Skill.AGILITY.id()); }
			public WorldLocation location() { return player.getWorldLocation(); }
			public Object pending() { return player.getAttribute(ConfiguredAgilityAction.PENDING_ATTRIBUTE); }
			public void pending(Object token) { if (token == null) player.removeAttribute(ConfiguredAgilityAction.PENDING_ATTRIBUTE);
				else player.setAttribute(ConfiguredAgilityAction.PENDING_ATTRIBUTE, token); }
			public boolean placementCurrent() { return !object.isRemoved() && regions.findNativeLayeredGameObject(object) == object
				&& find(regions, identity) == placement && player.getWorldLocation().getWorldSpace().equals(identity.getLocation().getWorldSpace())
				&& player.getWorldLocation().getCoordinate().getLevel() == identity.getLocation().getCoordinate().getLevel()
				&& player.atObject(object); }
			public void requireSafe(WorldLocation location) {
				if (owner == null || regions.findNativeLayeredWorldPackage(location).orElse(null) != owner)
					throw new IllegalStateException("Shortcut destination has no matching authored package");
				AgilityShortcutLanding.requireSafe(location, new AgilityShortcutLanding.Terrain() {
					public com.openrsc.server.io.NativeLayeredTerrainTile tile(WorldLocation at) { return owner.findTile(at).orElse(null); }
					public com.openrsc.server.external.TileDef definition(int id) { return player.getWorld().getServer().getEntityHandler().getTileDef(id); }
					public boolean collisionClear(WorldLocation at) { return AgilityShortcutLanding.collisionMaskClear(regions.getTile(at).traversalMask); }
				});
			}
			public void schedule(final Runnable completion) {
				player.face(object);
				boolean added = player.getWorld().getServer().getGameEventHandler().add(new SingleTickEvent(player.getWorld(), player, placement.getAgilityShortcut().getTemplate().ticks,
					"Configured agility " + placement.getAgilityShortcut().getTemplate().id, DuplicationStrategy.ALLOW_MULTIPLE) {
					public void action() { completion.run(); }
				});
				if (!added) throw new IllegalStateException("Shortcut could not be scheduled");
			}
			public void message(String message) { player.message(message); }
			public void land(WorldLocation location) { player.teleportLayered(location, false); }
		}, placement.getAgilityShortcut(), () -> ThreadLocalRandom.current().nextInt(10000));
		return true;
	}
	private static NativeLayeredSceneryPlacement find(RegionManager regions, NativeLayeredGameObjectIdentity identity) {
		if (identity == null || !"scenery".equals(identity.getKind())) return null;
		NativeLayeredWorldPackage owner = regions.findNativeLayeredWorldPackage(identity.getPackageId()).orElse(null);
		if (owner == null) return null;
		for (NativeLayeredPlacementSet set : owner.getPlacementSets().values()) for (NativeLayeredSceneryPlacement placement : set.getScenery())
			if (identity.getPlacementId().equals(placement.getPlacementId()) && identity.getLocation().equals(placement.getLocation())) return placement;
		return null;
	}
}
