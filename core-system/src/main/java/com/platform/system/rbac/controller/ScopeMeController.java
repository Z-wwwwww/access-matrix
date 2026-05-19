package com.platform.system.rbac.controller;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.infrastructure.security.rbac.DataScopeDecision;
import com.platform.core.infrastructure.security.rbac.DataScopeResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Debug-grade endpoint that returns the {@link DataScopeDecision} the
 * framework computed for the current request — extremely useful while
 * configuring roles + departments to verify "what would this user see?".
 *
 * <p>Open to any authenticated caller (no @RequiresPermission); the response
 * only reveals decisions about <em>oneself</em>.
 */
@RestController
@RequestMapping("/scope")
public class ScopeMeController {

    private final DataScopeResolver dataScopeResolver;

    public ScopeMeController(DataScopeResolver dataScopeResolver) {
        this.dataScopeResolver = dataScopeResolver;
    }

    @GetMapping("/me")
    public JsonResult<DataScopeDecision> me() {
        DataScopeDecision decision = dataScopeResolver.currentDecision();
        if (decision == null || decision.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return JsonResult.ok(decision);
    }
}
