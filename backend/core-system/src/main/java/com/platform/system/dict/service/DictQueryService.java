package com.platform.system.dict.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.platform.core.common.dict.DictRegistry;
import com.platform.system.dict.dto.DictReadDto;
import com.platform.system.dict.entity.DictItemEntity;
import com.platform.system.dict.mapper.DictItemMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read side of the dictionary feature ({@code GET /dict/{code}}). Resolves a code
 * to its options from one of two sources, in order:
 * <ol>
 *   <li><b>built-in</b> — a {@link DictRegistry} enum (status/state/type; code branches on it)</li>
 *   <li><b>managed</b> — the {@code core_dict_item} table (runtime-editable business lookups)</li>
 * </ol>
 *
 * <p>Results are cached in-process and invalidated by {@link DictAdminService} on
 * write (only managed codes ever change; built-in codes are immutable). The
 * frontend {@code dictStore} caches per session on top of this, so the DB is hit
 * rarely. Unknown codes return an empty item list rather than 404 — the read path
 * is deliberately delete-tolerant.
 */
@Service
public class DictQueryService {

    private final DictItemMapper itemMapper;
    private final DictJsonCodec codec;
    private final Map<String, DictReadDto.View> cache = new ConcurrentHashMap<>();

    public DictQueryService(DictItemMapper itemMapper, DictJsonCodec codec) {
        this.itemMapper = itemMapper;
        this.codec = codec;
    }

    public DictReadDto.View read(String code) {
        return cache.computeIfAbsent(code, this::load);
    }

    /**
     * True if {@code value} is a current item of dict {@code code} (built-in or
     * managed). Used for form-3 input validation against the OPEN set — unlike
     * {@code DictEnum.requireValid} (closed enum), this allows ops-added values.
     */
    public boolean isValidValue(String code, Object value) {
        if (value == null) return false;
        String v = String.valueOf(value);
        return read(code).items().stream()
                .anyMatch(i -> String.valueOf(i.value()).equals(v));
    }

    void evict(String code) {
        cache.remove(code);
    }

    private DictReadDto.View load(String code) {
        if (DictRegistry.isBuiltIn(code)) {
            List<DictReadDto.ItemView> items = new java.util.ArrayList<>();
            int sort = 0;
            for (DictRegistry.Item it : DictRegistry.items(code)) {
                items.add(new DictReadDto.ItemView(it.value(), it.labelKey(), null, it.cssClass(), sort++, true));
            }
            return new DictReadDto.View(code, true, List.copyOf(items));
        }
        List<DictItemEntity> rows = itemMapper.selectList(new QueryWrapper<DictItemEntity>()
                .eq("mark", 1)
                .eq("dict_code", code)
                .orderByAsc("sort_no", "item_value"));
        List<DictReadDto.ItemView> items = rows.stream()
                .map(r -> new DictReadDto.ItemView(
                        r.getItemValue(),
                        null,
                        codec.parse(r.getLabelI18n()),
                        r.getCssClass(),
                        r.getSortNo(),
                        r.getStatus() != null && r.getStatus() == 1))
                .toList();
        return new DictReadDto.View(code, false, items);
    }
}
