package com.platform.system.rbac.service;

import com.platform.system.rbac.mapper.UserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Invalidates {@code userPermissions} cache entries after RBAC writes.
 *
 * <p>Called by:
 * <ul>
 *   <li>user-role link changes → {@link #evictUser(String)}</li>
 *   <li>role-permission link changes (or role status flip) → {@link #evictRole(String)}
 *       which fans out to every user that holds the role</li>
 *   <li>permission rename / delete → {@link #evictAll()}</li>
 * </ul>
 *
 * <p>Stage 1 covers Caffeine only. Redis L2 + Pub/Sub fan-out is added in Stage 4.
 */
@Service
public class PermissionCacheService {

    private static final Logger log = LoggerFactory.getLogger(PermissionCacheService.class);
    private static final String CACHE_NAME = "userPermissions";

    private final CacheManager cacheManager;
    private final UserRoleMapper userRoleMapper;

    public PermissionCacheService(CacheManager cacheManager, UserRoleMapper userRoleMapper) {
        this.cacheManager = cacheManager;
        this.userRoleMapper = userRoleMapper;
    }

    public void evictUser(String userId) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(userId);
            log.debug("Evicted userPermissions for user {}", userId);
        }
    }

    public void evictRole(String roleId) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) return;
        List<String> userIds = userRoleMapper.findUserIdsByRoleId(roleId);
        userIds.forEach(cache::evict);
        log.debug("Evicted userPermissions for {} users impacted by role {}", userIds.size(), roleId);
    }

    public void evictAll() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.clear();
            log.debug("Cleared userPermissions cache entirely");
        }
    }
}
