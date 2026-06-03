package com.platform.core.infrastructure.scheduling;

import com.platform.core.infrastructure.scheduling.JobSchedulingConfig.NodeIdentity;
import com.platform.core.infrastructure.scheduling.mapper.JobLockMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 分布式ロックの薄いサービス。クラスタで「1 回の発火を 1 ノードだけが実行する」ことを保証する。
 * {@code core_job_lock} への原子的な取得/解放を {@link JobLockMapper} 経由で行う。
 */
@Service
public class JobLockService {

    private static final Logger log = LoggerFactory.getLogger(JobLockService.class);

    private final JobLockMapper mapper;
    private final String nodeId;

    public JobLockService(JobLockMapper mapper, NodeIdentity nodeIdentity) {
        this.mapper = mapper;
        this.nodeId = nodeIdentity.nodeId();
    }

    public String nodeId() {
        return nodeId;
    }

    /**
     * ロック取得を試みる。取得できたら {@code true}。
     *
     * @param lockName       "{jobCode}::{tenantId}"
     * @param maxRunSeconds  リース時間（= クラッシュ時の自動失効までの猶予）
     */
    public boolean tryAcquire(String lockName, int maxRunSeconds) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plusSeconds(Math.max(1, maxRunSeconds));
        try {
            return mapper.tryAcquire(lockName, now, until, nodeId) == 1;
        } catch (Exception e) {
            // 取得失敗は実行を諦めるだけ（次の発火が再試行する）。スケジューラは死なせない。
            log.warn("[scheduler] lock acquire failed for '{}': {}", lockName, e.getMessage());
            return false;
        }
    }

    /** ロック解放。{@code locked_by=nodeId} の行だけ消す。 */
    public void release(String lockName) {
        try {
            mapper.release(lockName, nodeId);
        } catch (Exception e) {
            // 解放失敗でも lock_until で自然失効するので致命ではない。
            log.warn("[scheduler] lock release failed for '{}': {}", lockName, e.getMessage());
        }
    }
}
