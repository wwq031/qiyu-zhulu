package com.qiyuzhulu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * 战役。
 * 对应 Python 的 active_campaigns 列表项。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Campaign {

    private String id;                       // 战役ID，如"camp_0_0"
    private String province;                 // 发生省份PID
    private String provinceName;             // 省份显示名称
    private String terrain;                  // 地形类型
    private String attackerFaction;          // 攻击方势力ID
    private String attackerName;             // 攻击方显示名称
    private String defenderFaction;          // 防守方势力ID
    private String defenderName;             // 防守方显示名称
    private String defenderType;             // faction / npc
    private List<String> attackerUnits;      // 攻击方单位名称列表
    private List<String> defenderUnits;      // 防守方单位名称列表

    @JsonProperty("attacker_tactics")
    private Map<String, String> attackerTactics;  // 单位名→战术ID

    @JsonProperty("defender_tactics")
    private Map<String, String> defenderTactics;  // 单位名→战术ID

    private int round;                       // 当前回合
    private int maxRounds;                   // 最大回合数（默认4）
    private String status;                   // ongoing/attacker_occupied/defender_held/...
    private int provinceValue;               // 省份战略价值 3-8

    // ── 运行时字段（不持久化）──
    @JsonProperty("_atk_casualties")
    private int atkCasualties;

    @JsonProperty("_def_casualties")
    private int defCasualties;

    @JsonProperty("_retreated")
    private boolean retreated;

    @JsonProperty("last_result")
    private String lastResult;

    private transient List<Unit> attackerCache;
    private transient List<Unit> defenderCache;
    private transient List<Map<String, Object>> reinforcementQueue;

    public Campaign() {}

    // ── Getters / Setters ──

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }

    public String getProvince() { return province; }
    public void setProvince(String v) { this.province = v; }

    public String getProvinceName() { return provinceName; }
    public void setProvinceName(String v) { this.provinceName = v; }

    public String getTerrain() { return terrain; }
    public void setTerrain(String v) { this.terrain = v; }

    public String getAttackerFaction() { return attackerFaction; }
    public void setAttackerFaction(String v) { this.attackerFaction = v; }

    public String getAttackerName() { return attackerName; }
    public void setAttackerName(String v) { this.attackerName = v; }

    public String getDefenderFaction() { return defenderFaction; }
    public void setDefenderFaction(String v) { this.defenderFaction = v; }

    public String getDefenderName() { return defenderName; }
    public void setDefenderName(String v) { this.defenderName = v; }

    public String getDefenderType() { return defenderType; }
    public void setDefenderType(String v) { this.defenderType = v; }

    public List<String> getAttackerUnits() { return attackerUnits; }
    public void setAttackerUnits(List<String> v) { this.attackerUnits = v; }

    public List<String> getDefenderUnits() { return defenderUnits; }
    public void setDefenderUnits(List<String> v) { this.defenderUnits = v; }

    public Map<String, String> getAttackerTactics() { return attackerTactics; }
    public void setAttackerTactics(Map<String, String> v) { this.attackerTactics = v; }

    public Map<String, String> getDefenderTactics() { return defenderTactics; }
    public void setDefenderTactics(Map<String, String> v) { this.defenderTactics = v; }

    public int getRound() { return round; }
    public void setRound(int v) { this.round = v; }

    public int getMaxRounds() { return maxRounds; }
    public void setMaxRounds(int v) { this.maxRounds = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public int getProvinceValue() { return provinceValue; }
    public void setProvinceValue(int v) { this.provinceValue = v; }

    public int getAtkCasualties() { return atkCasualties; }
    public void setAtkCasualties(int v) { this.atkCasualties = v; }

    public int getDefCasualties() { return defCasualties; }
    public void setDefCasualties(int v) { this.defCasualties = v; }

    public boolean isRetreated() { return retreated; }
    public void setRetreated(boolean v) { this.retreated = v; }

    public String getLastResult() { return lastResult; }
    public void setLastResult(String v) { this.lastResult = v; }

    public List<Unit> getAttackerCache() { return attackerCache; }
    public void setAttackerCache(List<Unit> v) { this.attackerCache = v; }

    public List<Unit> getDefenderCache() { return defenderCache; }
    public void setDefenderCache(List<Unit> v) { this.defenderCache = v; }

    public List<Map<String, Object>> getReinforcementQueue() { return reinforcementQueue; }
    public void setReinforcementQueue(List<Map<String, Object>> v) { this.reinforcementQueue = v; }

    public boolean isOngoing() {
        return "ongoing".equals(status);
    }
}
