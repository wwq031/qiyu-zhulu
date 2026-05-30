package com.qiyuzhulu.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 自定义战术 */
public class CustomTactic {
    private String name;
    private String icon;
    @JsonProperty("atk_mult")
    private double atkMult;       // 攻击倍率 0.1-5.0
    @JsonProperty("def_mult")
    private double defMult;       // 防御倍率 0.0-5.0
    private String pro;           // 优势描述
    private String con;           // 劣势描述

    public CustomTactic() {}

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getIcon() { return icon; }
    public void setIcon(String v) { this.icon = v; }
    public double getAtkMult() { return atkMult; }
    public void setAtkMult(double v) { this.atkMult = v; }
    public double getDefMult() { return defMult; }
    public void setDefMult(double v) { this.defMult = v; }
    public String getPro() { return pro; }
    public void setPro(String v) { this.pro = v; }
    public String getCon() { return con; }
    public void setCon(String v) { this.con = v; }

    /** 自动推导损耗倍率 */
    public double deriveLossMult() {
        return Math.max(0.2, Math.min(3.0, atkMult * 0.8 + Math.max(0, 1.0 - defMult) * 0.4));
    }
}
