package com.platform.system.auth.dto;

import java.util.List;

public record UserInfoResponse(
        String userId,
        String username,
        String userNo,
        String email,
        String displayName,
        String tenantId,
        String deptId,
        // Role IDs — the stable key for frontend role-based checks (survives
        // admin renames). roleNames is the human-facing display, in the same
        // order. Both come from the same RBAC lookup.
        List<String> roles,
        List<String> roleNames,
        List<String> authorities) {}
