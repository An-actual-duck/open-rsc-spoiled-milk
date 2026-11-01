package com.openrsc.server.content.market.task;

import com.openrsc.server.content.market.MarketItem;
import com.openrsc.server.content.market.MarketOperation;
import com.openrsc.server.model.entity.player.Player;

/** Retains one server-generated operation identity through any retry. */
public final class NewMarketItemTask extends MarketTask {
    private final MarketOperation operation;
    public NewMarketItemTask(Player player, MarketItem item) {
        operation = new MarketOperation(MarketOperation.Kind.CREATE, player, 0, item.getCatalogID(), item.getAmount(), item.getPrice());
    }
    public java.util.UUID getOperationId() { return operation.id; }
    @Override public void doTask() { operation.actor.getWorld().getMarket().getTransactions().execute(operation); }
}
