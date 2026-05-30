package com.qiyuzhulu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** AI势力数据（state['ai_factions'] 中的条目） */
public class AiFactionData {
    private List<String> territories;
    @JsonProperty("faction_state")
    private FactionState factionState;
    @JsonProperty("ai_personality")
    private String aiPersonality;
    private String region;          // 可选的区域覆盖

    public AiFactionData() {}

    public List<String> getTerritories() { return territories; }
    public void setTerritories(List<String> v) { this.territories = v; }
    public FactionState getFactionState() { return factionState; }
    public void setFactionState(FactionState v) { this.factionState = v; }
    public String getAiPersonality() { return aiPersonality; }
    public void setAiPersonality(String v) { this.aiPersonality = v; }
    public String getRegion() { return region; }
    public void setRegion(String v) { this.region = v; }
}
