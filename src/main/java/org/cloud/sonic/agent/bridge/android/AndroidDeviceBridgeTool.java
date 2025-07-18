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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.cloud.sonic.agent.common.enums.AndroidKey;
import org.cloud.sonic.agent.common.maps.AndroidThreadMap;
import org.cloud.sonic.agent.common.maps.AndroidWebViewMap;
import org.cloud.sonic.agent.common.maps.ChromeDriverMap;
import org.cloud.sonic.agent.common.maps.GlobalProcessMap;
import org.cloud.sonic.agent.tests.android.AndroidBatteryThread;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.ScheduleTool;
import org.cloud.sonic.agent.tools.file.DownloadTool;
import org.cloud.sonic.agent.tools.file.FileTool;
import org.cloud.sonic.agent.tools.file.UploadTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.InstallReceiver;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;

import lombok.extern.slf4j.Slf4j;

/**
 * @author ZhouYiXun
 * @des ADB工具类
 * @date 2021/08/16 19:26
 */
@DependsOn({ "androidThreadPoolInit" })
@Component
@Slf4j
@Order(value = Ordered.HIGHEST_PRECEDENCE)
public class AndroidDeviceBridgeTool implements ApplicationListener<ContextRefreshedEvent> {
    public static AndroidDebugBridge androidDebugBridge = null;
    private static String uiaApkVersion;
    private static String apkVersion;
    private static RestTemplate restTemplate;

    private static Map<String, Integer> forwardPortMap = new ConcurrentHashMap<>();
    @Value("${sonic.saa}")
    private String ver;
    @Value("${sonic.saus}")
    private String uiaVer;
    @Autowired
    private RestTemplate restTemplateBean;

