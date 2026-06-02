package com.platform.core.infrastructure.event;

/**
 * Who caused a domain event. Persisted as {@code core_domain_event.actor_type}
 * so analytics / AI can separate human-driven changes from machine-driven ones
 * (e.g. "what fraction of price changes were AI autopilot vs. a revenue manager").
 */
public enum ActorType {

    /** A change driven by the current authenticated end user (the common case). */
    HUMAN(1),

    /** A change driven by an AI service account (recommendation accepted / autopilot). */
    AI(2),

    /** A change driven by an automated/background process with no human or AI actor. */
    SYSTEM(3);

    private final int code;

    ActorType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
