package com.platform.core.infrastructure.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * 動的スケジューラの基盤 bean。
 *
 * <p><b>TaskScheduler。</b> {@link ThreadPoolTaskScheduler} を 1 つ定義する。
 * アプリで唯一の {@code TaskScheduler} bean になるため、{@code @EnableScheduling} 配下の
 * {@code @Scheduled}（reconciler / {@code OutboxDispatcher}）もこのプールで走る。
 * 既定のシングルスレッドより並行性が上がる。プールサイズは {@code app.scheduler.pool-size}。
 *
 * <p><b>nodeId。</b> インスタンス識別子（{@code hostname:pid:rand}）。分布式ロックの
 * {@code locked_by} と {@code core_job_log.node_id} に使い、どのノードが実行したかを残す。
 * 起動時に 1 度だけ生成。
 */
@Configuration
public class JobSchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(JobSchedulingConfig.class);

    @Bean
    public ThreadPoolTaskScheduler taskScheduler(
            @Value("${app.scheduler.pool-size:4}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("job-sched-");
        // キャンセルしたタスクをキューから即除去 — cron 変更で reschedule を繰り返しても
        // 取り消し済み future がメモリに溜まらない。
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }

    @Bean
    public NodeIdentity jobNodeIdentity() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = "unknown";
        }
        long pid = ProcessHandle.current().pid();
        String rand = UUID.randomUUID().toString().substring(0, 8);
        String nodeId = host + ":" + pid + ":" + rand;
        log.info("[scheduler] node identity = {}", nodeId);
        return new NodeIdentity(nodeId);
    }

    /** 不変のノード識別子ホルダー。 */
    public record NodeIdentity(String nodeId) {
    }
}
