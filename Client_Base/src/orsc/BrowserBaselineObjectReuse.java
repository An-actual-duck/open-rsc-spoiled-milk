package orsc;

import java.util.*;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.GameObjectDef;
import orsc.graphics.three.RSModel;
import orsc.graphics.three.WholeOverworldZoneRegistry;

/** Private baseline workspace. Exact proofs authorize clone reuse, never scene authority. */
final class BrowserBaselineObjectReuse {
    static final int MAX_OBJECTS = 4096, MAX_TEMPLATES = 512;
    static final long MAX_BYTES = 16L * 1024 * 1024;
    private static final boolean ENABLED = Boolean.getBoolean("spoiledmilk.browserBenchmark")
        && Boolean.getBoolean("spoiledmilk.browserBaselineReuse");
    private final Map<Integer, Template> templates = new HashMap<Integer, Template>();
    private Map<Key, RSModel> candidates;
    private Set<Integer> usedTemplates;
    private Map<Integer, Template> verifiedTemplates;
    private String scope;
    private long bytes;
    long reused, cloned, refused, resets, peakBytes, admitted;
    long retainedBytes() { return bytes; }
    int retainedTemplates() { return templates.size(); }
    static boolean isEnabled() { return ENABLED; }
    void clear() { templates.clear(); candidates = null; usedTemplates = null; verifiedTemplates = null; scope = null; bytes = 0; resets++; }

    boolean begin(mudclient mc, List<SceneBaselineState.Record> incoming) {
        NativeLayeredTerrainSnapshot source = mc == null || mc.getWorld() == null ? null
            : mc.getWorld().getWholeOverworldSourceSnapshot();
        if (!ENABLED || !Boolean.getBoolean("spoiledmilk.browserWholeRam")
            || !WholeOverworldZoneRegistry.isStaticSceneReuseEnabled()
            || !orsc.graphics.three.Renderer3DSettings.isBrowserPresentationAvailable()
            || source == null || source.getLevel() != 0 || !"global".equals(source.getWorldSpace())
            || mc.getRequestedPlane() != 0 || mc.getWorld().hasWholeOverworldTerrainPatches()
            || mc.getGameObjectInstanceCount() > MAX_OBJECTS || incoming.size() > MAX_OBJECTS) { clear(); return false; }
        String current = source.getWorldSpace() + ":" + source.getLevel() + ":" + source.packageIdentity()
            + ":" + Config.S_WANT_CUSTOM_SPRITES + ":" + Config.S_WANT_CUSTOM_LANDSCAPE
            + ":" + RendererGeometrySettings.getMode() + ":" + RendererLightingSettings.getMode()
            + ":" + RendererToneSettings.getMode() + ":" + RendererFogSettings.getMode();
        if (!current.equals(scope)) { clear(); scope = current; }
        candidates = new HashMap<Key, RSModel>(); usedTemplates = new HashSet<Integer>();
        verifiedTemplates = new HashMap<Integer, Template>();
        Set<Key> ambiguous = new HashSet<Key>();
        Set<Key> seenExisting = new HashSet<Key>();
        Set<Key> seenIncoming = new HashSet<Key>();
        for (SceneBaselineState.Record record : incoming) {
            Key key = new Key(record.x, record.y, record.id, record.direction);
            if (!seenIncoming.add(key)) ambiguous.add(key);
        }
        Map<Integer, Boolean> checkedTemplates = new HashMap<Integer, Boolean>();
        boolean duplicateReference = false;
        IdentityHashMap<RSModel, Key> references = new IdentityHashMap<RSModel, Key>();
        for (int i = 0; i < mc.getGameObjectInstanceCount(); i++) {
            int id = mc.getGameObjectInstanceID(i), direction = mc.getGameObjectInstanceDir(i);
            Key key = new Key(mc.getGameObjectInstanceX(i) + mc.getMidRegionBaseX(),
                mc.getGameObjectInstanceZ(i) + mc.getMidRegionBaseZ(), id, direction);
            RSModel model = mc.getGameObjectInstanceModel(i);
            Key priorReference = model == null ? null : references.put(model, key);
            if (priorReference != null) { duplicateReference = true; ambiguous.add(priorReference); ambiguous.add(key); }
            if (!seenExisting.add(key)) ambiguous.add(key);
            Template template = templates.get(id);
            Boolean valid = checkedTemplates.get(id);
            if (valid == null) {
                GameObjectDef def = EntityHandler.getObjectDef(id);
                valid = template != null && def != null && def.modelID >= 0
                    && template.matchesTemplate(def, mc.getModelCacheItem(def.modelID));
                checkedTemplates.put(id, valid);
                if (valid) verifiedTemplates.put(id, template);
            }
            if (model == null || !mc.isBrowserBaselineStaticObject(id) || !valid
                || !template.proof.matchesModelGeometry(model) || !model.isBrowserBaselineRotation(direction)) { refused++; continue; }
            candidates.put(key, model);
        }
        if (duplicateReference) { refused++; clear(); return false; }
        for (Key key : ambiguous) candidates.remove(key);
        // Object keys, reference/ambiguity tables and hash-table slack are transient,
        // conservatively charged at 512 bytes per existing instance.
        if (bytes + mc.getGameObjectInstanceCount() * 512L > MAX_BYTES) { clear(); return false; }
        peakBytes = Math.max(peakBytes, bytes + mc.getGameObjectInstanceCount() * 512L);
        admitted++;
        return true;
    }

