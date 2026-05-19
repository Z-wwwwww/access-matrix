package com.platform.system.rbac.service;

import com.platform.system.rbac.dto.DeptNode;
import com.platform.system.rbac.entity.DeptEntity;
import com.platform.system.rbac.mapper.DeptMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeptQueryService {

    private final DeptMapper deptMapper;

    public DeptQueryService(DeptMapper deptMapper) {
        this.deptMapper = deptMapper;
    }

    /** Whole-tenant tree assembled into a tree. Caffeine-cached. */
    @Cacheable(value = "deptTree", key = "#tenantId", unless = "#result.isEmpty()")
    public List<DeptNode> loadTree(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) tenantId = "default";
        List<DeptEntity> flat = deptMapper.findAllForTenant(tenantId);
        return assemble(flat);
    }

    /** Subtree IDs by a dept's materialised path. Caffeine-cached. */
    @Cacheable(value = "deptSubtree", key = "#path", unless = "#result.isEmpty()")
    public List<String> subtreeIds(String path) {
        if (path == null || path.isBlank()) return List.of();
        return deptMapper.findSubtreeIds(path);
    }

    private List<DeptNode> assemble(List<DeptEntity> flat) {
        Map<String, DeptNode> nodes = new LinkedHashMap<>();
        for (DeptEntity d : flat) nodes.put(d.getId(), toNode(d));

        Map<String, List<DeptNode>> byParent = new HashMap<>();
        List<DeptNode> roots = new ArrayList<>();
        for (DeptEntity d : flat) {
            DeptNode node = nodes.get(d.getId());
            String pid = d.getParentId();
            if (pid == null || pid.isBlank()) {
                roots.add(node);
            } else {
                DeptNode parent = nodes.get(pid);
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    // Orphan branch (parent missing / disabled) → promote to root so admin can see it.
                    roots.add(node);
                }
            }
            byParent.computeIfAbsent(pid == null ? "" : pid, k -> new ArrayList<>()).add(node);
        }

        Comparator<DeptNode> byOrder = Comparator
                .comparing((DeptNode n) -> n.getSortOrder() == null ? Integer.MAX_VALUE : n.getSortOrder())
                .thenComparing(DeptNode::getCode);
        roots.sort(byOrder);
        nodes.values().forEach(n -> n.getChildren().sort(byOrder));
        return roots;
    }

    private DeptNode toNode(DeptEntity d) {
        DeptNode n = new DeptNode();
        n.setId(d.getId());
        n.setParentId(d.getParentId());
        n.setCode(d.getCode());
        n.setName(d.getName());
        n.setPath(d.getPath());
        n.setLevel(d.getLevel());
        n.setSortOrder(d.getSortOrder());
        n.setLeaderUserId(d.getLeaderUserId());
        n.setStatus(d.getStatus());
        return n;
    }
}
