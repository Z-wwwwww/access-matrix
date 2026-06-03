package com.platform.system.rbac.service;

import com.platform.core.common.security.PermissionMatcher;
import com.platform.system.rbac.dto.MenuNode;
import com.platform.system.rbac.entity.MenuEntity;
import com.platform.system.rbac.mapper.MenuMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the menu tree visible to a given user.
 *
 * <p>Menus are a single GLOBAL set (V41) — one shared navigation tree for all
 * tenants — and visibility is decided per user by {@code permission_code}.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>If the user holds either super-wildcard — {@code *:*} (platform super)
 *       or {@code tenant:*} (business-tenant super) — start from the full global
 *       menu set ({@code findAllVisible()}).</li>
 *   <li>Otherwise collect the user's directly-authorised menus via
 *       {@code core_rbac_role_menu} (role joins stay tenant-scoped).</li>
 *   <li><b>Both branches</b> then filter by {@code permission_code}: a
 *       {@code *:*} platform admin keeps only {@code platform:*} menus, a
 *       {@code tenant:*} business super keeps only business menus — the two
 *       wildcards are namespace-disjoint ({@link PermissionMatcher}), so this
 *       cleanly separates the platform console from business navigation.</li>
 *   <li>Back-fill parent chains, assemble the tree, then prune directory nodes
 *       left empty after filtering (so the "other" admin never sees an empty
 *       container).</li>
 * </ol>
 */
@Service
public class MenuQueryService {

    private static final TypeReference<Map<String, String>> I18N_MAP = new TypeReference<>() {};

    private final MenuMapper menuMapper;
    private final PermissionQueryService permissionQueryService;
    private final JsonMapper jsonMapper;

    public MenuQueryService(MenuMapper menuMapper, PermissionQueryService permissionQueryService,
                            JsonMapper jsonMapper) {
        this.menuMapper = menuMapper;
        this.permissionQueryService = permissionQueryService;
        this.jsonMapper = jsonMapper;
    }

    @Cacheable(value = "userMenu", key = "#userId", unless = "#result.isEmpty()")
    public List<MenuNode> loadUserMenuTree(String userId) {
        if (userId == null || userId.isBlank()) return List.of();

        // Tenant from RequestContext (post-auth JWT tid) — drives the per-tenant
        // role joins below. Menus themselves are global (no tenant scope).
        String tenantId = com.platform.core.common.context.RequestContext.tenantIdOrDefault();
        Set<String> perms = permissionQueryService.loadUserPermissions(userId);

        // Source set: super-wildcards start from the whole global menu set;
        // everyone else from their role_menu-granted menus (tenant-scoped joins).
        List<MenuEntity> source;
        if (perms.contains(PermissionMatcher.SUPER)
                || perms.contains(PermissionMatcher.TENANT_SUPER)) {
            source = menuMapper.findAllVisible();
        } else {
            source = menuMapper.findMenusByUserId(userId, tenantId);
        }
        if (source.isEmpty()) return List.of();

        // Filter by permission_code for ALL callers. Leaves whose code the user
        // does not hold are dropped; containers (no permission_code) survive here
        // and are pruned later if they end up childless. This is what keeps a
        // *:* platform admin from seeing business menus and a tenant:* business
        // super from seeing the platform console (namespace-disjoint wildcards).
        List<MenuEntity> permitted = filterByPermissionCode(source, perms);
        if (permitted.isEmpty()) return List.of();

        List<MenuEntity> flat = withAncestors(permitted);
        List<MenuNode> tree = assembleTree(flat);
        pruneEmptyDirectories(tree);
        return tree;
    }

    /**
     * Keep every menu without a {@code permission_code} (containers / category
     * roots) and every leaf whose code wildcard-matches the caller's set.
     * Parent re-hydration is left to {@link #withAncestors(List)} so a
     * permitted leaf never becomes orphaned.
     */
    private List<MenuEntity> filterByPermissionCode(List<MenuEntity> menus, Set<String> perms) {
        return menus.stream()
                .filter(m -> {
                    String code = m.getPermissionCode();
                    if (code == null || code.isBlank()) return true;
                    return PermissionMatcher.matchesAny(perms, new String[]{code});
                })
                .collect(Collectors.toList());
    }

