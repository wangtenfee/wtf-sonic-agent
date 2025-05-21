package org.cloud.sonic.agent.aspect;

import org.cloud.sonic.agent.common.models.HandleContext;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSONObject;

@Component
public class TestAop {
    @IteratorCheck
    public void runStep(JSONObject stepJSON, HandleContext handleContext) {
        System.out.println(stepJSON.toJSONString());
        assert handleContext.currentIteratorElement != null;
    }
}
