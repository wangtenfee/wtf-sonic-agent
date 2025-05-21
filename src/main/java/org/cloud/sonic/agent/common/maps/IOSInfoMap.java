package org.cloud.sonic.agent.common.maps;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.fastjson.JSONObject;

public class IOSInfoMap {
    private static Map<String, JSONObject> detailMap = new ConcurrentHashMap<String, JSONObject>();

    public static Map<String, JSONObject> getDetailMap() {
        return detailMap;
    }
}
