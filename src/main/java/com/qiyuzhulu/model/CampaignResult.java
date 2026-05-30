package com.qiyuzhulu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 战役结果（前端展示用） */
public class CampaignResult {
    private String id;
    private String province;
    @JsonProperty("province_name")
    private String provinceName;
    private String outcome;             // annihilate/decisive_win/costly_win/stalemate/setback/rout/...
    @JsonProperty("outcome_cn")
    private String outcomeCn;
    private int round;
    private double ratio;
    @JsonProperty("atk_casualties")
    private int atkCasualties;
    @JsonProperty("def_casualties")
    private int defCasualties;
    @JsonProperty("province_fell")
    private boolean provinceFell;
    @JsonProperty("attacker_faction")
    private String attackerFaction;
    @JsonProperty("defender_faction")
    private String defenderFaction;
    @JsonProperty("attacker_name")
    private String attackerName;
    @JsonProperty("defender_name")
    private String defenderName;
    @JsonProperty("attacker_units")
    private List<String> attackerUnits;
    @JsonProperty("is_player_attacker")
    private boolean isPlayerAttacker;
    @JsonProperty("honor_available")
    private boolean honorAvailable;
    @JsonProperty("honor_cost")
    private int honorCost;
    private String message;

    public CampaignResult() {}

    // Getters/Setters
    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getProvince() { return province; }
    public void setProvince(String v) { this.province = v; }
    public String getProvinceName() { return provinceName; }
    public void setProvinceName(String v) { this.provinceName = v; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String v) { this.outcome = v; }
    public String getOutcomeCn() { return outcomeCn; }
    public void setOutcomeCn(String v) { this.outcomeCn = v; }
    public int getRound() { return round; }
    public void setRound(int v) { this.round = v; }
    public double getRatio() { return ratio; }
    public void setRatio(double v) { this.ratio = v; }
    public int getAtkCasualties() { return atkCasualties; }
    public void setAtkCasualties(int v) { this.atkCasualties = v; }
    public int getDefCasualties() { return defCasualties; }
    public void setDefCasualties(int v) { this.defCasualties = v; }
    public boolean isProvinceFell() { return provinceFell; }
    public void setProvinceFell(boolean v) { this.provinceFell = v; }
    public String getAttackerFaction() { return attackerFaction; }
    public void setAttackerFaction(String v) { this.attackerFaction = v; }
    public String getDefenderFaction() { return defenderFaction; }
    public void setDefenderFaction(String v) { this.defenderFaction = v; }
    public String getAttackerName() { return attackerName; }
    public void setAttackerName(String v) { this.attackerName = v; }
    public String getDefenderName() { return defenderName; }
    public void setDefenderName(String v) { this.defenderName = v; }
    public List<String> getAttackerUnits() { return attackerUnits; }
    public void setAttackerUnits(List<String> v) { this.attackerUnits = v; }
    public boolean isPlayerAttacker() { return isPlayerAttacker; }
    public void setPlayerAttacker(boolean v) { this.isPlayerAttacker = v; }
    public boolean isHonorAvailable() { return honorAvailable; }
    public void setHonorAvailable(boolean v) { this.honorAvailable = v; }
    public int getHonorCost() { return honorCost; }
    public void setHonorCost(int v) { this.honorCost = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
}