    @Autowired
    private AndroidDeviceStatusListener androidDeviceStatusListener;

    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        init();
        log.info("Enable Android Module");
    }

    /**
     * @return java.lang.String
     * @author ZhouYiXun
     * @des 获取系统安卓SDK路径
     * @date 2021/8/16 19:35
     */
    private static String getADBPathFromSystemEnv() {
        String path = System.getenv("ANDROID_HOME");
        if (path != null) {
            path += File.separator + "platform-tools" + File.separator + "adb";
        } else {
            path = "plugins" + File.separator + "adb";
        }
        return path;
    }

    /**
     * @return void
     * @author ZhouYiXun
     * @des 定义方法
     * @date 2021/8/16 19:36
     */
    public void init() {
        apkVersion = ver;
        uiaApkVersion = uiaVer;
        restTemplate = restTemplateBean;
        // 获取系统SDK路径
        String systemADBPath = getADBPathFromSystemEnv();
        // 添加设备上下线监听
        AndroidDebugBridge.addDeviceChangeListener(androidDeviceStatusListener);
        try {
            AndroidDebugBridge.init(false);
            // 开始创建ADB
            androidDebugBridge = AndroidDebugBridge.createBridge(systemADBPath, true, Long.MAX_VALUE,
                    TimeUnit.MILLISECONDS);
            if (androidDebugBridge != null) {
                log.info("Android devices listening...");
            }
        } catch (IllegalStateException e) {
            log.warn("AndroidDebugBridge has been init!");
        }
        int count = 0;
        // 获取设备列表，超时后退出
        while (!androidDebugBridge.hasInitialDeviceList()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            count++;
            if (count > 200) {
                break;
            }
        }
        ScheduleTool.scheduleAtFixedRate(
                new AndroidBatteryThread(),
                AndroidBatteryThread.DELAY,
                AndroidBatteryThread.DELAY,
                AndroidBatteryThread.TIME_UNIT);
    }

    /**
     * @return com.android.ddmlib.IDevice[]
     * @author ZhouYiXun
     * @des 获取真实在线设备列表
     * @date 2021/8/16 19:38
     */
    public static IDevice[] getRealOnLineDevices() {
        if (androidDebugBridge != null) {
            return androidDebugBridge.getDevices();
        } else {
            return null;
        }
    }

    /**
     * @param iDevice
     * @return void
     * @author ZhouYiXun
     * @des 重启设备
     * @date 2021/8/16 19:41
     */
    public static void reboot(IDevice iDevice) {
        if (iDevice != null) {
            executeCommand(iDevice, "reboot");
        }
    }

    public static void shutdown(IDevice iDevice) {
        if (iDevice != null) {
            executeCommand(iDevice, "reboot -p");
        }
    }

    /**
     * @param udId 设备序列号
     * @return com.android.ddmlib.IDevice
     * @author ZhouYiXun
     * @des 根据udId获取iDevice对象
     * @date 2021/8/16 19:42
     */
    public static IDevice getIDeviceByUdId(String udId) {
        IDevice iDevice = null;
        IDevice[] iDevices = AndroidDeviceBridgeTool.getRealOnLineDevices();
        if (iDevices == null || iDevices.length == 0) {
            return null;
        }
        for (IDevice device : iDevices) {
            // 如果设备是在线状态并且序列号相等，则就是这个设备
            if (device.getState().equals(IDevice.DeviceState.ONLINE)
                    && device.getSerialNumber().equals(udId)) {
                iDevice = device;
                break;
            }
        }
        if (iDevice == null) {
            log.info("Device 「{}」 has not connected!", udId);
        }
        return iDevice;
    }

    /**
     * @param iDevice
     * @return java.lang.String
     * @author ZhouYiXun
     * @des 获取屏幕大小
     * @date 2021/8/16 19:44
     */
    public static String getScreenSize(IDevice iDevice) {
        String size = "";
        try {
            size = executeCommand(iDevice, "wm size");
            if (size.contains("Override size")) {
                size = size.substring(size.indexOf("Override size"));
            } else {
                size = size.split(":")[1];
            }
            // 注意顺序问题
            size = size.trim()
                    .replace(":", "")
                    .replace("Override size", "")
                    .replace("\r", "")
                    .replace("\n", "")
                    .replace(" ", "");
            if (size.length() > 20) {
                size = "unknown";
            }
        } catch (Exception e) {
            log.info("Get screen size failed, ignore when plug in moment...");
        }
        return size;
    }

    /**
     * @param iDevice
     * @param command
     * @return java.lang.String
     * @author ZhouYiXun
     * @des 发送shell指令给对应设备
     * @date 2021/8/16 19:47
     */
    public static String executeCommand(IDevice iDevice, String command) {
        CollectingOutputReceiver output = new CollectingOutputReceiver();
        try {
            iDevice.executeShellCommand(command, output, 0, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.info("Send shell command {} to device {} failed.", command, iDevice.getSerialNumber());
            log.error(e.getMessage());
        }
        return output.getOutput();
    }

    public static void install(IDevice iDevice, String path) throws InstallException {
        try {
            iDevice.installPackage(path,
                    true, new InstallReceiver(), 180L, 180L, TimeUnit.MINUTES, "-r", "-t", "-g");
        } catch (InstallException e) {
            log.info("{} install failed, cause {}, retry...", path, e.getMessage());
            try {
                iDevice.installPackage(path,
                        true, new InstallReceiver(), 180L, 180L, TimeUnit.MINUTES, "-r", "-t");
            } catch (InstallException e2) {
                log.info("{} install failed, cause {}, retry...", path, e2.getMessage());
                try {
                    iDevice.installPackage(path,
                            true, new InstallReceiver(), 180L, 180L, TimeUnit.MINUTES);
                } catch (InstallException e3) {
                    log.info("{} install failed, cause {}", path, e3.getMessage());
                    throw e3;
                }
            }
        }
    }

    public static boolean checkSonicApkVersion(IDevice iDevice) {
        String all = executeCommand(iDevice, "dumpsys package org.cloud.sonic.android");
        return all.contains("versionName=" + apkVersion);
    }

    public static boolean checkUiaApkVersion(IDevice iDevice) {
        String all = executeCommand(iDevice, "dumpsys package io.appium.uiautomator2.server");
        return all.contains("versionName=" + uiaApkVersion);
    }

    /**
     * @param iDevice
     * @param port
     * @param service
     * @return void
     * @author ZhouYiXun
     * @des 同adb forward指令，将设备内进程的端口暴露给pc本地，但是只能转发给localhost，不能转发给ipv4
     * @date 2021/8/16 19:52
     */
    public static void forward(IDevice iDevice, int port, String service) {
        String name = String.format("process-%s-forward-%s", iDevice.getSerialNumber(), service);
        Integer oldP = forwardPortMap.get(name);
        if (oldP != null) {
            removeForward(iDevice, oldP, service);
        }
        try {
            log.info("{} device {} port forward to {}", iDevice.getSerialNumber(), service, port);
            iDevice.createForward(port, service, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
            forwardPortMap.put(name, port);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public static void forward(IDevice iDevice, int port, int target) {
        String name = String.format("process-%s-forward-%d", iDevice.getSerialNumber(), target);
        Integer oldP = forwardPortMap.get(name);
        if (oldP != null) {
            removeForward(iDevice, oldP, target);
        }
        try {
            log.info("{} device {} forward to {}", iDevice.getSerialNumber(), target, port);
            iDevice.createForward(port, target);
            forwardPortMap.put(name, port);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    /**
     * @param iDevice
     * @param port
     * @param serviceName
     * @return void
     * @author ZhouYiXun
     * @des 去掉转发
     * @date 2021/8/16 19:53
     */
    public static void removeForward(IDevice iDevice, int port, String serviceName) {
        try {
            log.info("cancel {} device {} port forward to {}", iDevice.getSerialNumber(), serviceName, port);
            iDevice.removeForward(port);
            String name = String.format("process-%s-forward-%s", iDevice.getSerialNumber(), serviceName);
            if (forwardPortMap.get(name) != null) {
                forwardPortMap.remove(name);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public static void removeForward(IDevice iDevice, int port, int target) {
        try {
            log.info("cancel {} device {} forward to {}", iDevice.getSerialNumber(), target, port);
            iDevice.removeForward(port);
            String name = String.format("process-%s-forward-%d", iDevice.getSerialNumber(), target);
            if (forwardPortMap.get(name) != null) {
                forwardPortMap.remove(name);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    /**
     * @param iDevice
     * @param localPath
     * @param remotePath
     * @return void
     * @author ZhouYiXun
     * @des 推送文件
     * @date 2021/8/16 19:59
     */
    // public static void pushLocalFile(IDevice iDevice, String localPath, String
    // remotePath) {
    // AndroidDeviceThreadPool.cachedThreadPool.execute(() -> {
    // //使用iDevice的pushFile方法好像有bug，暂时用命令行去推送
    // ProcessBuilder pb = new ProcessBuilder(new
    // String[]{getADBPathFromSystemEnv(), "-s", iDevice.getSerialNumber(), "push",
    // localPath, remotePath});
    // pb.redirectErrorStream(true);
    // try {
    // pb.start();
    // } catch (IOException e) {
    // log.error(e.getMessage());
    // return;
    // }
    // });
    // }

    /**
     * @param iDevice
     * @param keyNum
     * @return void
     * @author ZhouYiXun
     * @des 输入对应按键
     * @date 2021/8/16 19:59
     */
    public static void pressKey(IDevice iDevice, int keyNum) {
        executeCommand(iDevice, String.format("input keyevent %s", keyNum));
    }

    public static void pressKey(IDevice iDevice, AndroidKey androidKey) {
        executeCommand(iDevice, String.format("input keyevent %s", androidKey.getCode()));
    }

    public static String uninstall(IDevice iDevice, String bundleId) throws InstallException {
        return iDevice.uninstallPackage(bundleId);
    }

    public static void forceStop(IDevice iDevice, String bundleId) {
        executeCommand(iDevice, String.format("am force-stop %s", bundleId));
    }

    public static String activateApp(IDevice iDevice, String bundleId) {
        return executeCommand(iDevice, String.format("monkey -p %s -c android.intent.category.LAUNCHER 1", bundleId));
    }

    /**
     * @param iDevice
     * @param key
     * @return java.lang.String
     * @author ZhouYiXun
     * @des 获取设备配置信息
     * @date 2021/8/16 20:01
     */
    public static String getProperties(IDevice iDevice, String key) {
        return iDevice.getProperty(key);
    }

    /**
     * @param sdk
     * @return java.lang.String
     * @author ZhouYiXun
     * @des 根据sdk匹配对应的文件
     * @date 2021/8/16 20:01
     */
    public static String matchMiniCapFile(String sdk) {
        String filePath;
        if (Integer.parseInt(sdk) < 16) {
            filePath = "minicap-nopie";
        } else {
            filePath = "minicap";
        }
        return filePath;
    }

    public static void startProxy(IDevice iDevice, String host, int port) {
        executeCommand(iDevice, String.format("settings put global http_proxy %s:%d", host, port));
    }

    public static void clearProxy(IDevice iDevice) {
        executeCommand(iDevice, "settings put global http_proxy :0");
    }

    public static void screen(IDevice iDevice, String type) {
        int p = getScreen(iDevice);
        try {
            switch (type) {
                case "abort" ->
                    executeCommand(iDevice,
                            "content insert --uri content://settings/system --bind name:s:accelerometer_rotation --bind value:i:0");
                case "add" -> {
                    if (p == 3) {
                        p = 0;
                    } else {
                        p++;
                    }
                    executeCommand(iDevice,
                            "content insert --uri content://settings/system --bind name:s:user_rotation --bind value:i:"
                                    + p);
                }
                case "sub" -> {
                    if (p == 0) {
                        p = 3;
                    } else {
                        p--;
                    }
                    executeCommand(iDevice,
                            "content insert --uri content://settings/system --bind name:s:user_rotation --bind value:i:"
                                    + p);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public static int getScreen(IDevice iDevice) {
        try {
            return Integer.parseInt(executeCommand(iDevice, "settings get system user_rotation")
                    .trim().replaceAll("\n", "")
                    .replace("\t", ""));
        } catch (Exception e) {
            log.error(e.getMessage());
            return 0;
        }
    }

    public static int getOrientation(IDevice iDevice) {
        String inputs = executeCommand(iDevice, "dumpsys input");
        if (inputs.contains("SurfaceOrientation")) {
            String orientationS = inputs.substring(inputs.indexOf("SurfaceOrientation")).trim();
            return BytesTool.getInt(orientationS.substring(20, orientationS.indexOf("\n")));
        } else {
            inputs = executeCommand(iDevice, "dumpsys window displays");
            String orientationS = inputs.substring(inputs.indexOf("cur=")).trim();
            String sizeT = orientationS.substring(4, orientationS.indexOf(" "));
            String[] size = sizeT.split("x");
            if (BytesTool.getInt(size[0]) > BytesTool.getInt(size[1])) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    public static int[] getDisplayOfAllScreen(IDevice iDevice, int width, int height, int ori) {
        String out = executeCommand(iDevice, "dumpsys window windows");
        String[] windows = out.split("Window #");
        String packageName = getCurrentPackage(iDevice);
        int offsetx = 0, offsety = 0;
        if (packageName != null) {
            for (String window : windows) {
                if (window.contains("package=" + packageName)) {
                    String patten = "Frames: containing=\\[(\\d+\\.?\\d*),(\\d+\\.?\\d*)]\\[(\\d+\\.?\\d*),(\\d+\\.?\\d*)]";
                    Pattern pattern = Pattern.compile(patten);
                    Matcher m = pattern.matcher(window);
                    while (m.find()) {
                        if (m.groupCount() != 4)
                            break;
                        offsetx = Integer.parseInt(m.group(1));
                        offsety = Integer.parseInt(m.group(2));
                        width = Integer.parseInt(m.group(3));
                        height = Integer.parseInt(m.group(4));

                        if (ori == 1 || ori == 3) {
                            int tempOffsetX = offsetx;
                            int tempWidth = width;

                            offsetx = offsety;
                            offsety = tempOffsetX;
                            width = height;
                            height = tempWidth;
                        }

                        width -= offsetx;
                        height -= offsety;
                    }
                }
            }
        }
        return new int[] { offsetx, offsety, width, height };
    }

    public static String getCurrentPackage(IDevice iDevice) {
        int api = Integer.parseInt(iDevice.getProperty(IDevice.PROP_BUILD_API_LEVEL));
        String cmd = AndroidDeviceBridgeTool.executeCommand(iDevice,
                String.format("dumpsys window %s", api >= 29 ? "displays" : "windows"));
        String result = "";
        try {
            String start = cmd.substring(cmd.indexOf("mCurrentFocus="));
            String end = start.substring(0, start.indexOf("/"));
            result = end.substring(end.lastIndexOf(" ") + 1);
        } catch (Exception ignored) {
        }
        if (result.length() == 0) {
            try {
                String start = cmd.substring(cmd.indexOf("mFocusedApp="));
                String startCut = start.substring(0, start.indexOf("/"));
                result = startCut.substring(startCut.lastIndexOf(" ") + 1);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static String getCurrentActivity(IDevice iDevice) {
        int api = Integer.parseInt(iDevice.getProperty(IDevice.PROP_BUILD_API_LEVEL));
        String cmd = AndroidDeviceBridgeTool.executeCommand(iDevice,
                String.format("dumpsys window %s", api >= 29 ? "displays" : "windows"));
        String result = "";
        try {
            Pattern pattern = Pattern.compile("mCurrentFocus=(?!null)[^,]+");
            Matcher matcher = pattern.matcher(cmd);
            if (matcher.find()) {
                String start = cmd.substring(matcher.start());
                String end = start.substring(start.indexOf("/") + 1);
                result = end.substring(0, end.indexOf("}"));
            }
        } catch (Exception ignored) {
        }
        if (result.length() == 0) {
            try {
                String start = cmd.substring(cmd.indexOf("mFocusedApp="));
                String end = start.substring(start.indexOf("/") + 1);
                result = end.substring(0, end.indexOf(" "));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static void pushToCamera(IDevice iDevice, String url) {
        try {
            File image = DownloadTool.download(url);
            iDevice.pushFile(image.getAbsolutePath(), "/sdcard/DCIM/Camera/" + image.getName());
            executeCommand(iDevice,
                    "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/DCIM/Camera/"
                            + image.getName());
        } catch (IOException | AdbCommandRejectedException | SyncException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    public static void searchDevice(IDevice iDevice) {
        executeCommand(iDevice, "am start -n org.cloud.sonic.android/.plugin.activityPlugin.SearchActivity");
    }

    public static void controlBattery(IDevice iDevice, int type) {
        if (type == 0) {
            executeCommand(iDevice, "dumpsys battery unplug && dumpsys battery set status 1");
        }
        if (type == 1) {
            executeCommand(iDevice, "dumpsys battery reset");
        }
    }

    public static String pullFile(IDevice iDevice, String path) {
        String result = null;
        File base = new File("test-output" + File.separator + "pull");
        String filename = base.getAbsolutePath() + File.separator + UUID.randomUUID();
        File file = new File(filename);
        file.mkdirs();
        String system = System.getProperty("os.name").toLowerCase();
        String processName = String.format("process-%s-pull-file", iDevice.getSerialNumber());
        if (GlobalProcessMap.getMap().get(processName) != null) {
            Process ps = GlobalProcessMap.getMap().get(processName);
            ps.children().forEach(ProcessHandle::destroy);
            ps.destroy();
        }
        try {
            Process process = null;
            String command = String.format("%s -s %s pull %s %s", getADBPathFromSystemEnv(), iDevice.getSerialNumber(),
                    path, file.getAbsolutePath());
            if (system.contains("win")) {
                process = Runtime.getRuntime().exec(new String[] { "cmd", "/c", command });
            } else if (system.contains("linux") || system.contains("mac")) {
                process = Runtime.getRuntime().exec(new String[] { "sh", "-c", command });
            }
            GlobalProcessMap.getMap().put(processName, process);
            boolean isRunning;
            int wait = 0;
            do {
                Thread.sleep(500);
                wait++;
                isRunning = false;
                List<ProcessHandle> processHandleList = process.children().collect(Collectors.toList());
                if (processHandleList.size() == 0) {
                    if (process.isAlive()) {
                        isRunning = true;
                    }
                } else {
                    for (ProcessHandle p : processHandleList) {
                        if (p.isAlive()) {
                            isRunning = true;
                            break;
                        }
                    }
                }
                if (wait >= 20) {
                    process.children().forEach(ProcessHandle::destroy);
                    process.destroy();
                    break;
                }
            } while (isRunning);
            File re = new File(filename + File.separator
                    + (path.lastIndexOf("/") == -1 ? path : path.substring(path.lastIndexOf("/"))));
            result = UploadTools.upload(re, "packageFiles");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            FileTool.deleteDir(file);
        }
        return result;
    }

    public static boolean installSonicApk(IDevice iDevice) {
        String path = executeCommand(iDevice, "pm path org.cloud.sonic.android").trim()
                .replaceAll("package:", "")
                .replaceAll("\n", "")
                .replaceAll("\t", "");
        if (path.length() > 0 && checkSonicApkVersion(iDevice)) {
            log.info("Check Sonic Apk version and status pass...");
            return true;
        } else {
            log.info("Sonic Apk version not newest or not install, starting install...");
            try {
                uninstall(iDevice, "org.cloud.sonic.android");
            } catch (InstallException e) {
                log.info("uninstall sonic Apk err, cause {}", e.getMessage());
            }
            try {
                install(iDevice, "plugins/sonic-android-apk.apk");
                executeCommand(iDevice, "appops set org.cloud.sonic.android POST_NOTIFICATION allow");
                executeCommand(iDevice, "appops set org.cloud.sonic.android RUN_IN_BACKGROUND allow");
                executeCommand(iDevice, "dumpsys deviceidle whitelist +org.cloud.sonic.android");
                log.info("Sonic Apk install successful.");
                return true;
            } catch (InstallException e) {
                log.info("Sonic Apk install failed.");
                return false;
            }
        }
    }

    public static int startUiaServer(IDevice iDevice, int port) throws InstallException {
        Thread s = AndroidThreadMap.getMap().get(String.format("%s-uia-thread", iDevice.getSerialNumber()));
        if (s != null) {
            s.interrupt();
        }
        if (!checkUiaApkVersion(iDevice)) {
            uninstall(iDevice, "io.appium.uiautomator2.server");
            uninstall(iDevice, "io.appium.uiautomator2.server.test");
            install(iDevice, "plugins/sonic-appium-uiautomator2-server.apk");
            install(iDevice, "plugins/sonic-appium-uiautomator2-server-test.apk");
            executeCommand(iDevice, "appops set io.appium.uiautomator2.server RUN_IN_BACKGROUND allow");
            executeCommand(iDevice, "appops set io.appium.uiautomator2.server.test RUN_IN_BACKGROUND allow");
            executeCommand(iDevice, "dumpsys deviceidle whitelist +io.appium.uiautomator2.server");
            executeCommand(iDevice, "dumpsys deviceidle whitelist +io.appium.uiautomator2.server.test");
        }
        UiaThread uiaThread = new UiaThread(iDevice, port);
        uiaThread.start();
        int wait = 0;
        while (!uiaThread.getIsOpen()) {
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            wait++;
            if (wait >= 20) {
                break;
            }
        }
        AndroidThreadMap.getMap().put(String.format("%s-uia-thread", iDevice.getSerialNumber()), uiaThread);
        return port;
    }

    public static int startUiaServer(IDevice iDevice) throws InstallException {
        return startUiaServer(iDevice, PortTool.getPort());
    }

    static class UiaThread extends Thread {

        private IDevice iDevice;
        private int port;
        private boolean isOpen = false;

        public UiaThread(IDevice iDevice, int port) {
            this.iDevice = iDevice;
            this.port = port;
        }

        public boolean getIsOpen() {
            return isOpen;
        }

        @Override
        public void run() {
            forward(iDevice, port, 6790);
            try {
                iDevice.executeShellCommand(
                        "am instrument -w io.appium.uiautomator2.server.test/androidx.test.runner.AndroidJUnitRunner -e DISABLE_SUPPRESS_ACCESSIBILITY_SERVICES true -e disableAnalytics true",
                        new IShellOutputReceiver() {
                            @Override
                            public void addOutput(byte[] bytes, int i, int i1) {
                                String res = new String(bytes, i, i1);
                                log.info(res);
                                if (res.contains("io.appium.uiautomator2.server.test.AppiumUiAutomator2Server:")) {
                                    try {
                                        Thread.sleep(2000);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    isOpen = true;
                                }
                            }

                            @Override
                            public void flush() {
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }
                        }, 0, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            } finally {
                removeForward(iDevice, port, 6790);
            }
        }

        @Override
        public void interrupt() {
            super.interrupt();
        }
    }

    public static void clearWebView(IDevice iDevice) {
        List<JSONObject> has = AndroidWebViewMap.getMap().get(iDevice);
        if (has != null && has.size() > 0) {
            for (JSONObject j : has) {
                AndroidDeviceBridgeTool.removeForward(iDevice, j.getInteger("port"), j.getString("name"));
            }
        }
        AndroidWebViewMap.getMap().remove(iDevice);
    }

    public static void sendKeysByKeyboard(IDevice iDevice, String msg) {
        executeCommand(iDevice, String.format("am broadcast -a SONIC_KEYBOARD --es msg \"%s\"", msg));
    }

    public static boolean setClipperByKeyboard(IDevice iDevice, String msg) {
        String suc = executeCommand(iDevice, String.format("am broadcast -a SONIC_CLIPPER_SET --es msg \"%s\"", msg));
        return suc.contains("result=-1");
    }

    public static String getClipperByKeyboard(IDevice iDevice) {
        String suc = executeCommand(iDevice, "am broadcast -a SONIC_CLIPPER_GET");
        if (suc.contains("result=-1")) {
            return suc.substring(suc.indexOf("data=\"") + 6, suc.length() - 2);
        } else {
            return "";
        }
    }

    /**
     * 获取完整的ChromeVersion，格式:83.0.4103.106
     *
     * @param iDevice     IDevice
     * @param packageName 应用包名
     * @return 完整的ChromeVersion
     */
    public static String getFullChromeVersion(IDevice iDevice, String packageName) {
        String chromeVersion = "";
        List<JSONObject> result = getWebView(iDevice);
        if (result.size() > 0) {
            for (JSONObject j : result) {
                if (packageName.equals(j.getString("package"))) {
                    chromeVersion = j.getString("version");
                    break;
                }
            }
        }
        if (chromeVersion.length() == 0) {
            return null;
        } else {
            chromeVersion = chromeVersion.replace("Chrome/", "");
        }
        return chromeVersion;
    }

    /**
     * 只获取ChromeVersion的主版本
     *
     * @param chromeVersion 完整的ChromeVersion，格式:83.0.4103.106
     * @return 主版本，如83
     */
    public static String getMajorChromeVersion(String chromeVersion) {
        if (StringUtils.isEmpty(chromeVersion)) {
            return null;
        }
        int end = (chromeVersion.contains(".") ? chromeVersion.indexOf(".") : chromeVersion.length() - 1);
        return chromeVersion.substring(0, end);
    }

    /**
     * 根据IDevice以及完整的ChromeVersion获取chromeDriver
     *
     * @param iDevice           IDevice
     * @param fullChromeVersion 完整的版本号，形如:83.0.4103.106
     * @return chromeDriver file
     * @throws IOException IOException
     */
    public static File getChromeDriver(IDevice iDevice, String fullChromeVersion) throws IOException {
        if (fullChromeVersion == null) {
            return null;
        }
        clearWebView(iDevice);

        String system = System.getProperty("os.name").toLowerCase();
        File search = new File(String.format("webview/%s_chromedriver%s", fullChromeVersion,
                (system.contains("win") ? ".exe" : "")));
        if (search.exists()) {
            return search;
        }

        String majorChromeVersion = getMajorChromeVersion(fullChromeVersion);
        boolean greaterThen114 = majorChromeVersion != null && Integer.parseInt(majorChromeVersion) > 114;
        HttpHeaders headers = new HttpHeaders();
        if (system.contains("win")) {
            system = "win32";
        } else if (system.contains("linux")) {
            system = "linux64";
        } else {
            String arch = System.getProperty("os.arch").toLowerCase();
            if (arch.contains("aarch64")) {
                if (greaterThen114) {
                    system = "mac-arm64";
                } else {
                    String driverList = restTemplate.exchange(
                            String.format("https://registry.npmmirror.com/-/binary/chromedriver/%s/",
                                    ChromeDriverMap.getMap().get(majorChromeVersion)),
                            HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
                    boolean findM1ChromeDriver = false;
                    for (Object obj : JSONArray.parseArray(driverList)) {
                        JSONObject jsonObject = JSONObject.parseObject(obj.toString());
                        String fullName = jsonObject.getString("name");
                        if (fullName.contains("m1") || fullName.contains("arm")) {
                            system = fullName.substring(fullName.indexOf("mac"), fullName.indexOf("."));
                            findM1ChromeDriver = true;
                            break;
                        }
                    }
                    // <=86版本，google未提供M1架构的chromeDriver，改为固定用chromedriver_mac64.zip
                    if (!findM1ChromeDriver) {
                        system = "mac64";
                    }
                }
            } else {
                if (greaterThen114) {
                    system = "mac-x64";
                } else {
                    system = "mac64";
                }
            }
        }
        File file;
        if (greaterThen114) {
            // Starting with M115 the ChromeDriver release process is integrated with that
            // of Chrome.
            // The latest Chrome + ChromeDriver releases per release channel (Stable, Beta,
            // Dev, Canary) are available
            // at the Chrome for Testing (CfT) availability dashboard.
            file = DownloadTool.download(String.format(
                    "https://storage.googleapis.com/chrome-for-testing-public/%s/%s/chromedriver-%s.zip",
                    ChromeDriverMap.getMap().get(majorChromeVersion), system, system));
        } else {
            file = DownloadTool.download(String.format(
                    "https://cdn.npmmirror.com/binaries/chromedriver/%s/chromedriver_%s.zip",
                    ChromeDriverMap.getMap().get(majorChromeVersion), system));
        }
        return FileTool.unZipChromeDriver(file, fullChromeVersion, greaterThen114, system);
    }

    public static List<JSONObject> getWebView(IDevice iDevice) {
        clearWebView(iDevice);
        List<JSONObject> has = new ArrayList<>();
        Set<String> webSet = new HashSet<>();
        String[] out = AndroidDeviceBridgeTool
                .executeCommand(iDevice, "cat /proc/net/unix").split("\n");
        for (String w : out) {
            if (w.contains("webview") || w.contains("WebView") || w.contains("_devtools_remote")) {
                if (w.contains("@") && w.indexOf("@") + 1 < w.length()) {
                    webSet.add(w.substring(w.indexOf("@") + 1));
                }
            }
        }
        List<JSONObject> result = new ArrayList<>();
        if (webSet.size() > 0) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");
            for (String ws : webSet) {
                int port = PortTool.getPort();
                AndroidDeviceBridgeTool.forward(iDevice, port, ws);
                JSONObject j = new JSONObject();
                j.put("port", port);
                j.put("name", ws);
                has.add(j);
                JSONObject r = new JSONObject();
                r.put("port", port);
                try {
                    ResponseEntity<LinkedHashMap> infoEntity = restTemplate.exchange(
                            "http://localhost:" + port + "/json/version", HttpMethod.GET, new HttpEntity<>(headers),
                            LinkedHashMap.class);
                    if (infoEntity.getStatusCode() == HttpStatus.OK) {
                        r.put("version", infoEntity.getBody().get("Browser"));
                        r.put("package", infoEntity.getBody().get("Android-Package"));
                    }
                } catch (Exception e) {
                    continue;
                }
                try {
                    ResponseEntity<JSONArray> responseEntity = restTemplate.exchange(
                            "http://localhost:" + port + "/json/list", HttpMethod.GET, new HttpEntity<>(headers),
                            JSONArray.class);
                    if (responseEntity.getStatusCode() == HttpStatus.OK) {
                        List<JSONObject> child = new ArrayList<>();
                        for (Object e : responseEntity.getBody()) {
                            LinkedHashMap objE = (LinkedHashMap) e;
                            JSONObject c = new JSONObject();
                            c.put("favicon", objE.get("faviconUrl"));
                            c.put("title", objE.get("title"));
                            c.put("url", objE.get("url"));
                            c.put("id", objE.get("id"));
                            child.add(c);
                        }
                        r.put("children", child);
                        result.add(r);
                    }
                } catch (Exception ignored) {
                }
            }
            AndroidWebViewMap.getMap().put(iDevice, has);
        }
        return result;
    }
}
