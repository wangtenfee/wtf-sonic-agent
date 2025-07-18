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
package org.cloud.sonic.agent.tests.android.minicap;

import java.io.File;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.tests.android.AndroidTestTaskBootThread;
import org.cloud.sonic.agent.tools.BytesTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;

import jakarta.websocket.Session;

/**
 * 启动minicap等服务的线程
 *
 * @author Eason(main) JayWenStar(until e1a877b7)
 * @date 2021/12/2 12:40 上午
 */
public class MiniCapLocalThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(MiniCapLocalThread.class);

    /**
     * 占用符逻辑参考：{@link AndroidTestTaskBootThread#ANDROID_TEST_TASK_BOOT_PRE}
     */
    public final static String ANDROID_START_MINICAP_SERVER_PRE = "android-minicap-start-minicap-server-task-%s-%s-%s";

    private IDevice iDevice;

    private String pic;

    private int finalC;

    private Session session;

    private String udId;

    private AndroidTestTaskBootThread androidTestTaskBootThread;

    private Semaphore isFinish = new Semaphore(0);

    public MiniCapLocalThread(IDevice iDevice, String pic, int finalC, Session session,
            AndroidTestTaskBootThread androidTestTaskBootThread) {
        this.iDevice = iDevice;
        this.pic = pic;
        this.finalC = finalC;
        this.session = session;
        this.udId = iDevice.getSerialNumber();
        this.androidTestTaskBootThread = androidTestTaskBootThread;

        this.setDaemon(true);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_START_MINICAP_SERVER_PRE));
    }

    public IDevice getiDevice() {
        return iDevice;
    }

    public String getPic() {
        return pic;
    }

    public int getFinalC() {
        return finalC;
    }

    public Session getSession() {
        return session;
    }

    public String getUdId() {
        return udId;
    }

    public AndroidTestTaskBootThread getAndroidTestTaskBootThread() {
        return androidTestTaskBootThread;
    }

    public Semaphore getIsFinish() {
        return isFinish;
    }

    public boolean runMiniCap(String type) {
        AtomicBoolean isSuc = new AtomicBoolean(true);
        // 先删除原有路径下的文件，防止上次出错后停止，再次打开会报错的情况
        AndroidDeviceBridgeTool.executeCommand(iDevice, "rm -rf /data/local/tmp/minicap*");
        // 获取cpu信息
        String cpuAbi = AndroidDeviceBridgeTool.getProperties(iDevice, "ro.product.cpu.abi");
        // 获取安卓sdk版本
        String androidSdkVersion = AndroidDeviceBridgeTool.getProperties(iDevice, "ro.build.version.sdk");
        // 查找对应文件并推送
        String miniCapFileName = AndroidDeviceBridgeTool.matchMiniCapFile(androidSdkVersion);
        File miniCapFile = new File("mini" + File.separator + cpuAbi + File.separator + miniCapFileName);
        File miniCapSoFile = new File("mini/minicap-shared/aosp/" + type + "/android-" + androidSdkVersion
                + File.separator + cpuAbi + File.separator + "minicap.so");
        if (!miniCapFile.exists() || (!miniCapSoFile.exists())) {
            return false;
        }
        try {
            iDevice.pushFile(miniCapFile.getAbsolutePath(), "/data/local/tmp/" + miniCapFileName);
            iDevice.pushFile(miniCapSoFile.getAbsolutePath(), "/data/local/tmp/minicap.so");
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 给文件权限
        AndroidDeviceBridgeTool.executeCommand(iDevice, "chmod 777 /data/local/tmp/" + miniCapFileName);
        String size = AndroidDeviceBridgeTool.getScreenSize(iDevice);
        String vSize;
        int q = 80;
        if (pic.equals("fixed")) {
            vSize = size;
            q = 40;
        } else {
            vSize = "800x800";
        }
        try {
            // 开始启动
            iDevice.executeShellCommand(
                    String.format("LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/%s -Q %d -S -P %s@%s/%d",
                            miniCapFileName, q, size, vSize, finalC),
                    new IShellOutputReceiver() {
                        @Override
                        public void addOutput(byte[] bytes, int i, int i1) {
                            String res = new String(bytes, i, i1);
                            log.info(res);
                            if (res.contains("Server start")) {
                                isFinish.release();
                            }
                            if (res.contains("Vector<> have different types")
                                    || res.contains("CANNOT LINK EXECUTABLE")) {
                                log.info(iDevice.getSerialNumber() + "设备不兼容" + type + "投屏！");
                                isSuc.set(false);
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
        } catch (Exception e) {
            isSuc.set(false);
            log.info("{} minicap stopped.", iDevice.getSerialNumber());
            log.error(e.getMessage());
        }
        return isSuc.get();
    }

    @Override
    public void run() {
        boolean suc;
        suc = runMiniCap("libs");
        String man = iDevice.getProperty(IDevice.PROP_DEVICE_MANUFACTURER);
        if (man == null) {
            return;
        }
        if (!suc && iDevice != null && (man.equals("Xiaomi") || man.equals("deltainno") || man.equals("HUAWEI"))) {
            suc = runMiniCap("Xiaomi");
            if (!suc && iDevice != null) {
                suc = runMiniCap("Xiaomi_NW");
                if (!suc && iDevice != null) {
                    suc = runMiniCap("Xiaomi_One");
                }
            }
        }
        if (!suc && iDevice != null && man.equals("vivo")) {
            suc = runMiniCap("vivo");
        }
        if (!suc && iDevice != null && man.equals("LGE")) {
            suc = runMiniCap("LGE");
        }
        if (session != null && (!suc)) {
            JSONObject support = new JSONObject();
            support.put("msg", "support");
            support.put("text", "该设备不兼容MiniCap投屏！");
            BytesTool.sendText(session, support.toJSONString());
        }
    }

}