package com.qiyuzhulu.model;

import java.util.Map;

/** 国家精神 */
public class NationalSpirit {
    private String name;
    private String desc;
    private Map<String, Integer> effects;  // 属性修正 {industry: +5, ideology: -3}

    public NationalSpirit() {}

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getDesc() { return desc; }
    public void setDesc(String v) { this.desc = v; }
    public Map<String, Integer> getEffects() { return effects; }
    public void setEffects(Map<String, Integer> v) { this.effects = v; }
}
