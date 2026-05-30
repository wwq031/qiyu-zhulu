package com.qiyuzhulu.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyuzhulu.model.FactionDefinition;
import com.qiyuzhulu.model.NpcDefinition;
import com.qiyuzhulu.model.Stats;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.*;
import java.util.*;

/**
 * 加载 game_data.json — 势力定义、NPC、区域、科技树等。
 */
@Repository
public class GameDataRepo {

    @Value("${qiyu.data.game-data:data/game_data.json}")
    private String gameDataPath;

    private Map<String, Object> raw;                        // 原始JSON
    private Map<String, FactionDefinition> factions;        // 28可玩势力
    private Map<String, FactionDefinition> npcFactions;     // 29 NPC势力
    private Map<String, NpcDefinition> hostileNpcs;          // 28 hostile NPC
    private Map<String, Map<String, Object>> regions;       // 7大区
    private Map<String, Object> phases;                     // 5阶段
    private Map<String, Object> techTree;                   // 科技树
    private List<Map<String, Object>> smallEvents;          // 随机事件列表
    private Map<String, Object> aiSystem;                   // AI系统配置
    private Map<String, Object> foreignPowers;              // 列强
    private Map<String, Object> statsSystem;                // 属性系统
    private Map<String, Object> resolutions;                // 国策决议（从resolutions.json）

    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource(gameDataPath);
        try (InputStream is = resource.getInputStream()) {
            raw = mapper.readValue(is, new TypeReference<Map<String, Object>>() {});
        }

        // 解析势力
        factions = parseFactions("factions");
        npcFactions = parseFactions("npc_factions");
        hostileNpcs = parseNpcDefinitions("hostile_npcs");
        regions = parseMapMap("regions");

        @SuppressWarnings("unchecked")
        Map<String, Object> phasesRaw = (Map<String, Object>) raw.get("phases");
        phases = phasesRaw != null ? phasesRaw : Map.of();

        @SuppressWarnings("unchecked")
        Map<String, Object> techRaw = (Map<String, Object>) raw.get("tech_tree");
        techTree = techRaw != null ? techRaw : Map.of();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> eventsRaw = (List<Map<String, Object>>) raw.get("small_events");
        smallEvents = eventsRaw != null ? eventsRaw : List.of();

        aiSystem = parseMap("ai_system");
        foreignPowers = parseMap("foreign_powers");
        statsSystem = parseMap("stats_system");

        // 加载 resolutions.json
        try {
            ClassPathResource resFile = new ClassPathResource("data/resolutions.json");
            try (InputStream ris = resFile.getInputStream()) {
                resolutions = mapper.readValue(ris, new TypeReference<Map<String, Object>>() {});
            }
        } catch (IOException e) {
            resolutions = Map.of();
        }
    }

    private Map<String, FactionDefinition> parseFactions(String key) {
        Map<String, FactionDefinition> result = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> section = (Map<String, Object>) raw.get(key);
        if (section != null) {
            for (var entry : section.entrySet()) {
                FactionDefinition f = mapper.convertValue(entry.getValue(), FactionDefinition.class);
                if (f.getId() == null) f.setId(entry.getKey());
                result.put(entry.getKey(), f);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> parseMapMap(String key) {
        Map<String, Object> val = (Map<String, Object>) raw.get(key);
        if (val == null) return Map.of();
        return (Map<String, Map<String, Object>>) (Map<?, ?>) val;
    }

    private Map<String, NpcDefinition> parseNpcDefinitions(String key) {
        Map<String, NpcDefinition> result = new LinkedHashMap<>();
        Map<String, Object> section = (Map<String, Object>) raw.get(key);
        if (section != null) {
            for (var entry : section.entrySet()) {
                NpcDefinition npc = mapper.convertValue(entry.getValue(), NpcDefinition.class);
                npc.setId(entry.getKey());
                result.put(entry.getKey(), npc);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String key) {
        Map<String, Object> val = (Map<String, Object>) raw.get(key);
        return val != null ? val : Map.of();
    }

    // ── 公共访问方法 ──

    public Map<String, FactionDefinition> getFactions() { return factions; }
    public Map<String, FactionDefinition> getNpcFactions() { return npcFactions; }
    public Map<String, NpcDefinition> getHostileNpcs() { return hostileNpcs; }
    public Map<String, Map<String, Object>> getRegions() { return regions; }
    public Map<String, Object> getPhases() { return phases; }
    public Map<String, Object> getTechTree() { return techTree; }
    public List<Map<String, Object>> getSmallEvents() { return smallEvents; }
    public Map<String, Object> getAiSystem() { return aiSystem; }
    public Map<String, Object> getForeignPowers() { return foreignPowers; }
    public Map<String, Object> getStatsSystem() { return statsSystem; }
    public Map<String, Object> getResolutions() { return resolutions; }
    public Map<String, Object> getRaw() { return raw; }

    /** 根据ID获取势力定义（先查可玩势力，再查NPC） */
    public Optional<FactionDefinition> getFaction(String id) {
        FactionDefinition f = factions.get(id);
        if (f != null) return Optional.of(f);
        return Optional.ofNullable(npcFactions.get(id));
    }

    /** 获取势力名称→ID映射 */
    public Map<String, String> getNameToIdMap() {
        Map<String, String> map = new LinkedHashMap<>();
        factions.forEach((id, f) -> map.put(f.getName(), id));
        npcFactions.forEach((id, f) -> map.put(f.getName(), id));
        return map;
    }
}
