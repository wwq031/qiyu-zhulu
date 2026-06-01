package com.qiyuzhulu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

/**
 * 势力状态。对应 Python 的 faction_state dict。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FactionState {

    private String name;                        // 势力显示名
    private Stats stats;                        // 六围+海军
    private int treasury;                       // 国库
    @JsonProperty("agri_tax_rate")
    private int agriTaxRate = 20;               // 农业税率 0-100
    @JsonProperty("commerce_tax_rate")
    private int commerceTaxRate = 20;           // 商业税率 0-100
    @JsonProperty("population_support")
    private int populationSupport;              // 民心 0-100
    private int corruption;                     // 腐败度 0-100
    @JsonProperty("military_tech")
    private int militaryTech;                   // 军事科技等级 1-10
    private String capital;                     // 首都省份名
    @JsonProperty("evolution_stage")
    private int evolutionStage;                 // 进化阶段
    private List<String> territories;           // 领土（省份名列表）
    private List<String> forces;                // 遗留：原势力军队名列表
    private List<Unit> units;                   // 部队列表
    private Map<String, Integer> army;          // 按兵种计数 {infantry: N, cavalry: N, ...}
    @JsonProperty("unit_serial")
    private Map<String, Integer> unitSerial;    // 番号序号
    @JsonProperty("unit_prefix")
    private String unitPrefix;                  // 番号前缀
    @JsonProperty("province_buildings")
    private Map<String, Map<String, Integer>> provinceBuildings;  // {pid: {factory: N, irrigation: N, academy: N}}

    // ── 瞬态/缓存 ──
    @JsonProperty("_national_spirit")
    private NationalSpirit nationalSpirit;

    private Map<String, Integer> supplyStatus;  // 部队补给状态

    public FactionState() {
        this.stats = new Stats();
        this.territories = new ArrayList<>();
        this.forces = new ArrayList<>();
        this.units = new ArrayList<>();
        this.army = new HashMap<>();
        this.unitSerial = new HashMap<>();
        this.provinceBuildings = new HashMap<>();
        this.supplyStatus = new HashMap<>();
    }

    // ── Getters / Setters ──

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public Stats getStats() { return stats; }
    public void setStats(Stats v) { this.stats = v; }

    public int getTreasury() { return treasury; }
    public void setTreasury(int v) { this.treasury = v; }

    public int getAgriTaxRate() { return agriTaxRate; }
    public void setAgriTaxRate(int v) { this.agriTaxRate = Math.max(0, Math.min(100, v)); }
    public int getCommerceTaxRate() { return commerceTaxRate; }
    public void setCommerceTaxRate(int v) { this.commerceTaxRate = Math.max(0, Math.min(100, v)); }

    public int getPopulationSupport() { return populationSupport; }
    public void setPopulationSupport(int v) { this.populationSupport = v; }
    public int getCorruption() { return corruption; }
    public void setCorruption(int v) { this.corruption = v; }

    public int getMilitaryTech() { return militaryTech; }
    public void setMilitaryTech(int v) { this.militaryTech = v; }

    public String getCapital() { return capital; }
    public void setCapital(String v) { this.capital = v; }

    public int getEvolutionStage() { return evolutionStage; }
    public void setEvolutionStage(int v) { this.evolutionStage = v; }

    public List<String> getTerritories() { return territories; }
    public void setTerritories(List<String> v) { this.territories = v; }

    public List<String> getForces() { return forces; }
    public void setForces(List<String> v) { this.forces = v; }

    public List<Unit> getUnits() { return units; }
    public void setUnits(List<Unit> v) { this.units = v; }

    public Map<String, Integer> getArmy() { return army; }
    public void setArmy(Map<String, Integer> v) { this.army = v; }

    public Map<String, Integer> getUnitSerial() { return unitSerial; }
    public void setUnitSerial(Map<String, Integer> v) { this.unitSerial = v; }

    public String getUnitPrefix() { return unitPrefix; }
    public void setUnitPrefix(String v) { this.unitPrefix = v; }

    public Map<String, Map<String, Integer>> getProvinceBuildings() { return provinceBuildings; }
    public void setProvinceBuildings(Map<String, Map<String, Integer>> v) { this.provinceBuildings = v; }

    public NationalSpirit getNationalSpirit() { return nationalSpirit; }
    public void setNationalSpirit(NationalSpirit v) { this.nationalSpirit = v; }

    public Map<String, Integer> getSupplyStatus() { return supplyStatus; }
    public void setSupplyStatus(Map<String, Integer> v) { this.supplyStatus = v; }

    // ── 便捷方法 ──

    /** 获取某省份的建筑等级，无则返回0 */
    public int getBuildingLevel(String pid, String buildingKey) {
        return provinceBuildings.getOrDefault(pid, Map.of())
                .getOrDefault(buildingKey, 0);
    }

    /** 增加某省份的某建筑等级 */
    public void addBuilding(String pid, String buildingKey, int level) {
        provinceBuildings.computeIfAbsent(pid, k -> new HashMap<>())
                .merge(buildingKey, level, Integer::sum);
    }

    /** 获取按类型计数的部队数量 */
    public int getUnitCountByType(String type) {
        return (int) units.stream().filter(u -> type.equals(u.getType())).count();
    }

    /** 获取活跃部队（非歼灭/投降） */
    public List<Unit> getActiveUnits() {
        return units.stream().filter(Unit::isActive).toList();
    }
}
