package com.platform.system.rbac.service;

import com.platform.core.common.error.BusinessException;
import com.platform.system.rbac.dto.MenuAdminDto;
import com.platform.system.rbac.entity.MenuEntity;
import com.platform.system.rbac.mapper.MenuMapper;
import com.platform.system.rbac.mapper.RoleMenuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the menu tree against cycles.
 *
 * <p>Why this matters more than the department equivalent: menus are a single
 * GLOBAL set (V41), and {@code MenuQueryService.assembleTree} attaches each node
 * to its parent while collecting only parent-less nodes as roots. A node inside a
 * cycle therefore hangs off nothing reachable from a root — the whole branch
 * silently vanishes from {@code /menu/me} for EVERY user of the installation, and
 * the API returns 200 with no hint of why. The admin UI already excludes self +
 * descendants from the parent picker; these tests pin the SERVER-side guard, which
 * is the actual boundary (`PUT /admin/menu/{id}` via curl bypasses the picker).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MenuAdminServiceReparentTest {

    @Mock MenuMapper menuMapper;
    @Mock RoleMenuMapper roleMenuMapper;
    @Mock PermissionCacheService cacheService;

    private MenuAdminService service;

    /** id -> row, so selectById walks the same tree the assertions describe. */
    private final Map<String, MenuEntity> tree = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new MenuAdminService(menuMapper, roleMenuMapper, cacheService, JsonMapper.builder().build());
        when(menuMapper.selectById(any())).thenAnswer(inv -> tree.get(inv.getArgument(0).toString()));
        // updateById now goes through ConcurrentEdit.requireApplied(...): 0 affected rows
        // means a concurrent editor advanced the @Version column. Mockito defaults an int
        // return to 0, so every mocked update would look like a lost update.
        org.mockito.Mockito.lenient()
                .when(menuMapper.updateById(org.mockito.ArgumentMatchers.any(MenuEntity.class)))
                .thenReturn(1);
    }

    /** root → mid → leaf */
    private void seedChain() {
        tree.put("root", menu("root", null));
        tree.put("mid", menu("mid", "root"));
        tree.put("leaf", menu("leaf", "mid"));
    }

    private static MenuEntity menu(String id, String parentId) {
        MenuEntity m = new MenuEntity();
        m.setId(id);
        m.setParentId(parentId);
        m.setMark(1);
        m.setCode(id);
        m.setMenuType(2);
        return m;
    }

    private static MenuAdminDto.UpdateRequest reparent(String newParentId) {
        // Only parentId is set; every other field stays null so update() leaves it alone.
        return new MenuAdminDto.UpdateRequest(newParentId, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    @Test
    void update_refusesSelfAsParent() {
        seedChain();

        assertThatThrownBy(() -> service.update("mid", reparent("mid")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("its own parent");

        verify(menuMapper, never()).updateById(any(MenuEntity.class));
    }

    @Test
    void update_refusesADirectDescendantAsParent() {
        seedChain();

        // mid → under leaf: leaf's ancestor chain is leaf → mid, so we hit mid.
        assertThatThrownBy(() -> service.update("mid", reparent("leaf")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("descendant");

        verify(menuMapper, never()).updateById(any(MenuEntity.class));
    }

    @Test
    void update_refusesADeepDescendantAsParent() {
        seedChain();
        tree.put("deep", menu("deep", "leaf"));   // root → mid → leaf → deep

        assertThatThrownBy(() -> service.update("root", reparent("deep")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("descendant");

        verify(menuMapper, never()).updateById(any(MenuEntity.class));
    }

    @Test
    void update_refusesANonExistentParent() {
        seedChain();

        assertThatThrownBy(() -> service.update("leaf", reparent("ghost")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Menu not found");

        verify(menuMapper, never()).updateById(any(MenuEntity.class));
    }

    @Test
    void update_allowsAValidReparent() {
        seedChain();

        // leaf → directly under root: root's chain is root (no parent), never hits leaf.
        assertThatCode(() -> service.update("leaf", reparent("root"))).doesNotThrowAnyException();

        verify(menuMapper).updateById(any(MenuEntity.class));
    }

    @Test
    void update_allowsPromotingToRoot() {
        seedChain();

        // Blank parentId is the "make it a root" payload — must stay allowed.
        assertThatCode(() -> service.update("leaf", reparent(""))).doesNotThrowAnyException();

        verify(menuMapper).updateById(any(MenuEntity.class));
    }

    @Test
    void update_terminatesEvenIfTheStoredTreeAlreadyContainsACycle() {
        // Pre-existing dirty data (a cycle written before the guard existed) must not
        // hang the walk — the visited set bounds it.
        tree.put("a", menu("a", "b"));
        tree.put("b", menu("b", "a"));
        tree.put("outside", menu("outside", null));

        assertThatCode(() -> service.update("outside", reparent("a"))).doesNotThrowAnyException();
    }

    @Test
    void create_refusesANonExistentParent() {
        seedChain();
        when(menuMapper.selectCount(any())).thenReturn(0L);

        MenuAdminDto.CreateRequest req = new MenuAdminDto.CreateRequest(
                "ghost", "new.code", "New", null, 2, "/new", null, null,
                0, 0, 0, 0, 0, null, null, null, 1);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Menu not found");

        verify(menuMapper, never()).insert(any(MenuEntity.class));
    }
}
