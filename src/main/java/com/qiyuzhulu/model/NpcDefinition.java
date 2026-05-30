package com.qiyuzhulu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * NPC势力定义。替代 hostile_npcs 中的 Map<String,Object>。
 * 对应 game_data.json 中 hostile_npcs 条目。
 */
public class NpcDefinition {

    private String id;           // JSON key, e.g. "heihe_daoyin"
    private String name;         // 显示名, e.g. "黑河道尹公署"
    private String region;       // 所属大区
    private Stats stats;         // 六围属性
    private String forces;       // 武装力量描述字符串
    private String ai;           // AI行为描述

    @JsonProperty("territories")
    private List<String> territories; // 领地名称列表

    public NpcDefinition() {}

    // ── Getters / Setters ──

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getRegion() { return region; }
    public void setRegion(String v) { this.region = v; }

    public Stats getStats() { return stats; }
    public void setStats(Stats v) { this.stats = v; }

    public String getForces() { return forces; }
    public void setForces(String v) { this.forces = v; }

    public String getAi() { return ai; }
    public void setAi(String v) { this.ai = v; }

    public List<String> getTerritories() { return territories; }
    public void setTerritories(List<String> v) { this.territories = v; }

    /** 是否已被玩家完全占领 */
    public boolean isDefeatedBy(java.util.Collection<String> playerTerritories) {
        if (territories == null || territories.isEmpty()) return true;
        return territories.stream().noneMatch(t -> !playerTerritories.contains(t));
    }
}
