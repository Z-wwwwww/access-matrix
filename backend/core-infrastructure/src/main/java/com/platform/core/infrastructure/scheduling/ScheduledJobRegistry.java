package com.platform.core.infrastructure.scheduling;

import com.platform.core.common.scheduling.ScheduledJob;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 全 {@link ScheduledJob} bean を起動時に集めて {@code code → bean} で索引する。
 * 重複コードは fail-fast（{@code PermissionCode.register} の重複拒否と同じ思想）。
 *
 * <p>ジョブが 1 つも無くても正常に起動する（{@code List} 注入は空リストになる）。
 */
@Component
public class ScheduledJobRegistry {

    private final Map<String, ScheduledJob> byCode = new TreeMap<>();

    public ScheduledJobRegistry(List<ScheduledJob> jobs) {
        for (ScheduledJob job : jobs) {
            String code = job.code();
            if (code == null || code.isBlank()) {
                throw new IllegalStateException(
                        "ScheduledJob with blank code(): " + job.getClass().getName());
            }
            ScheduledJob prev = byCode.put(code, job);
            if (prev != null) {
                throw new IllegalStateException(
                        "Duplicate ScheduledJob code '" + code + "': "
                                + prev.getClass().getName() + " and " + job.getClass().getName());
            }
        }
    }

    /** コードで bean を引く（無ければ empty）。 */
    public Optional<ScheduledJob> find(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    /** 登録済みの全ジョブ（同期 guard が使う）。 */
    public List<ScheduledJob> all() {
        return List.copyOf(byCode.values());
    }
}
