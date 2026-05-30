package com.qiyuzhulu.model;

/** 自定义兵种 */
public class CustomUnitType {
    private String name;
    private String icon;
    private int atk;                // 攻击力 5-50
    private int def;                // 防御力 3-50
    private int morale;             // 士气 20-100
    private int exp;                // 经验 10-80
    private String suffix;          // 番号后缀：号/师/旅/团/营/连

    public CustomUnitType() {}

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getIcon() { return icon; }
    public void setIcon(String v) { this.icon = v; }
    public int getAtk() { return atk; }
    public void setAtk(int v) { this.atk = v; }
    public int getDef() { return def; }
    public void setDef(int v) { this.def = v; }
    public int getMorale() { return morale; }
    public void setMorale(int v) { this.morale = v; }
    public int getExp() { return exp; }
    public void setExp(int v) { this.exp = v; }
    public String getSuffix() { return suffix; }
    public void setSuffix(String v) { this.suffix = v; }

    /** 自动推导训练费用 */
    public double deriveCost() {
        return Math.max(5, atk * 0.55 + def * 0.40 + morale * 0.04 + exp * 0.03);
    }

    /** 自动推导训练回合数 */
    public int deriveTurns() {
        return Math.max(2, (int) Math.round(deriveCost() / 4.5));
    }

    /** 自动推导军事加成 */
    public int deriveMilitaryGain() {
        return Math.max(3, (int) Math.round(deriveCost() * 0.55));
    }
}
