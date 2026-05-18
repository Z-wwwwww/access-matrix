package com.platform.core.common.id;

import com.github.f4b6a3.ulid.UlidCreator;

public final class IdGenerator {

    private IdGenerator() {}

    /**
     * Monotonic ULID — 26-char Crockford Base32, time-ordered for B+Tree locality.
     */
    public static String ulid() {
        return UlidCreator.getMonotonicUlid().toString();
    }
}
