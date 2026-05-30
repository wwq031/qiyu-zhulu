package com.qiyuzhulu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/** 建设队列项 */
public class ConstructionItem {
    private String name;                    // 项目名
    @JsonProperty("turns_left")
    private int turnsLeft;                 // 剩余回合
    private Map<String, Integer> effect;   // 效果 {industry: 3, military: 2, ...}
    @JsonProperty("building_key")
    private String buildingKey;            // factory / irrigation / academy
    @JsonProperty("location_pid")
    private String locationPid;            // 选址省份PID
    @JsonProperty("_tech_id")
    private String techId;                 // 科技ID（科技研发队列项）
    @JsonProperty("_tech_name")
    private String techName;               // 科技名

    public ConstructionItem() {}

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public int getTurnsLeft() { return turnsLeft; }
    public void setTurnsLeft(int v) { this.turnsLeft = v; }
    public Map<String, Integer> getEffect() { return effect; }
    public void setEffect(Map<String, Integer> v) { this.effect = v; }
    public String getBuildingKey() { return buildingKey; }
    public void setBuildingKey(String v) { this.buildingKey = v; }
    public String getLocationPid() { return locationPid; }
    public void setLocationPid(String v) { this.locationPid = v; }
    public String getTechId() { return techId; }
    public void setTechId(String v) { this.techId = v; }
    public String getTechName() { return techName; }
    public void setTechName(String v) { this.techName = v; }
}
