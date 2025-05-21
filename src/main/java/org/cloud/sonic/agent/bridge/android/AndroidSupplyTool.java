/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published
 *   by the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.bridge.android;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.cloud.sonic.agent.common.maps.GlobalProcessMap;
import org.cloud.sonic.agent.tests.LogUtil;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AndroidSupplyTool implements ApplicationListener<ContextRefreshedEvent> {
    private static final File sasBinary = new File("plugins" + File.separator + "sonic-android-supply");
    private static final String sas = sasBinary.getAbsolutePath();

    private static final Map<String, Thread> perfmonThreads = new ConcurrentHashMap<>();

    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        log.info("Enable sonic-android-supply Module");
    }

    public static void startShare(String udId, Session session) {
        executeShare(udId, session, PortTool.getPort());
    }

    public static void startShare(String udId, int port) {
        executeShare(udId, null, port);
    }

    private static void executeShare(String udId, Session session, int port) {
        JSONObject sasJSON = new JSONObject();
        sasJSON.put("msg", "sas");
        sasJSON.put("isEnable", true);
        stopShare(udId);
        String processName = String.format("process-%s-sas", udId);
        String commandLine = String.format("%s share -s %s --translate-port %d", sas, udId, port);
        try {
            Process ps = executeCommand(commandLine);
            GlobalProcessMap.getMap().put(processName, ps);
            sasJSON.put("port", port);
        } catch (IOException e) {
            log.error("Error starting Android share", e);
            sasJSON.put("port", 0);
        } finally {
            if (session != null) {
                BytesTool.sendText(session, sasJSON.toJSONString());
            }
        }
    }

    public static void stopShare(String udId) {
        terminateProcess(String.format("process-%s-sas", udId));
    }

    public static void stopPerfmon(String udId) {
        // 中断并移除性能监控线程
        Thread perfmonThread = perfmonThreads.get(udId);
        if (perfmonThread != null) {
            perfmonThread.interrupt(); // 中断线程
            perfmonThreads.remove(udId); // 移除线程
        }
        terminateProcess(String.format("process-%s-perfmon", udId));
    }

    private static void terminateProcess(String processName) {
        Process ps = GlobalProcessMap.getMap().get(processName);
        if (ps != null) {
            ps.children().forEach(ProcessHandle::destroy); // 销毁子进程
            ps.destroy(); // 销毁当前进程

            try {
                boolean exited = ps.waitFor(5, TimeUnit.SECONDS); // 等待进程终止，超时5秒
                if (!exited) {
                    ps.destroyForcibly(); // 强制终止
                }
            } catch (InterruptedException e) {
                log.error("Process termination interrupted: ", e);
                Thread.currentThread().interrupt(); // 重新设置中断状态
            }

            GlobalProcessMap.getMap().remove(processName); // 从全局映射中移除进程
        }
    }

    public static void startPerfmon(String udId, String pkg, Session session, LogUtil logUtil, int interval) {
        stopPerfmon(udId); // 启动前先停止已有监控
        String processName = String.format("process-%s-perfmon", udId);
        String commandLine = String.format("%s perfmon -s %s -r %d %s -j --sys-cpu --sys-mem --sys-network", sas, udId,
                interval, pkg.isEmpty() ? "" : "--proc-cpu --proc-fps --proc-mem --proc-threads -p " + pkg);

        try {
            Process ps = executeCommand(commandLine);
            GlobalProcessMap.getMap().put(processName, ps);

            // 启动输出线程并保存
            Thread perfmonThread = new Thread(() -> {
                try (BufferedReader stdInput = new BufferedReader(new InputStreamReader(ps.getInputStream()))) {
                    String line;
                    while (!Thread.currentThread().isInterrupted() && (line = stdInput.readLine()) != null) {
                        processPerfmonLine(line, session, logUtil);
                    }
                } catch (IOException e) {
                    // 忽略流关闭的异常
                    if (!"Stream closed".equals(e.getMessage())) {
                        log.error("Error reading perfmon output", e);
                    }
                }
            });
            perfmonThreads.put(udId, perfmonThread);
            perfmonThread.start(); // 启动输出线程

        } catch (IOException e) {
            log.error("Error starting performance monitor", e);
        }
    }

    private static Process executeCommand(String command) throws IOException {
        String system = System.getProperty("os.name").toLowerCase();
        Process process;

        if (system.contains("win")) {
            process = Runtime.getRuntime().exec(new String[] { "cmd", "/c", command });
        } else if (system.contains("linux") || system.contains("mac")) {
            process = Runtime.getRuntime().exec(new String[] { "sh", "-c", command });
        } else {
            throw new RuntimeException("Unsupported operating system: " + system);
        }

        // 打印输出和错误流
        printProcessOutput(process);

        return process;
    }

    private static void printProcessOutput(Process process) {
        // 创建线程打印标准输出
        new Thread(() -> {
            try (BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = stdInput.readLine()) != null) {
                    log.info("Output: " + line);
                }
            } catch (IOException e) {
                // 忽略流关闭的异常
                if (!"Stream closed".equals(e.getMessage())) {
                    log.error("Error reading standard output", e);
                }
            }
        }).start();

        // 创建线程打印错误输出
        new Thread(() -> {
            try (BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = stdError.readLine()) != null) {
                    log.error("Error: " + line);
                }
            } catch (IOException e) {
                // 忽略流关闭的异常
                if (!"Stream closed".equals(e.getMessage())) {
                    log.error("Error reading error output", e);
                }
            }
        }).start();
    }

    private static void processPerfmonLine(String line, Session session, LogUtil logUtil) {
        try {
            JSONObject perf = JSON.parseObject(line);
            if (session != null) {
                JSONObject perfDetail = new JSONObject();
                perfDetail.put("msg", "perfDetail");
                perfDetail.put("detail", perf);
                BytesTool.sendText(session, perfDetail.toJSONString());
            }
            if (logUtil != null) {
                logUtil.sendPerLog(perf.toJSONString());
            }
        } catch (Exception e) {
            log.error("Error processing performance monitor line", e);
        }
    }
}