    /** Make sure every node's parent chain is included so the tree assembler does not drop branches. */
    private List<MenuEntity> withAncestors(List<MenuEntity> direct) {
        Map<String, MenuEntity> byId = new HashMap<>();
        Set<String> needed = new HashSet<>();
        for (MenuEntity m : direct) {
            byId.put(m.getId(), m);
            String pid = m.getParentId();
            if (pid != null && !pid.isBlank() && !byId.containsKey(pid)) {
                needed.add(pid);
            }
        }
        // Walk up the parent chain; one fetch per generation, until we converge.
        while (!needed.isEmpty()) {
            List<MenuEntity> fetched = menuMapper.findByIdIn(new ArrayList<>(needed));
            needed.clear();
            for (MenuEntity m : fetched) {
                if (byId.put(m.getId(), m) == null) {
                    String pid = m.getParentId();
                    if (pid != null && !pid.isBlank() && !byId.containsKey(pid)) {
                        needed.add(pid);
                    }
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    private List<MenuNode> assembleTree(List<MenuEntity> flat) {
        // Group children by their parent_id (null/blank = root).
        Map<String, List<MenuEntity>> byParent = new HashMap<>();
        for (MenuEntity m : flat) {
            String key = (m.getParentId() == null || m.getParentId().isBlank()) ? "" : m.getParentId();
            byParent.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }
        byParent.values().forEach(list -> list.sort(
                Comparator.comparing(MenuEntity::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                          .thenComparing(MenuEntity::getCode)));

        Map<String, MenuNode> nodes = new LinkedHashMap<>();
        for (MenuEntity m : flat) {
            nodes.put(m.getId(), toNode(m));
        }
        List<MenuNode> roots = new ArrayList<>();
        for (MenuEntity m : flat) {
            MenuNode node = nodes.get(m.getId());
            String pid = m.getParentId();
            if (pid == null || pid.isBlank()) {
                roots.add(node);
            } else {
                MenuNode parent = nodes.get(pid);
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    // Parent missing (e.g. status=0) — promote to root so the user still sees it.
                    roots.add(node);
                }
            }
        }
        roots.sort(Comparator.comparing((MenuNode n) -> n.getSortOrder() == null ? Integer.MAX_VALUE : n.getSortOrder())
                              .thenComparing(MenuNode::getCode));
        for (MenuNode n : nodes.values()) {
            n.getChildren().sort(Comparator.comparing((MenuNode c) -> c.getSortOrder() == null ? Integer.MAX_VALUE : c.getSortOrder())
                                            .thenComparing(MenuNode::getCode));
        }
        return roots;
    }

    /**
     * Recursively drop directory nodes ({@code menuType == 1}) left with no
     * children after permission filtering — e.g. the "Platform" container for a
     * business super admin, or the "System" container for a platform admin.
     * Leaf/page nodes ({@code menuType != 1}) are never pruned.
     */
    private void pruneEmptyDirectories(List<MenuNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return;
        nodes.removeIf(n -> {
            pruneEmptyDirectories(n.getChildren());
            boolean isDirectory = n.getMenuType() != null && n.getMenuType() == 1;
            boolean hasNoChildren = n.getChildren() == null || n.getChildren().isEmpty();
            return isDirectory && hasNoChildren;
        });
    }

    private MenuNode toNode(MenuEntity m) {
        MenuNode n = new MenuNode();
        n.setId(m.getId());
        n.setCode(m.getCode());
        n.setTitle(m.getTitle());
        n.setTitleI18n(parseI18n(m.getTitleI18n()));
        n.setMenuType(m.getMenuType());
        n.setPath(m.getPath());
        n.setComponent(m.getComponent());
        n.setIcon(m.getIcon());
        n.setSortOrder(m.getSortOrder());
        n.setHide(m.getHide());
        n.setHideFooter(m.getHideFooter());
        n.setHideSidebar(m.getHideSidebar());
        n.setPinned(m.getPinned());
        n.setTabUnique(m.getTabUnique());
        n.setRedirect(m.getRedirect());
        n.setPermissionCode(m.getPermissionCode());
        return n;
    }

    /**
     * Parse the raw {@code title_i18n} JSON column into a Map. Null/blank → null
     * so {@code @JsonInclude(NON_NULL)} on {@link MenuNode} keeps the wire payload tight.
     * Malformed JSON degrades silently; the frontend falls back to {@code title}.
     */
    private Map<String, String> parseI18n(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return jsonMapper.readValue(raw, I18N_MAP);
        } catch (Exception e) {
            return null;
        }
    }
}
