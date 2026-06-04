package com.platform.business.demo.job;

import com.platform.core.common.scheduling.JobContext;
import com.platform.core.common.scheduling.ScheduledJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 毎分コンソールへ "helloworld" を出力するだけの最小ジョブ。
 *
 * <p>{@link DemoHeartbeatJob} と同形だが、こちらは <b>既定で有効</b>
 * （{@code enabledByDefault=true}）— 起動同期後すぐ毎分発火する。停止・cron 変更は
 * 平台運維(Platform Admin)の管理画面から行える。
 */
@Component
public class HelloWorldJob implements ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(HelloWorldJob.class);

    @Override
    public String code() {
        return "demo:hello-world";
    }

    @Override
    public String defaultCron() {
        return "0 * * * * *";   // 毎分 0 秒
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public void execute(JobContext ctx) {
        log.info("helloworld");
    }
}
