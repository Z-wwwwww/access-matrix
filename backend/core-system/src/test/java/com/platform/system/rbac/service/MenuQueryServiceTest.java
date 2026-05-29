package com.platform.system.rbac.service;

import com.platform.core.common.context.RequestContext;
import com.platform.system.rbac.dto.MenuNode;
import com.platform.system.rbac.entity.MenuEntity;
import com.platform.system.rbac.mapper.MenuMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the menu-visibility short-circuit for both super-wildcards.
 *
 * <p>Regression guard: a business-tenant SUPER_ADMIN holds {@code tenant:*},
 * NOT {@code *:*}. An earlier version short-circuited only on {@code *:*}, so
 * business super-admins fell through to the {@code role_menu} path with zero
 * bindings and saw an empty menu (no nav, no buttons). Both wildcards must
 * resolve to "see every visible menu in the current tenant".
 */
@ExtendWith(MockitoExtension.class)
class MenuQueryServiceTest {

    @Mock MenuMapper menuMapper;
    @Mock PermissionQueryService permissionQueryService;
    @Mock JsonMapper jsonMapper;

    MenuQueryService service;

    @BeforeEach
    void setUp() {
        service = new MenuQueryService(menuMapper, permissionQueryService, jsonMapper);
        RequestContext.set("demo", "u-1", "demo-admin", Locale.ENGLISH, "trace-1");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private MenuEntity menu(String id, String code) {
        MenuEntity m = new MenuEntity();
        m.setId(id);
        m.setCode(code);
        m.setSortOrder(0);
        return m;
    }

    @Test
    void tenantSuperWildcard_seesAllVisibleMenus_withoutRoleMenuBindings() {
        when(permissionQueryService.loadUserPermissions("u-1")).thenReturn(Set.of("tenant:*"));
        when(menuMapper.findAllVisible("demo"))
                .thenReturn(List.of(menu("m-1", "dashboard"), menu("m-2", "users")));

        List<MenuNode> tree = service.loadUserMenuTree("u-1");

        assertThat(tree).extracting(MenuNode::getCode)
                .containsExactlyInAnyOrder("dashboard", "users");
        // Must take the all-visible path, NOT the role_menu binding path.
        verify(menuMapper).findAllVisible("demo");
        verify(menuMapper, never()).findMenusByUserId(anyString(), anyString());
    }

    @Test
    void platformSuperWildcard_stillSeesAllVisibleMenus() {
        when(permissionQueryService.loadUserPermissions("u-1")).thenReturn(Set.of("*:*"));
        when(menuMapper.findAllVisible("demo")).thenReturn(List.of(menu("m-1", "dashboard")));

        List<MenuNode> tree = service.loadUserMenuTree("u-1");

        assertThat(tree).extracting(MenuNode::getCode).containsExactly("dashboard");
        verify(menuMapper).findAllVisible("demo");
        verify(menuMapper, never()).findMenusByUserId(anyString(), anyString());
    }

    @Test
    void nonSuperUser_fallsBackToRoleMenuBindings() {
        when(permissionQueryService.loadUserPermissions("u-1")).thenReturn(Set.of("user:read"));
        when(menuMapper.findMenusByUserId("u-1", "demo")).thenReturn(List.of());

        List<MenuNode> tree = service.loadUserMenuTree("u-1");

        assertThat(tree).isEmpty();
        verify(menuMapper).findMenusByUserId("u-1", "demo");
        verify(menuMapper, never()).findAllVisible(anyString());
    }
}
