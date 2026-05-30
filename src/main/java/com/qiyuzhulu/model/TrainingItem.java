package com.qiyuzhulu.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 训练队列项 */
public class TrainingItem {
    private String type = "unit_training";
    private String name;                    // 显示名
    @JsonProperty("unit_type")
    private String unitType;               // infantry / cavalry / artillery / engineer / naval / custom
    @JsonProperty("turns_left")
    private int turnsLeft;
    @JsonProperty("total_turns")
    private int totalTurns;
    private String location;                // 部署省份PID
    @JsonProperty("location_name")
    private String locationName;            // 部署省份显示名
    @JsonProperty("early_deploy")
    private boolean earlyDeploy;
    private int cost;                       // 训练花费

    public TrainingItem() {}

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getUnitType() { return unitType; }
    public void setUnitType(String v) { this.unitType = v; }
    public int getTurnsLeft() { return turnsLeft; }
    public void setTurnsLeft(int v) { this.turnsLeft = v; }
    public int getTotalTurns() { return totalTurns; }
    public void setTotalTurns(int v) { this.totalTurns = v; }
    public String getLocation() { return location; }
    public void setLocation(String v) { this.location = v; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String v) { this.locationName = v; }
    public boolean isEarlyDeploy() { return earlyDeploy; }
    public void setEarlyDeploy(boolean v) { this.earlyDeploy = v; }
    public int getCost() { return cost; }
    public void setCost(int v) { this.cost = v; }
}
