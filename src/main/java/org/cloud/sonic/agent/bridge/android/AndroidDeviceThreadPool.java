package org.cloud.sonic.agent.bridge.android;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

/**
 * @author ZhouYiXun
 * @des 所有安卓相关线程都放这个线程池
 * @date 2021/08/16 19:26
 */
@Configuration
public class AndroidDeviceThreadPool {
    public static ExecutorService cachedThreadPool;

    @Bean
    public ExecutorService androidThreadPoolInit() {
        cachedThreadPool = Executors.newCachedThreadPool();
        return cachedThreadPool;
    }

    @PreDestroy
    public void shutdownThreadPool() {
        if (cachedThreadPool != null) {
            cachedThreadPool.shutdown();
        }
    }
}