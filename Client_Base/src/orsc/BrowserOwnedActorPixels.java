package orsc;

import java.util.Map;
import java.util.IdentityHashMap;
import orsc.graphics.three.Renderer3DSettings;
import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.SpriteArchive.Frame;

/** Private publication policy: native/software defaults never copy or trust actor data. */
public final class BrowserOwnedActorPixels {
    private BrowserOwnedActorPixels() { }
    public static boolean isEnabled() {
        return Boolean.getBoolean("spoiledmilk.browserOwnedActorPixels")
            && Renderer3DSettings.isBrowserPresentationAvailable();
    }
    public static final long MAX_COPY_BYTES = 64L * 1024 * 1024;
    public static final int MAX_COPIED_FRAMES = 8192, MAX_MEMO_ENTRIES = 8192;
    private static volatile PublicationReport lastReport = new PublicationReport(MAX_COPY_BYTES,
        MAX_COPIED_FRAMES, MAX_MEMO_ENTRIES, new long[10], false);

    public static final class PublicationReport {
        public final long payloadBudget, frameLimit, memoLimit;
        public final long copiedPrimaryBytes, copiedAuxiliaryBytes, copiedFrames, sharedReferences;
        public final long alreadyPublishedReferences, unsupportedFallbacks, byteBudgetFallbacks;
        public final long frameBudgetFallbacks, memoBudgetFallbacks, memoEntries;
        public final boolean enabled;
        private PublicationReport(long bytes, int frames, int memo, long[] c, boolean enabled) {
            payloadBudget=bytes; frameLimit=frames; memoLimit=memo;
            copiedPrimaryBytes=c[0]; copiedAuxiliaryBytes=c[1]; copiedFrames=c[2]; sharedReferences=c[3];
            alreadyPublishedReferences=c[4]; unsupportedFallbacks=c[5]; byteBudgetFallbacks=c[6];
            frameBudgetFallbacks=c[7]; memoBudgetFallbacks=c[8]; memoEntries=c[9]; this.enabled=enabled;
        }
        public double[] counters() {
            return new double[]{payloadBudget,frameLimit,memoLimit,copiedPrimaryBytes,copiedAuxiliaryBytes,
                copiedFrames,sharedReferences,alreadyPublishedReferences,unsupportedFallbacks,
                byteBudgetFallbacks,frameBudgetFallbacks,memoBudgetFallbacks,memoEntries,enabled?1:0};
        }
    }
    public static double[] publicationCounters() { return lastReport.counters(); }

    public static void publish(Map<String, Map<String, Entry>> tree) {
        lastReport = publishWithLimits(tree, MAX_COPY_BYTES, MAX_COPIED_FRAMES, MAX_MEMO_ENTRIES);
    }
    /** Package-visible small budgets exercise exhaustion without allocating production-sized fixtures. */
    static PublicationReport publishWithLimits(Map<String, Map<String, Entry>> tree,
            long byteBudget, int frameLimit, int memoLimit) {
        if (byteBudget < 0 || byteBudget > MAX_COPY_BYTES || frameLimit < 0
            || frameLimit > MAX_COPIED_FRAMES || memoLimit < 0 || memoLimit > MAX_MEMO_ENTRIES)
            throw new IllegalArgumentException("Invalid actor publication budget");
        long[] counts = new long[10];
        if (!isEnabled() || tree == null) return new PublicationReport(byteBudget,frameLimit,memoLimit,counts,false);
        IdentityHashMap<Frame, Frame> copies = new IdentityHashMap<Frame, Frame>();
        // Type, not a mutable name, defines the audited decoded actor families.
        for (Map<String, Entry> space : tree.values()) {
            if (space == null) continue;
            for (Entry entry : space.values()) {
                if (entry == null || entry.getClass() != Entry.class || !actorType(entry.getType())) continue;
                Frame[] frames = entry.getFrames();
                for (int i = 0; i < frames.length; i++) {
                    Frame source = frames[i];
                    if (source == null) continue;
                    if (source.isOwnedActorPublication()) { counts[4]++; continue; }
                    Frame copy = copies.get(source);
                    if (copy != null) { counts[3]++; frames[i] = copy; continue; }
                    // No new references retained after the bounded identity table fills.
                    if (copies.size() >= memoLimit) { counts[8]++; continue; }
                    long remaining = byteBudget - counts[0] - counts[1];
                    com.openrsc.client.model.Sprite.ActorCopyCost cost = source.ownedActorCopyCost();
                    copy = source;
                    if (cost == null) counts[5]++;
                    else if (counts[2] >= frameLimit) counts[7]++;
                    else if (cost.totalBytes() > remaining) counts[6]++;
                    else {
                        copy = source.copyForOwnedActor(remaining);
                        if (copy != source) {
                            counts[0] += cost.primaryBytes; counts[1] += cost.auxiliaryBytes; counts[2]++;
                        } else counts[5]++;
                    }
                    copies.put(source, copy); frames[i] = copy;
                }
            }
        }
        counts[9] = copies.size();
        return new PublicationReport(byteBudget,frameLimit,memoLimit,counts,true);
    }
    private static boolean actorType(Entry.TYPE type) {
        return type == Entry.TYPE.PLAYER_PART || type == Entry.TYPE.NPC
            || type == Entry.TYPE.PLAYER_EQUIPPABLE_HASCOMBAT
            || type == Entry.TYPE.PLAYER_EQUIPPABLE_NOCOMBAT;
    }
}
