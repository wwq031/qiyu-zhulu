package com.qiyuzhulu.model;

/**
 * 可攻击的敌方省份信息 — 替代 listEnemyProvinces() 返回的 Map<String,Object>。
 */
public class EnemyProvince {
    private String pid;
    private String name;
    private String terrain;
    private String type;          // city / rural / port
    private String owner;         // 显示名
    private String ownerFid;      // 势力/NPC ID
    private String ownerType;     // "faction" / "npc" / "npc_faction"
    private boolean inCampaign;

    public EnemyProvince() {}

    public EnemyProvince(String pid, String name, String terrain, String type,
                          String owner, String ownerFid, String ownerType, boolean inCampaign) {
        this.pid = pid; this.name = name; this.terrain = terrain; this.type = type;
        this.owner = owner; this.ownerFid = ownerFid; this.ownerType = ownerType;
        this.inCampaign = inCampaign;
    }

    public String getPid() { return pid; }
    public void setPid(String v) { this.pid = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getTerrain() { return terrain; }
    public void setTerrain(String v) { this.terrain = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getOwner() { return owner; }
    public void setOwner(String v) { this.owner = v; }
    public String getOwnerFid() { return ownerFid; }
    public void setOwnerFid(String v) { this.ownerFid = v; }
    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String v) { this.ownerType = v; }
    public boolean isInCampaign() { return inCampaign; }
    public void setInCampaign(boolean v) { this.inCampaign = v; }
}
