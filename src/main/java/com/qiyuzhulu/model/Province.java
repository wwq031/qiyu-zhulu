package com.qiyuzhulu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * 省份数据。对应 map_data.json 中的 provinces 条目。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Province {

    private String id;                      // PID
    private String name;                    // 显示名
    private String region;                  // 所属大区
    private String terrain;                 // 地形类型
    private String type;                    // city/rural/port/pass
    private Map<String, Integer> connections; // 相邻省份 {neighbor_pid: distance}
    private String desc;                    // 描述
    private boolean claimable;              // 是否可占领
    private double lat;                     // 纬度
    private double lng;                     // 经度
    private String district;                // 子区域
    private int industry;                   // 工业 0-10
    private int agriculture;                // 农业 0-10
    private int commerce;                   // 商业 0-10
    private int railway;                    // 铁路等级 0-3
    private int port;                       // 港口 0-1
    private int population;                 // 人口等级
    private List<String> resources;         // 资源列表
    private String parentCity;              // 父城市名（郊野专用）

    public Province() {}

    // ── Getters / Setters ──

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getRegion() { return region; }
    public void setRegion(String v) { this.region = v; }
    public String getTerrain() { return terrain; }
    public void setTerrain(String v) { this.terrain = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public Map<String, Integer> getConnections() { return connections; }
    public void setConnections(Map<String, Integer> v) { this.connections = v; }
    public String getDesc() { return desc; }
    public void setDesc(String v) { this.desc = v; }
    public boolean isClaimable() { return claimable; }
    public void setClaimable(boolean v) { this.claimable = v; }
    public double getLat() { return lat; }
    public void setLat(double v) { this.lat = v; }
    public double getLng() { return lng; }
    public void setLng(double v) { this.lng = v; }
    public String getDistrict() { return district; }
    public void setDistrict(String v) { this.district = v; }
    public int getIndustry() { return industry; }
    public void setIndustry(int v) { this.industry = v; }
    public int getAgriculture() { return agriculture; }
    public void setAgriculture(int v) { this.agriculture = v; }
    public int getCommerce() { return commerce; }
    public void setCommerce(int v) { this.commerce = v; }
    public int getRailway() { return railway; }
    public void setRailway(int v) { this.railway = v; }
    public int getPort() { return port; }
    public void setPort(int v) { this.port = v; }
    public int getPopulation() { return population; }
    public void setPopulation(int v) { this.population = v; }
    public List<String> getResources() { return resources; }
    public void setResources(List<String> v) { this.resources = v; }
    public String getParentCity() { return parentCity; }
    public void setParentCity(String v) { this.parentCity = v; }

    public boolean isCity() { return "city".equals(type); }
    public boolean isRural() { return "rural".equals(type); }
    public boolean isPass() { return "pass".equals(type); }
    public boolean isPort() { return "port".equals(type); }
    public boolean hasRailway() { return railway > 0; }
}
