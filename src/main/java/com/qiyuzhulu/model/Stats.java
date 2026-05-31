package com.qiyuzhulu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

/**
 * 六围属性 + 海军。
 * 对应 Python 的 stats dict。
 */
public class Stats {

    @JsonProperty("industry")
    private int industry;

    @JsonProperty("agriculture")
    private int agriculture;

    @JsonProperty("military")
    private int military;

    @JsonProperty("economy")
    private int economy;

    @JsonProperty("ideology")
    private int ideology;

    @JsonProperty("diplomacy")
    private int diplomacy;

    @JsonProperty("naval_power")
    private int navalPower;

    public Stats() {}

    public Stats(int industry, int agriculture, int military, int economy,
                 int ideology, int diplomacy, int navalPower) {
        this.industry = industry;
        this.agriculture = agriculture;
        this.military = military;
        this.economy = economy;
        this.ideology = ideology;
        this.diplomacy = diplomacy;
        this.navalPower = navalPower;
    }

    @JsonProperty("_flex")
    private Map<String, Integer> flexibleStats; // AI返回的非标准键（如intelligence）

    // ── Getters / Setters ──

    public int getIndustry() { return industry; }
    public void setIndustry(int v) { this.industry = v; }

    public int getAgriculture() { return agriculture; }
    public void setAgriculture(int v) { this.agriculture = v; }

    public int getMilitary() { return military; }
    public void setMilitary(int v) { this.military = v; }

    public int getEconomy() { return economy; }
    public void setEconomy(int v) { this.economy = v; }

    public int getIdeology() { return ideology; }
    public void setIdeology(int v) { this.ideology = v; }

    public int getDiplomacy() { return diplomacy; }
    public void setDiplomacy(int v) { this.diplomacy = v; }

    public int getNavalPower() { return navalPower; }
    public void setNavalPower(int v) { this.navalPower = v; }

    /** 按属性名取值（兼容灵活访问模式） */
    public int get(String key) {
        return switch (key) {
            case "industry" -> industry;
            case "agriculture" -> agriculture;
            case "military" -> military;
            case "economy" -> economy;
            case "ideology" -> ideology;
            case "diplomacy" -> diplomacy;
            case "naval_power" -> navalPower;
            default -> flexibleStats != null ? flexibleStats.getOrDefault(key, 0) : 0;
        };
    }

    /** 按属性名设值，返回自身（链式调用） */
    public Stats set(String key, int value) {
        switch (key) {
            case "industry" -> industry = value;
            case "agriculture" -> agriculture = value;
            case "military" -> military = value;
            case "economy" -> economy = value;
            case "ideology" -> ideology = value;
            case "diplomacy" -> diplomacy = value;
            case "naval_power" -> navalPower = value;
            default -> {
                if (flexibleStats == null) flexibleStats = new HashMap<>();
                flexibleStats.put(key, value);
            }
        }
        return this;
    }

    /** 增加值并返回自身 */
    public Stats add(String key, int delta) {
        return set(key, get(key) + delta);
    }

    /** 创建深拷贝 */
    public Stats copy() {
        Stats s = new Stats(industry, agriculture, military, economy, ideology, diplomacy, navalPower);
        if (flexibleStats != null) s.flexibleStats = new HashMap<>(flexibleStats);
        return s;
    }

    @Override
    public String toString() {
        return String.format("工%d 农%d 军%d 经%d 思%d 交%d 海%d",
                industry, agriculture, military, economy, ideology, diplomacy, navalPower);
    }
}
