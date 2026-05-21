package com.platform.system.rbac.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeptNode {

    private String id;
    private String parentId;
    private String code;
    private String name;
    private String path;
    private Integer level;
    private Integer sortOrder;
    /**
     * Department-leader user id. Memo / display only — does <b>not</b> grant
     * permissions and does <b>not</b> affect data-scope visibility. Front-end
     * resolves the id to a human label via the user-list endpoint.
     */
    private String leaderUserId;
    private Integer status;
    private List<DeptNode> children = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getLeaderUserId() { return leaderUserId; }
    public void setLeaderUserId(String leaderUserId) { this.leaderUserId = leaderUserId; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public List<DeptNode> getChildren() { return children; }
    public void setChildren(List<DeptNode> children) { this.children = children; }
}
