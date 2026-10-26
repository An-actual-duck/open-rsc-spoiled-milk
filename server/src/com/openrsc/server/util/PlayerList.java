package com.openrsc.server.util;

import com.openrsc.server.model.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public class PlayerList extends EntityList<Player> {
    private final Map<Long, Player> playerHashIndex = new HashMap<>();

    public PlayerList(int capacity) {
        super(capacity);
    }

    @Override
    public synchronized boolean add(Player entity) {
        if(super.add(entity)) {
            playerHashIndex.put(entity.getUsernameHash(), entity);
            return true;
        }
        return false;
    }

    @Override
    public synchronized Player remove(int index) {
        Player player = super.remove(index);
        if(player != null) {
            if (playerHashIndex.get(player.getUsernameHash()) == player) {
                playerHashIndex.remove(player.getUsernameHash());
            }
        }
        return player;
    }

    @Override
    public synchronized void clear() {
        super.clear();
        playerHashIndex.clear();
    }

    /**
     * Gets a player by their username hash
     * @param hash username hash
     * @return the player associated with this hash
     */
    public synchronized Player getPlayerByHash(long hash) {
        return playerHashIndex.get(hash);
    }
	/**
	 * Remove a player by their username hash
	 * @param hash username hash
	 * @return the player associated with this hash
	 */
	public synchronized Player removePlayerByHash(long hash) {
        Player player = playerHashIndex.get(hash);
        if (player != null) remove(player);
        return player;
	}
}
