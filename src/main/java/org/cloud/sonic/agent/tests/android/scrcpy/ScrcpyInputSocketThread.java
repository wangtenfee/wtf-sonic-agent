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
package org.cloud.sonic.agent.tests.android.scrcpy;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;

import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.common.maps.ScreenMap;
import org.cloud.sonic.agent.tests.android.AndroidTestTaskBootThread;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;
import com.android.ddmlib.IDevice;

import jakarta.websocket.Session;

/**
 * scrcpy socket线程
 * 通过端口转发，将设备视频流转发到此Socket
 */
public class ScrcpyInputSocketThread extends Thread {

    private final Logger log = LoggerFactory.getLogger(ScrcpyInputSocketThread.class);

    public final static String ANDROID_INPUT_SOCKET_PRE = "android-scrcpy-input-socket-task-%s-%s-%s";

    private IDevice iDevice;

    private BlockingQueue<byte[]> dataQueue;

    private ScrcpyLocalThread scrcpyLocalThread;

    private AndroidTestTaskBootThread androidTestTaskBootThread;

    private Session session;

    public ScrcpyInputSocketThread(IDevice iDevice, BlockingQueue<byte[]> dataQueue,
            ScrcpyLocalThread scrcpyLocalThread, Session session) {
        this.iDevice = iDevice;
        this.dataQueue = dataQueue;
        this.scrcpyLocalThread = scrcpyLocalThread;
        this.session = session;
        this.androidTestTaskBootThread = scrcpyLocalThread.getAndroidTestTaskBootThread();
        this.setDaemon(false);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_INPUT_SOCKET_PRE));
    }

    public IDevice getiDevice() {
        return iDevice;
    }

    public BlockingQueue<byte[]> getDataQueue() {
        return dataQueue;
    }

    public ScrcpyLocalThread getScrcpyLocalThread() {
        return scrcpyLocalThread;
    }

    public AndroidTestTaskBootThread getAndroidTestTaskBootThread() {
        return androidTestTaskBootThread;
    }

    public Session getSession() {
        return session;
    }

    private static final int BUFFER_SIZE = 1024 * 1024 * 10;
    private static final int READ_BUFFER_SIZE = 1024 * 5;

    @Override
    public void run() {
        int scrcpyPort = PortTool.getPort();
        AndroidDeviceBridgeTool.forward(iDevice, scrcpyPort, "scrcpy");
        Socket videoSocket = new Socket();
        InputStream inputStream = null;
        try {
            videoSocket.connect(new InetSocketAddress("localhost", scrcpyPort));
            inputStream = videoSocket.getInputStream();
            if (videoSocket.isConnected()) {
                String sizeTotal = AndroidDeviceBridgeTool.getScreenSize(iDevice);
                JSONObject size = new JSONObject();
                size.put("msg", "size");
                size.put("width", sizeTotal.split("x")[0]);
                size.put("height", sizeTotal.split("x")[1]);
                BytesTool.sendText(session, size.toJSONString());
            }
            int readLength;
            int naLuIndex;
            int bufferLength = 0;
            byte[] buffer = new byte[BUFFER_SIZE];
            while (scrcpyLocalThread.isAlive()) {
                readLength = inputStream.read(buffer, bufferLength, READ_BUFFER_SIZE);
                if (readLength > 0) {
                    bufferLength += readLength;
                    for (int i = 5; i < bufferLength - 4; i++) {
                        if (buffer[i] == 0x00 &&
                                buffer[i + 1] == 0x00 &&
                                buffer[i + 2] == 0x00 &&
                                buffer[i + 3] == 0x01) {
                            naLuIndex = i;
                            byte[] naluBuffer = new byte[naLuIndex];
                            System.arraycopy(buffer, 0, naluBuffer, 0, naLuIndex);
                            dataQueue.add(naluBuffer);
                            bufferLength -= naLuIndex;
                            System.arraycopy(buffer, naLuIndex, buffer, 0, bufferLength);
                            i = 5;
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (scrcpyLocalThread.isAlive()) {
                scrcpyLocalThread.interrupt();
                log.info("scrcpy thread closed.");
            }
            if (videoSocket.isConnected()) {
                try {
                    videoSocket.close();
                    log.info("scrcpy video socket closed.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                    log.info("scrcpy input stream closed.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        AndroidDeviceBridgeTool.removeForward(iDevice, scrcpyPort, "scrcpy");
        if (session != null) {
            ScreenMap.getMap().remove(session);
        }
    }
}
