package com.openrsc.server.content.market.task;

import com.openrsc.server.content.market.MarketOperation;
import com.openrsc.server.model.entity.player.Player;

/** Retains one server-generated operation identity through any retry. */
public final class CancelMarketItemTask extends MarketTask {
    private final MarketOperation operation;
    public CancelMarketItemTask(Player owner, final int auctionID) {
        operation = new MarketOperation(MarketOperation.Kind.CANCEL, owner, auctionID, 0, 0, 0);
    }
    public java.util.UUID getOperationId() { return operation.id; }
    @Override public void doTask() { operation.actor.getWorld().getMarket().getTransactions().execute(operation); }
}
