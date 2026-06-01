package com.qiyuzhulu.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一的服务层返回值，替代散布全代码的 Map.of("ok", false, "message", ...)。
 * 用法：ActionResult.ok("消息") / ActionResult.fail("原因")
 */
public record ActionResult(boolean ok, String message, Map<String, Object> extra) {

    public static ActionResult ok(String msg) {
        return new ActionResult(true, msg, Map.of());
    }

    public static ActionResult fail(String msg) {
        return new ActionResult(false, msg, Map.of());
    }

    public ActionResult with(String key, Object value) {
        var m = new LinkedHashMap<>(extra);
        m.put(key, value);
        return new ActionResult(ok, message, m);
    }

    /** 转为旧式Map，兼容现有Controller */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>(extra);
        m.put("ok", ok);
        m.put("message", message);
        return m;
    }
}
