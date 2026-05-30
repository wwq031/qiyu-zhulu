package com.qiyuzhulu.model;

/**
 * 省份引用 — 同时持有PID和名称，消除"到底是名还是PID"的困惑。
 * 作为 record 自动获得 equals/hashCode/toString。
 */
public record ProvinceRef(String pid, String name) {

    public static final ProvinceRef BEIJING = new ProvinceRef("beijing", "北京");

    public static ProvinceRef of(String pidOrName, java.util.function.Function<String, ProvinceRef> resolver) {
        return resolver.apply(pidOrName);
    }

    /** 从PID创建（名称为空占位） */
    public static ProvinceRef fromPid(String pid) {
        return new ProvinceRef(pid, pid);
    }

    @Override
    public String toString() { return name + "(" + pid + ")"; }
}
