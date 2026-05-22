package com.platform.system.rbac.dto;

/**
 * 権限字典の DTO。Create / Update リクエストは常量法導入後に削除した
 * （字典は {@code PermissionConsistencyGuard} がコード常量から自動 upsert する）。
 */
public final class PermissionDto {

    private PermissionDto() {}

    public record View(
            String id,
            String code,
            String name,
            String resource,
            String action,
            String module,
            String description,
            Integer isBuiltIn) {}
}
