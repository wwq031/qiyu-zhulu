package com.qiyuzhulu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 势力定义（来自 game_data.json）。
 * 包含可玩势力和NPC势力的完整定义。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FactionDefinition {

    private String id;
    private String name;
    private String region;
    private String ideology;
    private List<String> evolution;                 // 3阶段进化名
    @JsonProperty("initial_territory")
    private List<String> initialTerritory;          // 初始省份名列表
    @JsonProperty("initial_forces")
    private List<String> initialForces;             // 初始部队名列表
    private Stats stats;                            // 初始六围
    @JsonProperty("special_units")
    private List<String> specialUnits;              // 特殊单位名
    private List<String> warfare;                   // 战争专长
    @JsonProperty("domestic_policy")
    private List<String> domesticPolicy;            // 内政标签
    @JsonProperty("social_system")
    private String socialSystem;                    // 社会制度描述
    private String diplomacy;                       // 外交描述
    private String ai;                              // AI行为描述
    private Leader leader;                          // 领袖信息
    private String lore;                            // 背景故事
    private String color;                           // 势力色 (#hex)
    @JsonProperty("national_spirit")
    private NationalSpirit nationalSpirit;          // 国家精神
    @JsonProperty("naval_power")
    private int navalPower;                         // 海军力量（可选）

    // NPC专用
    @JsonProperty("is_npc")
    private boolean npc;
    @JsonProperty("ai_personality")
    private String aiPersonality;

    public FactionDefinition() {}

    // ── Getters / Setters ──

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getRegion() { return region; }
    public void setRegion(String v) { this.region = v; }
    public String getIdeology() { return ideology; }
    public void setIdeology(String v) { this.ideology = v; }
    public List<String> getEvolution() { return evolution; }
    public void setEvolution(List<String> v) { this.evolution = v; }
    public List<String> getInitialTerritory() { return initialTerritory; }
    public void setInitialTerritory(List<String> v) { this.initialTerritory = v; }
    public List<String> getInitialForces() { return initialForces; }
    public void setInitialForces(List<String> v) { this.initialForces = v; }
    public Stats getStats() { return stats; }
    public void setStats(Stats v) { this.stats = v; }
    public List<String> getSpecialUnits() { return specialUnits; }
    public void setSpecialUnits(List<String> v) { this.specialUnits = v; }
    public List<String> getWarfare() { return warfare; }
    public void setWarfare(List<String> v) { this.warfare = v; }
    public List<String> getDomesticPolicy() { return domesticPolicy; }
    public void setDomesticPolicy(List<String> v) { this.domesticPolicy = v; }
    public String getSocialSystem() { return socialSystem; }
    public void setSocialSystem(String v) { this.socialSystem = v; }
    public String getDiplomacy() { return diplomacy; }
    public void setDiplomacy(String v) { this.diplomacy = v; }
    public String getAi() { return ai; }
    public void setAi(String v) { this.ai = v; }
    public Leader getLeader() { return leader; }
    public void setLeader(Leader v) { this.leader = v; }
    public String getLore() { return lore; }
    public void setLore(String v) { this.lore = v; }
    public String getColor() { return color; }
    public void setColor(String v) { this.color = v; }
    public NationalSpirit getNationalSpirit() { return nationalSpirit; }
    public void setNationalSpirit(NationalSpirit v) { this.nationalSpirit = v; }
    public int getNavalPower() { return navalPower; }
    public void setNavalPower(int v) { this.navalPower = v; }
    public boolean isNpc() { return npc; }
    public void setNpc(boolean v) { this.npc = v; }
    public String getAiPersonality() { return aiPersonality; }
    public void setAiPersonality(String v) { this.aiPersonality = v; }
}
