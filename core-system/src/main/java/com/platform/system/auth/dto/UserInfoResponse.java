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
        List<String> roles,
        List<String> authorities) {}
