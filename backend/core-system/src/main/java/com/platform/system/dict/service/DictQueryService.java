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

    /**
     * Is {@code value} an option a client may still CHOOSE — i.e. a live item that
     * is also {@code enabled} (status=1)?
     *
     * <p>Distinct from {@link #isValidValue}, which answers the weaker "is this a
     * legal value of this dict at all" and must keep saying yes for retired values
     * so historical rows still resolve a label. The two questions need different
     * filters and both are needed:
     *
     * <ul>
     *   <li>{@code DictAdminService.deleteItem} refuses to hard-delete any value the
     *       code branches on or that business data still references, and its own
     *       comment directs the operator to "disable it (status=0) instead" — so
     *       {@code status=0} is THE retirement mechanism for a managed dict item.</li>
     *   <li>The read API ships an {@code enabled} flag per item precisely so the two
     *       uses can diverge, and {@code useDict} does diverge: {@code items} (all,
     *       for labels) vs {@code options} (enabled only, for {@code <Select>}).</li>
     * </ul>
     *
     * Without this method the server had no enabled-aware validator at all, so
     * retiring an option only hid it in fresh UI — any client (including a browser
     * tab holding a pre-disable dict cache) could still write the retired value.
     */
    public boolean isSelectableValue(String code, Object value) {
        if (value == null) return false;
        String v = String.valueOf(value);
        return read(code).items().stream()
                .anyMatch(i -> String.valueOf(i.value()).equals(v) && i.enabled());
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
