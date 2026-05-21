package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.result.PageResult;
import com.platform.system.rbac.dto.PermissionDto;
import com.platform.system.rbac.entity.PermissionEntity;
import com.platform.system.rbac.mapper.PermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class PermissionAdminService {

    private final PermissionMapper mapper;
    private final PermissionCacheService cacheService;

    public PermissionAdminService(PermissionMapper mapper, PermissionCacheService cacheService) {
        this.mapper = mapper;
        this.cacheService = cacheService;
    }

    public PageResult<PermissionDto.View> list(long page, long size, String keyword, String module) {
        Page<PermissionEntity> p = new Page<>(page, size);
        QueryWrapper<PermissionEntity> w = new QueryWrapper<PermissionEntity>().eq("mark", 1).orderByAsc("module", "code");
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("code", keyword).or().like("name", keyword));
        }
        if (module != null && !module.isBlank()) {
            w.eq("module", module);
        }
        Page<PermissionEntity> result = mapper.selectPage(p, w);
        List<PermissionDto.View> records = result.getRecords().stream().map(this::toView).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    /** Group all permissions by module, intended for the role-config UI. */
    public Map<String, List<PermissionDto.View>> listByModule() {
        return mapper.selectList(new QueryWrapper<PermissionEntity>().eq("mark", 1).orderByAsc("module", "code"))
                .stream()
                .map(this::toView)
                .collect(Collectors.groupingBy(
                        v -> v.module() == null ? "" : v.module(),
                        TreeMap::new,
                        Collectors.toList()));
    }

    @Transactional
    public String create(PermissionDto.CreateRequest req) {
        Long dup = mapper.selectCount(new QueryWrapper<PermissionEntity>().eq("mark", 1).eq("code", req.code()));
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Permission code already exists: " + req.code());
        }
        PermissionEntity e = new PermissionEntity();
        e.setId(IdGenerator.ulid());
        e.setCode(req.code());
        e.setName(req.name());
        e.setResource(req.resource());
        e.setAction(req.action());
        e.setModule(req.module());
        e.setDescription(req.description());
        e.setIsBuiltIn(0);
        mapper.insert(e);
        return e.getId();
    }

    @Transactional
    public void update(String id, PermissionDto.UpdateRequest req) {
        PermissionEntity e = require(id);
        assertNotBuiltIn(e, "update");
        if (req.name() != null) e.setName(req.name());
        if (req.module() != null) e.setModule(req.module());
        if (req.description() != null) e.setDescription(req.description());
        mapper.updateById(e);
        // Permission rename affects every role with this permission → conservative full invalidation.
        cacheService.evictAll();
    }

    @Transactional
    public void delete(String id) {
        PermissionEntity e = require(id);
        assertNotBuiltIn(e, "delete");
        e.setMark(0);
        mapper.updateById(e);
        cacheService.evictAll();
    }

    /**
     * Built-in permissions (is_built_in=1) are platform-seeded and referenced by application
     * code via {@code @RequiresPermission} string literals. Mutating them at runtime would
     * silently desync the AOP guard. Lock all writes — including cosmetic edits — for safety.
     */
    private void assertNotBuiltIn(PermissionEntity e, String op) {
        if (Integer.valueOf(1).equals(e.getIsBuiltIn())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Built-in permission " + e.getCode() + " is read-only (rejected: " + op + ")");
        }
    }

    private PermissionEntity require(String id) {
        PermissionEntity e = mapper.selectById(id);
        if (e == null || e.getMark() == null || e.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Permission not found: " + id);
        }
        return e;
    }

    private PermissionDto.View toView(PermissionEntity e) {
        return new PermissionDto.View(
                e.getId(), e.getCode(), e.getName(),
                e.getResource(), e.getAction(), e.getModule(),
                e.getDescription(), e.getIsBuiltIn());
    }
}
