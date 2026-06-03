package com.platform.business.demo.job;

import com.platform.core.common.scheduling.JobContext;
import com.platform.core.common.scheduling.ScheduledJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 動的スケジューラの動作確認用サンプルジョブ（{@link ScheduledJob} の規範実装）。
 *
 * <p>副作用の無いハートビート — 1 行ログを出すだけ。スケジューラ / 分布式ロック /
 * 実行ログ / 起停・即時実行の全経路を最小コストで検証できる。新しいジョブを書くときは
 * このクラスの形をコピーし、{@link #execute} に業務ロジックを入れる。
 *
 * <p>既定は <b>無効</b>（{@code enabledByDefault=false}）。管理画面(平台運維)で有効化すると
 * 毎分発火する。{@code system} 租户コンテキストで走る。
 */
@Component
public class DemoHeartbeatJob implements ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(DemoHeartbeatJob.class);

    @Override
    public String code() {
        return "demo:heartbeat";
    }

    @Override
    public String defaultCron() {
        return "0 * * * * *";   // 毎分 0 秒
    }

    @Override
    public void execute(JobContext ctx) {
        log.info("[demo:heartbeat] tick — trigger={}, tenant={}, fireTime={}",
                ctx.triggerType(), ctx.tenantId(), ctx.fireTime());
    }
}
