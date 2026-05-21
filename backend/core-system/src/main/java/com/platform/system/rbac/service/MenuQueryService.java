package com.platform.system.rbac.service;

import com.platform.core.common.security.PermissionMatcher;
import com.platform.system.rbac.dto.MenuNode;
import com.platform.system.rbac.entity.MenuEntity;
import com.platform.system.rbac.mapper.MenuMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

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
 * <p>Algorithm:
 * <ol>
 *   <li>If the user holds {@code *:*} we return the full visible menu tree
 *       (every {@code mark=1, status=1} entry). Cheaper than maintaining
 *       a {@code role_menu} link to every menu, and admins should auto-see
 *       new menus the moment they are created.</li>
 *   <li>Otherwise we collect the user's directly-authorised menus via
 *       {@code core_rbac_role_menu}, then back-fill the parent chain so the
 *       tree never has orphan branches. Finally we assemble parent_id-keyed
 *       buckets into a tree, sorted by {@code sort_order}.</li>
 * </ol>
 */
@Service
public class MenuQueryService {

    private final MenuMapper menuMapper;
    private final PermissionQueryService permissionQueryService;

    public MenuQueryService(MenuMapper menuMapper, PermissionQueryService permissionQueryService) {
        this.menuMapper = menuMapper;
        this.permissionQueryService = permissionQueryService;
    }

    @Cacheable(value = "userMenu", key = "#userId", unless = "#result.isEmpty()")
    public List<MenuNode> loadUserMenuTree(String userId) {
        if (userId == null || userId.isBlank()) return List.of();

        Set<String> perms = permissionQueryService.loadUserPermissions(userId);
        List<MenuEntity> flat;
        if (perms.contains(PermissionMatcher.SUPER)) {
            flat = menuMapper.findAllVisible();
        } else {
            List<MenuEntity> direct = menuMapper.findMenusByUserId(userId);
            if (direct.isEmpty()) return List.of();
            // Two-pass filter: drop leaf menus whose permission_code the user
            // does not hold, even though a role granted them the menu link.
            // (e.g. role X has the "Orders" menu, but only includes order:read,
            // so any "Orders > Delete" leaf with permission_code=order:delete
            // is hidden.) Containers (no permission_code) survive because the
            // tree-assembler still needs them to host the leaves the user can see.
            List<MenuEntity> permitted = filterByPermissionCode(direct, perms);
            if (permitted.isEmpty()) return List.of();
            flat = withAncestors(permitted);
        }
        return assembleTree(flat);
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

    private MenuNode toNode(MenuEntity m) {
        MenuNode n = new MenuNode();
        n.setId(m.getId());
        n.setCode(m.getCode());
        n.setTitle(m.getTitle());
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
}
