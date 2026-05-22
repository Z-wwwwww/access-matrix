package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.result.PageResult;
import com.platform.system.rbac.dto.PermissionDto;
import com.platform.system.rbac.entity.PermissionEntity;
import com.platform.system.rbac.mapper.PermissionMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 権限字典の読み取り専用クエリ。
 *
 * <p>常量法導入後、字典の実体は {@code PermissionConsistencyGuard} が起動時に
 * {@code PermissionRegistry}（コード由来）から upsert する。
 * {@code create / update / delete} はもう存在しない（ユーザーが書き換える経路がない）。
 */
@Service
public class PermissionAdminService {

    private final PermissionMapper mapper;

    public PermissionAdminService(PermissionMapper mapper) {
        this.mapper = mapper;
    }

    public PageResult<PermissionDto.View> list(long page, long size, String keyword, String module) {
        Page<PermissionEntity> p = new Page<>(page, size);
        QueryWrapper<PermissionEntity> w = new QueryWrapper<PermissionEntity>()
                .eq("mark", 1).orderByAsc("module", "code");
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

    /** Role 編集の権限ピッカー用：module でグルーピング。 */
    public Map<String, List<PermissionDto.View>> listByModule() {
        return mapper.selectList(new QueryWrapper<PermissionEntity>().eq("mark", 1).orderByAsc("module", "code"))
                .stream()
                .map(this::toView)
                .collect(Collectors.groupingBy(
                        v -> v.module() == null ? "" : v.module(),
                        TreeMap::new,
                        Collectors.toList()));
    }

    private PermissionDto.View toView(PermissionEntity e) {
        return new PermissionDto.View(
                e.getId(), e.getCode(), e.getName(),
                e.getResource(), e.getAction(), e.getModule(),
                e.getDescription(), e.getIsBuiltIn());
    }
}
