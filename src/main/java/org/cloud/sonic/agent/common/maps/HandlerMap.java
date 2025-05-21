package org.cloud.sonic.agent.common.maps;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.cloud.sonic.agent.tests.handlers.AndroidStepHandler;
import org.cloud.sonic.agent.tests.handlers.IOSStepHandler;

public class HandlerMap {
    private static Map<String, AndroidStepHandler> androidHandlerMap = new ConcurrentHashMap<String, AndroidStepHandler>();

    public static Map<String, AndroidStepHandler> getAndroidMap() {
        return androidHandlerMap;
    }

    private static Map<String, IOSStepHandler> iosHandlerMap = new ConcurrentHashMap<String, IOSStepHandler>();

    public static Map<String, IOSStepHandler> getIOSMap() {
        return iosHandlerMap;
    }
}
