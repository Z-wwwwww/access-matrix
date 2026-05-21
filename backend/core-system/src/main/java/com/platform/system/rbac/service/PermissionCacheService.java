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
    private static final String CACHE_SCOPE = "userDataScope";
    private static final String CACHE_DEPT_TREE = "deptTree";
    private static final String CACHE_DEPT_SUB  = "deptSubtree";

    private final CacheManager cacheManager;
    private final UserRoleMapper userRoleMapper;

    public PermissionCacheService(CacheManager cacheManager, UserRoleMapper userRoleMapper) {
        this.cacheManager = cacheManager;
        this.userRoleMapper = userRoleMapper;
    }

    public void evictUser(String userId) {
        evictKey(CACHE_PERMS, userId);
        evictKey(CACHE_MENU, userId);
        evictKey(CACHE_SCOPE, userId);
        log.debug("Evicted userPermissions + userMenu + userDataScope for user {}", userId);
    }

    public void evictRole(String roleId) {
        List<String> userIds = userRoleMapper.findUserIdsByRoleId(roleId);
        Cache perms = cacheManager.getCache(CACHE_PERMS);
        Cache menu  = cacheManager.getCache(CACHE_MENU);
        Cache scope = cacheManager.getCache(CACHE_SCOPE);
        for (String uid : userIds) {
            if (perms != null) perms.evict(uid);
            if (menu  != null) menu.evict(uid);
            if (scope != null) scope.evict(uid);
        }
        log.debug("Evicted perm + menu + scope caches for {} users impacted by role {}", userIds.size(), roleId);
    }

    /** Dept tree change → evict whole-tenant tree, all subtrees, and every user's data-scope. */
    public void evictAllDepts() {
        Cache tree = cacheManager.getCache(CACHE_DEPT_TREE);
        Cache sub  = cacheManager.getCache(CACHE_DEPT_SUB);
        Cache scope = cacheManager.getCache(CACHE_SCOPE);
        if (tree != null)  tree.clear();
        if (sub != null)   sub.clear();
        if (scope != null) scope.clear();
        log.debug("Cleared deptTree + deptSubtree + userDataScope caches");
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
        for (String name : new String[]{CACHE_PERMS, CACHE_MENU, CACHE_SCOPE, CACHE_DEPT_TREE, CACHE_DEPT_SUB}) {
            Cache c = cacheManager.getCache(name);
            if (c != null) c.clear();
        }
        log.debug("Cleared all RBAC caches");
    }

    private void evictKey(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) cache.evict(key);
    }
}
