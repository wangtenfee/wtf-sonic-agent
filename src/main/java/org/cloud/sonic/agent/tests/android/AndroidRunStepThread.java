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

import java.util.List;

import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.common.models.HandleContext;
import org.cloud.sonic.agent.tests.RunStepThread;
import org.cloud.sonic.agent.tests.handlers.StepHandlers;
import org.cloud.sonic.agent.tools.SpringTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

/**
 * android测试任务步骤运行线程
 *
 * @author Eason(main) JayWenStar(until e1a877b7)
 * @date 2021/12/2 12:30 上午
 */
public class AndroidRunStepThread extends RunStepThread {

    private final Logger log = LoggerFactory.getLogger(AndroidRunStepThread.class);

    /**
     * 占用符逻辑参考：{@link AndroidTestTaskBootThread#ANDROID_TEST_TASK_BOOT_PRE}
     */
    public final static String ANDROID_RUN_STEP_TASK_PRE = "android-run-step-task-%s-%s-%s";

    private final AndroidTestTaskBootThread androidTestTaskBootThread;

    public AndroidRunStepThread(AndroidTestTaskBootThread androidTestTaskBootThread) {
        this.androidTestTaskBootThread = androidTestTaskBootThread;

        this.setDaemon(true);
        this.setName(androidTestTaskBootThread.formatThreadName(ANDROID_RUN_STEP_TASK_PRE));
        setPlatformType(PlatformType.ANDROID);
        setLogTool(androidTestTaskBootThread.getAndroidStepHandler().getLog());
    }

    public AndroidTestTaskBootThread getAndroidTestTaskBootThread() {
        return androidTestTaskBootThread;
    }

    @Override
    public void run() {
        StepHandlers stepHandlers = SpringTool.getBean(StepHandlers.class);
        JSONObject jsonObject = androidTestTaskBootThread.getJsonObject();
        List<JSONObject> steps = jsonObject.getJSONArray("steps").toJavaList(JSONObject.class);

        HandleContext handleContext = new HandleContext();
        for (JSONObject step : steps) {
            if (isStopped()) {
                return;
            }
            try {
                stepHandlers.runStep(step, handleContext, this);
            } catch (Throwable e) {
                e.printStackTrace();
                break;
            }
        }

    }
}