package com.platform.business.demo.dict;

import com.platform.business.demo.task.TaskStatus;
import com.platform.core.common.dict.DictGuards;
import org.springframework.stereotype.Component;

/**
 * Declares delete-protection for business-demo's managed dictionaries (form 1 / 3).
 *
 * <p>{@code task_status} is <b>form 3</b>: its options live in {@code core_dict_item}
 * (runtime-editable, served by {@code GET /dict/task_status}), but the backend
 * branches on a subset via the {@link TaskStatus} enum — so enum values can't be
 * deleted ({@code branchEnum}). {@code task_priority} is <b>form 1</b> (no enum, no
 * branching); it is only protected by reference. Neither enum is registered in
 * {@code DictRegistry} (that would make them built-in / form 2 and bypass the DB).
 */
@Component
public class DemoDictGuardRegistrar {

    public DemoDictGuardRegistrar() {
        DictGuards.register("task_status")
                .branchEnum(TaskStatus.class)
                .usedBy("demo_task", "status");
        DictGuards.register("task_priority")
                .usedBy("demo_task", "priority");
    }
}
