package dev.fireball.api.event;

/**
 * Fired during mod lifecycle phases.
 */
public class ModLifecycleEvent implements Event {
    public enum Phase {
        PRE_INIT,
        INIT,
        POST_INIT,
        LOAD_COMPLETE
    }

    private final Phase phase;
    private final String modId;

    public ModLifecycleEvent(Phase phase, String modId) {
        this.phase = phase;
        this.modId = modId;
    }

    public Phase getPhase() {
        return phase;
    }

    public String getModId() {
        return modId;
    }
}
