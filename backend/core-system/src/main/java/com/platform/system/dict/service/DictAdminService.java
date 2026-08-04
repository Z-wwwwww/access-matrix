package com.platform.system.dict.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.dict.DictGuards;
import com.platform.core.common.dict.DictRegistry;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ConcurrentEdit;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.system.dict.dto.DictAdminDto;
import com.platform.system.dict.entity.DictEntity;
import com.platform.system.dict.entity.DictItemEntity;
import com.platform.system.dict.mapper.DictItemMapper;
import com.platform.system.dict.mapper.DictMapper;
import com.platform.system.dict.mapper.DictUsageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin CRUD for managed dictionaries. Built-in (enum) dicts are not editable here.
 *
 * <p>Lifecycle policy (see the design discussion): item {@code value} is frozen
 * once created; "删除" is normally a {@code status}=0 disable (kept resolvable for
 * history); {@code builtin=1} types are protected from item add/remove + type delete.
 */
@Service
public class DictAdminService {

    private final DictMapper dictMapper;
    private final DictItemMapper itemMapper;
    private final DictUsageMapper usageMapper;
    private final DictJsonCodec codec;
    private final DictQueryService queryService;

    public DictAdminService(DictMapper dictMapper, DictItemMapper itemMapper, DictUsageMapper usageMapper,
                            DictJsonCodec codec, DictQueryService queryService) {
        this.dictMapper = dictMapper;
        this.itemMapper = itemMapper;
        this.usageMapper = usageMapper;
        this.codec = codec;
        this.queryService = queryService;
    }

    // ── dict types ───────────────────────────────────────────────────

    public List<DictAdminDto.TypeView> listTypes() {
        return dictMapper.selectList(new QueryWrapper<DictEntity>()
                        .eq("mark", 1)
                        .orderByAsc("dict_code"))
                .stream().map(this::toTypeView).toList();
    }

    @Transactional
    public String createType(DictAdminDto.TypeCreateRequest req) {
        if (DictRegistry.isBuiltIn(req.dictCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.dict.codeIsBuiltin");
        }
        Long dup = dictMapper.selectCount(new QueryWrapper<DictEntity>()
                .eq("mark", 1).eq("dict_code", req.dictCode()));
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.dict.codeExists");
        }
        DictEntity d = new DictEntity();
        d.setId(IdGenerator.ulid());
        d.setDictCode(req.dictCode());
        d.setNameI18n(codec.serialize(req.nameI18n()));
        d.setBuiltin(0);
        d.setRemark(req.remark());
        dictMapper.insert(d);
        return d.getId();
    }

    @Transactional
    public void updateType(String id, DictAdminDto.TypeUpdateRequest req) {
        DictEntity d = requireType(id);
        if (req.nameI18n() != null) d.setNameI18n(codec.serialize(req.nameI18n()));
        if (req.remark() != null) d.setRemark(req.remark());
        ConcurrentEdit.requireApplied(dictMapper.updateById(d));
    }

    @Transactional
    public void deleteType(String id) {
        DictEntity d = requireType(id);
        if (d.getBuiltin() != null && d.getBuiltin() == 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.dict.typeBuiltinProtected");
        }
        Long items = itemMapper.selectCount(new QueryWrapper<DictItemEntity>()
                .eq("mark", 1).eq("dict_code", d.getDictCode()));
        if (items != null && items > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.dict.typeHasItems");
        }
        // mark は @TableLogic — updateById では SET 句から除外されるので UpdateWrapper で明示。
        dictMapper.update(null, new UpdateWrapper<DictEntity>()
                .eq("id", id).eq("mark", 1).set("mark", 0).set("update_user", "system"));
        queryService.evict(d.getDictCode());
    }

    // ── dict items ───────────────────────────────────────────────────

    public List<DictAdminDto.ItemView> listItems(String dictCode) {
        return itemMapper.selectList(new QueryWrapper<DictItemEntity>()
                        .eq("mark", 1).eq("dict_code", dictCode)
                        .orderByAsc("sort_no", "item_value"))
                .stream().map(this::toItemView).toList();
    }

