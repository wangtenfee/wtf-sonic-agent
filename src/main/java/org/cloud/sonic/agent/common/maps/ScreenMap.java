package org.cloud.sonic.agent.common.maps;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.websocket.Session;

public class ScreenMap {
    private static Map<Session, Thread> miniCapMap = new ConcurrentHashMap<>();

    public static Map<Session, Thread> getMap() {
        return miniCapMap;
    }
}
