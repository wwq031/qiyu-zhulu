package com.qiyuzhulu.service;

import java.util.*;

/**
 * 共享工具方法。消除各Service中重复的 mapOf/随机数等。
 */
public final class GameUtils {

    private GameUtils() {} // utility class

    /** 便捷构建 LinkedHashMap */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    /** 便捷构建字符串-字符串 Map */
    public static Map<String, String> mapOfS(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }
}
