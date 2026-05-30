package com.qiyuzhulu.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyuzhulu.model.Province;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.*;

/**
 * 加载 map_data.json — 340省份数据。
 */
@Repository
public class MapDataRepo {

    @Value("${qiyu.data.map-data:data/map_data.json}")
    private String mapDataPath;

    private Map<String, Province> provinces;         // PID → Province
    private Map<String, String> nameToPid;           // 省份名 → PID
    private Map<String, String> regionCenters;       // 区域ID → 首府PID
    private List<String> crossRegionGates;
    private Map<String, String> strategicPasses;
    private Map<String, String> terrainTypes;
    private String movementRule;

    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void load() throws Exception {
        ClassPathResource resource = new ClassPathResource(mapDataPath);
        Map<String, Object> raw;
        try (InputStream is = resource.getInputStream()) {
            raw = mapper.readValue(is, new TypeReference<Map<String, Object>>() {});
        }

        // 解析省份
        provinces = new LinkedHashMap<>();
        nameToPid = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> provsRaw = (Map<String, Object>) raw.get("provinces");
        if (provsRaw != null) {
            for (var entry : provsRaw.entrySet()) {
                Province p = mapper.convertValue(entry.getValue(), Province.class);
                p.setId(entry.getKey());
                provinces.put(entry.getKey(), p);
                nameToPid.put(p.getName(), entry.getKey());
            }
        }

        // 区域中心
        @SuppressWarnings("unchecked")
        Map<String, String> centers = (Map<String, String>) raw.get("region_centers");
        regionCenters = centers != null ? centers : Map.of();

        // 跨区关口（JSON中为 {route: ""} 格式，取 keys）
        Object gatesRaw = raw.get("cross_region_gates");
        if (gatesRaw instanceof Map) {
            crossRegionGates = new ArrayList<>(((Map<?,?>) gatesRaw).keySet().stream().map(Object::toString).toList());
        } else if (gatesRaw instanceof List) {
            crossRegionGates = ((List<?>) gatesRaw).stream().map(Object::toString).toList();
        } else {
            crossRegionGates = List.of();
        }

        // 战略关隘
        @SuppressWarnings("unchecked")
        Map<String, String> passes = (Map<String, String>) raw.get("strategic_passes");
        strategicPasses = passes != null ? passes : Map.of();

        // 地形类型
        @SuppressWarnings("unchecked")
        Map<String, String> terrains = (Map<String, String>) raw.get("terrain_types");
        terrainTypes = terrains != null ? terrains : Map.of();

        movementRule = (String) raw.getOrDefault("movement_rule", "");
    }

    public Province get(String pid) { return provinces.get(pid); }
    public String getPidByName(String name) { return nameToPid.get(name); }
    public Map<String, Province> getAll() { return provinces; }
    public Map<String, String> getRegionCenters() { return regionCenters; }
    public List<String> getCrossRegionGates() { return crossRegionGates; }
    public Map<String, String> getStrategicPasses() { return strategicPasses; }
    public Map<String, String> getTerrainTypes() { return terrainTypes; }
    public String getMovementRule() { return movementRule; }
    public int size() { return provinces.size(); }
}
