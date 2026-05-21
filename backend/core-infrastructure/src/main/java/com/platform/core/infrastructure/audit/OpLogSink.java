package com.platform.core.infrastructure.audit;

/**
 * Persists audit log rows. Defined here so {@code OpLogAspect} doesn't have
 * to import the {@code core-system} OpLog mapper directly — mirrors the
 * {@code UserPermissionsLookup} / {@code UserDataScopeLookup} pattern.
 *
 * <p>Implementations are expected to write asynchronously.
 */
public interface OpLogSink {
    void record(OpLogRecord record);
}
