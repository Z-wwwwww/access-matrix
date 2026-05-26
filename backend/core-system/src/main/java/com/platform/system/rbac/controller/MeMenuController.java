package com.platform.system.rbac.controller;

import com.platform.system.rbac.dto.MenuNode;
import com.platform.system.rbac.service.MenuQueryService;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/menu")
public class MeMenuController {

    private final MenuQueryService menuQueryService;

    public MeMenuController(MenuQueryService menuQueryService) {
        this.menuQueryService = menuQueryService;
    }

    @GetMapping("/me")
    public JsonResult<List<MenuNode>> me() {
        // RequestContext.userId is set by CoreRequestContextFilter — it is the
        // BUSINESS ULID, not the JWT subject. In OIDC mode the JWT subject is
        // the Keycloak UUID, which doesn't exist in core_auth_user.id (we link
        // it via keycloak_id instead). Reading the JWT directly here used to
        // return an empty menu for every OIDC user including admin.
        String userId = RequestContext.userId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return JsonResult.ok(menuQueryService.loadUserMenuTree(userId));
    }
}