    @Transactional
    public String createItem(String dictCode, DictAdminDto.ItemCreateRequest req) {
        DictEntity type = requireTypeByCode(dictCode);
        if (type.getBuiltin() != null && type.getBuiltin() == 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.dict.itemsNotEditable");
        }
        Long dup = itemMapper.selectCount(new QueryWrapper<DictItemEntity>()
                .eq("mark", 1).eq("dict_code", dictCode).eq("item_value", req.itemValue()));
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.dict.itemValueExists");
        }
        DictItemEntity it = new DictItemEntity();
        it.setId(IdGenerator.ulid());
        it.setDictCode(dictCode);
        it.setItemValue(req.itemValue());
        it.setLabelI18n(codec.serialize(req.labelI18n()));
        it.setSortNo(req.sortNo() == null ? 0 : req.sortNo());
        it.setCssClass(req.cssClass());
        it.setStatus(req.status() == null ? 1 : req.status());
        itemMapper.insert(it);
        queryService.evict(dictCode);
        return it.getId();
    }

    @Transactional
    public void updateItem(String id, DictAdminDto.ItemUpdateRequest req) {
        DictItemEntity it = requireItem(id);
        // itemValue is frozen — never updated.
        if (req.labelI18n() != null) it.setLabelI18n(codec.serialize(req.labelI18n()));
        if (req.sortNo() != null) it.setSortNo(req.sortNo());
        if (req.cssClass() != null) it.setCssClass(req.cssClass());
        if (req.status() != null) it.setStatus(req.status());
        ConcurrentEdit.requireApplied(itemMapper.updateById(it));
        queryService.evict(it.getDictCode());
    }

    @Transactional
    public void deleteItem(String id) {
        DictItemEntity it = requireItem(id);
        DictEntity type = requireTypeByCode(it.getDictCode());
        if (type.getBuiltin() != null && type.getBuiltin() == 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.dict.itemsNotEditable");
        }
        // Delete-protection (RESTRICT-like): a value the code branches on, or one
        // still referenced by business data, cannot be hard-deleted — disable it
        // (status=0) instead so historical rows keep resolving their label.
        if (DictGuards.isBranchValue(it.getDictCode(), it.getItemValue())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.dict.itemBranchProtected");
        }
        for (DictGuards.Usage u : DictGuards.usages(it.getDictCode())) {
            if (usageMapper.countUsage(u.table(), u.column(), it.getItemValue()) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.dict.itemInUse");
            }
        }
        itemMapper.update(null, new UpdateWrapper<DictItemEntity>()
                .eq("id", id).eq("mark", 1).set("mark", 0).set("update_user", "system"));
        queryService.evict(it.getDictCode());
    }

    // ── helpers ──────────────────────────────────────────────────────

    private DictEntity requireType(String id) {
        DictEntity d = dictMapper.selectById(id);
        if (d == null || d.getMark() == null || d.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "error.dict.notFound");
        }
        return d;
    }

    private DictEntity requireTypeByCode(String dictCode) {
        DictEntity d = dictMapper.selectOne(new QueryWrapper<DictEntity>()
                .eq("mark", 1).eq("dict_code", dictCode).last("limit 1"));
        if (d == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "error.dict.notFound");
        }
        return d;
    }

    private DictItemEntity requireItem(String id) {
        DictItemEntity it = itemMapper.selectById(id);
        if (it == null || it.getMark() == null || it.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "error.dict.notFound");
        }
        return it;
    }

    private DictAdminDto.TypeView toTypeView(DictEntity d) {
        Long count = itemMapper.selectCount(new QueryWrapper<DictItemEntity>()
                .eq("mark", 1).eq("dict_code", d.getDictCode()));
        return new DictAdminDto.TypeView(d.getId(), d.getDictCode(), codec.parse(d.getNameI18n()),
                d.getBuiltin(), d.getRemark(), count == null ? 0 : count.intValue());
    }

    private DictAdminDto.ItemView toItemView(DictItemEntity it) {
        return new DictAdminDto.ItemView(it.getId(), it.getDictCode(), it.getItemValue(),
                codec.parse(it.getLabelI18n()), it.getSortNo(), it.getCssClass(), it.getStatus());
    }
}