    RSModel take(SceneBaselineState.Record record) {
        if (candidates == null) return null;
        RSModel model = candidates.remove(new Key(record.x, record.y, record.id, record.direction));
        if (model != null) reused++;
        return model;
    }

    void remember(mudclient mc, int id, RSModel model, boolean wasClone) {
        if (candidates == null || model == null || !mc.isBrowserBaselineStaticObject(id)) return;
        if (wasClone) cloned++;
        Template template = templates.get(id);
        GameObjectDef def = EntityHandler.getObjectDef(id);
        RSModel source = def == null || def.modelID < 0 ? null : mc.getModelCacheItem(def.modelID);
        if (source == null) return;
        if (template == null || verifiedTemplates.get(id) != template || !template.matchesDefinition(def, source)
            || !template.proof.matchesModelGeometry(model)) {
            long allowance = MAX_BYTES - bytes - MAX_OBJECTS * 512L;
            if (templates.size() >= MAX_TEMPLATES || allowance <= 0) { refused++; return; }
            RSModel.BrowserBaselineProof proof = RSModel.browserBaselineProof(source, model, allowance);
            if (proof == null) { refused++; return; }
            if (template != null) bytes -= template.proof.retainedBytes();
            template = new Template(def, source, proof); templates.put(id, template); bytes += proof.retainedBytes();
            verifiedTemplates.put(id, template);
        }
        usedTemplates.add(id); peakBytes = Math.max(peakBytes, bytes + MAX_OBJECTS * 512L);
    }

    void finish() {
        if (usedTemplates != null) {
            Iterator<Map.Entry<Integer, Template>> it = templates.entrySet().iterator();
            while (it.hasNext()) { Map.Entry<Integer, Template> entry = it.next();
                if (!usedTemplates.contains(entry.getKey())) { bytes -= entry.getValue().proof.retainedBytes(); it.remove(); }
            }
        }
        candidates = null; usedTemplates = null; verifiedTemplates = null;
    }

    private static final class Template {
        final RSModel source; final RSModel.BrowserBaselineProof proof;
        final int modelId, width, height, type; final boolean interaction;
        final String name, modelName, command;
        Template(GameObjectDef d, RSModel source, RSModel.BrowserBaselineProof proof) {
            this.source = source; this.proof = proof; modelId = d.modelID; width = d.getWidth();
            height = d.getHeight(); type = d.getType(); interaction = d.isInteractionOnly();
            name = d.getName(); modelName = d.getObjectModel(); command = d.getCommand1();
        }
        boolean matchesTemplate(GameObjectDef d, RSModel source) {
            return matchesDefinition(d, source) && proof.matchesSource(source);
        }
        boolean matchesDefinition(GameObjectDef d, RSModel source) {
            return d != null && this.source == source && modelId == d.modelID && width == d.getWidth()
                && height == d.getHeight() && type == d.getType() && interaction == d.isInteractionOnly()
                && Objects.equals(name, d.getName()) && Objects.equals(modelName, d.getObjectModel())
                && Objects.equals(command, d.getCommand1());
        }

    }
    private static final class Key {
        final int x, z, id, direction;
        Key(int x, int z, int id, int direction) { this.x=x; this.z=z; this.id=id; this.direction=direction; }
        @Override public int hashCode() { return (((x * 31) + z) * 31 + id) * 31 + direction; }
        @Override public boolean equals(Object o) { if (!(o instanceof Key)) return false; Key k=(Key)o;
            return x==k.x && z==k.z && id==k.id && direction==k.direction; }
    }
}
