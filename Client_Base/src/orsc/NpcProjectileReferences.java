package orsc;

/** Discard NPC-origin projectile visuals when that registry lifetime leaves view. */
public final class NpcProjectileReferences {
    private NpcProjectileReferences() {}

    public static void clear(ORSCharacter[] actors, int count, int removedNpcIndex) {
        for (int i = 0; i < count; i++) {
            ORSCharacter actor = actors[i];
            if (actor != null && actor.projectileRange > 0
                    && actor.attackingNpcServerIndex == removedNpcIndex) {
                actor.projectileRange = 0;
                actor.attackingNpcServerIndex = -1;
            }
        }
    }
}
