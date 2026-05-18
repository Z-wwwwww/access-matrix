package com.platform.core.infrastructure.persistence;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.id.IdGenerator;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private static final String SYSTEM = "system";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String user = currentUser();
        String tenantId = RequestContext.tenantId();

        if (getFieldValByName("id", metaObject) == null) {
            strictInsertFill(metaObject, "id", String.class, IdGenerator.ulid());
        }
        strictInsertFill(metaObject, "tenantId", String.class, tenantId == null ? "default" : tenantId);
        strictInsertFill(metaObject, "mark", Integer.class, 1);
        strictInsertFill(metaObject, "createUser", String.class, user);
        strictInsertFill(metaObject, "updateUser", String.class, user);
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateUser", String.class, currentUser());
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    private String currentUser() {
        String userId = RequestContext.userId();
        return userId == null ? SYSTEM : userId;
    }
}
