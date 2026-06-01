package com.qiyuzhulu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部队。
 * 对应 Python 的 unit dict。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Unit {

    private String name;           // 部队番号，如"奉天军步兵第3号"
    private String type;           // infantry / cavalry / artillery / engineer / naval / custom
    private int attack;            // 攻击力 3-30
    private int defense;           // 防御力 3-30
    private int morale;            // 士气 10-100
    private int experience;        // 经验 1-100
    private String position;       // 所在省份PID
    private int speed;             // 1=普通, 2=骑兵
    private int strength;          // 当前兵力
    private int maxStrength;       // 最大兵力
    private String status;         // ready/fighting/marching/reinforcing/routed/annihilated/surrendered
    private String special;        // 特殊属性

    // ── 瞬态字段（不持久化或仅在回合内有效）──
    @JsonProperty("_move_path")
    private List<String> movePath;

    @JsonProperty("_move_target")
    private String moveTarget;

    @JsonProperty("_campaign")
    private String campaignId;

    @JsonProperty("_reinforce_campaign")
    private String reinforceCampaign;

    @JsonProperty("_routed_turns")
    private int routedTurns;

    @JsonProperty("_supply")
    private String supply;  // supplied/strained/cut_off/isolated

    @JsonProperty("_original_name")
    private String originalName;

    public Unit() {}

    // ── Getters / Setters ──

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }

    public int getAttack() { return attack; }
    public void setAttack(int v) { this.attack = v; }

    public int getDefense() { return defense; }
    public void setDefense(int v) { this.defense = v; }

    public int getMorale() { return morale; }
    public void setMorale(int v) { this.morale = v; }

    public int getExperience() { return experience; }
    public void setExperience(int v) { this.experience = v; }

    public String getPosition() { return position; }
    public void setPosition(String v) { this.position = v; }

    public int getSpeed() { return speed; }
    public void setSpeed(int v) { this.speed = v; }

    public int getStrength() { return strength; }
    public void setStrength(int v) { this.strength = v; }

    public int getMaxStrength() { return maxStrength; }
    public void setMaxStrength(int v) { this.maxStrength = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public String getSpecial() { return special; }
    public void setSpecial(String v) { this.special = v; }

    public List<String> getMovePath() { return movePath; }
    public void setMovePath(List<String> v) { this.movePath = v; }

    public String getMoveTarget() { return moveTarget; }
    public void setMoveTarget(String v) { this.moveTarget = v; }

    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String v) { this.campaignId = v; }

    public String getReinforceCampaign() { return reinforceCampaign; }
    public void setReinforceCampaign(String v) { this.reinforceCampaign = v; }

    public int getRoutedTurns() { return routedTurns; }
    public void setRoutedTurns(int v) { this.routedTurns = v; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String v) { this.originalName = v; }

    public String getSupply() { return supply; }
    public void setSupply(String v) { this.supply = v; }

    /** 部队是否处于活跃状态（非歼灭/投降/溃散） */
    public boolean isActive() {
        return status == null || !List.of("annihilated", "surrendered", "routed").contains(status);
    }

    /** 是否正在行军 */
    public boolean isMoving() {
        return movePath != null && !movePath.isEmpty();
    }

    /** 是否在战役中 */
    public boolean isInCampaign() {
        return campaignId != null && !campaignId.isEmpty();
    }

    /** 转为前端用的简略Map */
    public Map<String, Object> toMap(boolean isPlayer, String factionName, int index) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("type", type);
        m.put("attack", attack);
        m.put("defense", defense);
        m.put("morale", morale);
        m.put("strength", strength);
        m.put("index", index);
        m.put("is_player", isPlayer);
        m.put("faction_name", factionName);
        return m;
    }
}
