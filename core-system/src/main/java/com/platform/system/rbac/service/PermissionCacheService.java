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
    private static final String CACHE_PERMS = "userPermissions";
    private static final String CACHE_MENU  = "userMenu";

    private final CacheManager cacheManager;
    private final UserRoleMapper userRoleMapper;

    public PermissionCacheService(CacheManager cacheManager, UserRoleMapper userRoleMapper) {
        this.cacheManager = cacheManager;
        this.userRoleMapper = userRoleMapper;
    }

    public void evictUser(String userId) {
        evictKey(CACHE_PERMS, userId);
        evictKey(CACHE_MENU, userId);
        log.debug("Evicted userPermissions + userMenu for user {}", userId);
    }

    public void evictRole(String roleId) {
        List<String> userIds = userRoleMapper.findUserIdsByRoleId(roleId);
        Cache perms = cacheManager.getCache(CACHE_PERMS);
        Cache menu  = cacheManager.getCache(CACHE_MENU);
        for (String uid : userIds) {
            if (perms != null) perms.evict(uid);
            if (menu  != null) menu.evict(uid);
        }
        log.debug("Evicted userPermissions + userMenu for {} users impacted by role {}", userIds.size(), roleId);
    }

    /** Menu changes affect every user that holds any role linked to the menu. Used by Stage 4 menu CRUD. */
    public void evictAllMenus() {
        Cache cache = cacheManager.getCache(CACHE_MENU);
        if (cache != null) {
            cache.clear();
            log.debug("Cleared userMenu cache entirely");
        }
    }

    public void evictAll() {
        Cache perms = cacheManager.getCache(CACHE_PERMS);
        Cache menu  = cacheManager.getCache(CACHE_MENU);
        if (perms != null) perms.clear();
        if (menu  != null) menu.clear();
        log.debug("Cleared userPermissions + userMenu cache entirely");
    }

    private void evictKey(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) cache.evict(key);
    }
}
