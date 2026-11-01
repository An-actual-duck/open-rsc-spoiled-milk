package com.openrsc.server.content.market.task;

import com.openrsc.server.content.market.MarketOperation;
import com.openrsc.server.model.entity.player.Player;

/** Retains one server-generated operation identity through any retry. */
public final class BuyMarketItemTask extends MarketTask {
    private final MarketOperation operation;
    public BuyMarketItemTask(Player buyer, final int auctionID, int amount) {
        operation = new MarketOperation(MarketOperation.Kind.BUY, buyer, auctionID, 0, amount, 0);
    }
    public java.util.UUID getOperationId() { return operation.id; }
    @Override public void doTask() { operation.actor.getWorld().getMarket().getTransactions().execute(operation); }
}
