package com.platform.system.dict.builtin;

import com.platform.core.common.dict.CommonStatus;
import com.platform.core.common.dict.DictRegistry;
import com.platform.core.common.scheduling.TriggerType;
import org.springframework.stereotype.Component;

/**
 * Registers the system domain's built-in dictionaries into {@link DictRegistry}
 * at startup (same force-load pattern as {@code *Permissions}). Each code here is
 * a code-defined enum the application branches on — exposed read-only via
 * {@code GET /dict/{code}} so frontend dropdowns/labels stop hardcoding them.
 */
@Component
public class SystemDictRegistrar {

    public SystemDictRegistrar() {
        DictRegistry.register("common_status", CommonStatus.class);
        DictRegistry.register("tenant_status", TenantStatus.class);
        DictRegistry.register("job_run_status", JobRunStatus.class);
        DictRegistry.register("job_trigger_type", TriggerType.class);
        DictRegistry.register("menu_type", MenuType.class);
        DictRegistry.register("data_scope", DataScopeDict.class);
    }
}
