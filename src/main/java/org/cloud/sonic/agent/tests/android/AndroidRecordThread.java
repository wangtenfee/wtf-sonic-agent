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
package org.cloud.sonic.agent.tests.android;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * android 录像线程
 *
 * @author Eason(main) JayWenStar(until e1a877b7)
 * @date 2021/12/2 12:29 上午
 */
public class AndroidRecordThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(AndroidRecordThread.class);

    /**
     * 占用符逻辑参考：{@link AndroidTestTaskBootThread#ANDROID_TEST_TASK_BOOT_PRE}
     */
    public final static String ANDROID_RECORD_TASK_PRE = "android-record-task-%s-%s-%s";

    private final AndroidTestTaskBootThread androidTestTaskBootThread;

    public AndroidRecordThread(AndroidTestTaskBootThread androidTestTaskBootThread) {
        this.androidTestTaskBootThread = androidTestTaskBootThread;

        this.setDaemon(true);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_RECORD_TASK_PRE));
    }

    public AndroidTestTaskBootThread getAndroidTestTaskBootThread() {
        return androidTestTaskBootThread;
    }

    @Override
    public void run() {
        // AndroidStepHandler androidStepHandler =
        // androidTestTaskBootThread.getAndroidStepHandler();
        // AndroidRunStepThread runStepThread =
        // androidTestTaskBootThread.getRunStepThread();
        // String udId = androidTestTaskBootThread.getUdId();
        //
        // Boolean isSupportRecord = true;
        // String manufacturer =
        // AndroidDeviceBridgeTool.getIDeviceByUdId(udId).getProperty(IDevice.PROP_DEVICE_MANUFACTURER);
        // if (manufacturer.equals("HUAWEI") || manufacturer.equals("OPPO") ||
        // manufacturer.equals("vivo")) {
        // isSupportRecord = false;
        // }
        //
        // while (runStepThread.isAlive()) {
        // if (androidStepHandler.getAndroidDriver() == null) {
        // try {
        // Thread.sleep(500);
        // } catch (InterruptedException e) {
        // log.error(e.getMessage());
        // }
        // continue;
        // }
        // Thread miniCapPro = null;
        // AtomicReference<List<byte[]>> imgList = new AtomicReference<>(new
        // ArrayList<>());
        // AtomicReference<String[]> banner = new AtomicReference<>(new String[24]);
        // if (isSupportRecord) {
        // try {
        // androidStepHandler.startRecord();
        // } catch (Exception e) {
        // log.error(e.getMessage());
        // isSupportRecord = false;
        // }
        // } else {
        // MiniCapUtil miniCapUtil = new MiniCapUtil();
        // miniCapPro = miniCapUtil.start(udId, banner, imgList, "high", -1, null,
        // androidTestTaskBootThread);
        // }
        // int w = 0;
        // while (w < 10 && (runStepThread.isAlive())) {
        // try {
        // Thread.sleep(10000);
        // } catch (InterruptedException e) {
        // log.error(e.getMessage());
        // }
        // w++;
        // }
        // //处理录像
        // if (isSupportRecord) {
        // if (androidStepHandler.getStatus() == 3) {
        // androidStepHandler.stopRecord();
        // return;
        // } else {
        // androidStepHandler.getAndroidDriver().stopRecordingScreen();
        // }
        // } else {
        // miniCapPro.interrupt();
        // if (androidStepHandler.getStatus() == 3) {
        // File recordByRmvb = new File("test-output/record");
        // if (!recordByRmvb.exists()) {
        // recordByRmvb.mkdirs();
        // }
        // long timeMillis = Calendar.getInstance().getTimeInMillis();
        // String fileName = timeMillis + "_" + udId.substring(0, 4) + ".mp4";
        // File uploadFile = new File(recordByRmvb + File.separator + fileName);
        // try {
        // androidStepHandler.log.sendRecordLog(true, fileName,
        // RecordHandler.record(uploadFile, imgList.get()
        // , Integer.parseInt(banner.get()[9]), Integer.parseInt(banner.get()[13])));
        // } catch (FrameRecorder.Exception e) {
        // e.printStackTrace();
        // }
        // return;
        // }
        // }
        // }
    }
}
