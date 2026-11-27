package com.openrsc.server.content.production;

import com.openrsc.server.model.entity.player.Player;

import java.util.function.ObjIntConsumer;

/** The tab callback changes the authoritative reward catalog for the current vendor. */
public final class PointShopTabs {
	public static final String CLIENT_ATTRIBUTE = "point_shop_tabs_client";
	private final String[] labels;
	private final int selected;
	private final ObjIntConsumer<Player> openCatalog;

	public PointShopTabs(String[] labels, int selected, ObjIntConsumer<Player> openCatalog) {
		this.labels = labels.clone();
		this.selected = selected;
		this.openCatalog = openCatalog;
	}

	public String[] getLabels() { return labels.clone(); }
	public int getSelected() { return selected; }

	public void open(Player player, int tab) {
		if (tab >= 0 && tab < labels.length && tab != selected) {
			openCatalog.accept(player, tab);
		}
	}
}
