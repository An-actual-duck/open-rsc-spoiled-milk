package com.openrsc.server.content.market;

import com.openrsc.server.model.combat.CombatParticipantSnapshot;
import com.openrsc.server.model.entity.player.Player;
import java.util.UUID;

/** One server-created request identity, retained when that queued request is retried. */
public final class MarketOperation {
    public enum Kind { BUY, CREATE, CANCEL, COLLECT, MODERATE, EXPIRE }
    public final UUID id;
    public final Kind kind;
    public final Player actor;
    public final int actorId, auctionId, catalogId, amount, price;
    private final CombatParticipantSnapshot session;

    public MarketOperation(Kind kind, Player actor, int auctionId, int catalogId, int amount, int price) {
        this(UUID.randomUUID(), kind, actor, auctionId, catalogId, amount, price);
    }

    private MarketOperation(UUID id, Kind kind, Player actor, int auctionId, int catalogId, int amount, int price) {
        this.id = id; this.kind = kind; this.actor = actor;
        actorId = actor == null ? 0 : actor.getDatabaseID();
        session = actor == null ? null : CombatParticipantSnapshot.capture(actor);
        this.auctionId = auctionId; this.catalogId = catalogId; this.amount = amount; this.price = price;
    }

    public static MarketOperation expiry(int auctionId) {
        UUID id = UUID.nameUUIDFromBytes(("market-expiry-v1:" + auctionId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new MarketOperation(id, Kind.EXPIRE, null, auctionId, 0, 0, 0);
    }

    public boolean hasCurrentActor() {
        return actor != null && actorId > 0 && actor.getDatabaseID() == actorId
            && actor.isLoggedIn() && !actor.isUnregistering() && !actor.isLoggingOut()
            && !actor.isPersistenceSessionClosed() && session.matchesIdentityAndSession(actor)
            && actor.getWorld().getPlayer(actor.getUsernameHash()) == actor
            && actor.getWorld().getPlayerID(actorId) == actor;
    }

    public String requestKey() { return auctionId + ":" + catalogId + ":" + amount + ":" + price; }
}
