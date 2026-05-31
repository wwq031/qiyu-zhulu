package com.qiyuzhulu.controller;

import com.qiyuzhulu.model.FactionDefinition;
import com.qiyuzhulu.repo.GameDataRepo;
import com.qiyuzhulu.repo.MapDataRepo;
import com.qiyuzhulu.repo.SaveRepo;
import com.qiyuzhulu.service.AiProviderService;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据查询API — /api/factions, /api/regions, /api/stats/*。
 */
@RestController
@RequestMapping("/api")
public class DataController {

    private final GameDataRepo gameData;
    private final MapDataRepo mapData;
    private final SaveRepo saveRepo;
    private final AiProviderService aiProvider;

    public DataController(GameDataRepo gameData, MapDataRepo mapData, SaveRepo saveRepo,
                           AiProviderService aiProvider) {
        this.gameData = gameData;
        this.mapData = mapData;
        this.saveRepo = saveRepo;
        this.aiProvider = aiProvider;
    }

    /** GET /api/saves — 列出存档 */
    @GetMapping("/saves")
    public Map<String, Object> listSaves() {
        try {
            return Map.of("saves", saveRepo.listSaves());
        } catch (Exception e) {
            return Map.of("saves", List.of());
        }
    }

    /** GET /api/factions — 返回28可玩势力 */
    @GetMapping("/factions")
    public Map<String, Object> getFactions() {
        Map<String, FactionDefinition> factions = gameData.getFactions();
        List<Map<String, Object>> list = new ArrayList<>();
        for (var entry : factions.entrySet()) {
            FactionDefinition f = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", f.getId());
            item.put("name", f.getName());
            item.put("ideology", f.getIdeology());
            item.put("region", f.getRegion());
            item.put("region_name", getRegionName(f.getRegion()));
            item.put("evolution", f.getEvolution());
            item.put("stats", f.getStats());
            item.put("leader", f.getLeader());
            item.put("lore", f.getLore());
            item.put("diplomacy", f.getDiplomacy());
            item.put("warfare", f.getWarfare());
            item.put("special_units", f.getSpecialUnits());
            item.put("domestic_policy", f.getDomesticPolicy());
            item.put("social_system", f.getSocialSystem());
            item.put("ai", f.getAi());
            item.put("initial_territory", f.getInitialTerritory());
            item.put("initial_forces", f.getInitialForces());
            item.put("color", f.getColor());
            item.put("national_spirit", f.getNationalSpirit());
            list.add(item);
        }
        return Map.of("factions", list);
    }

    /** GET /api/regions — 返回7大区信息 */
    @GetMapping("/regions")
    public Map<String, Object> getRegions() {
        Map<String, Map<String, Object>> regions = gameData.getRegions();
        List<Map<String, Object>> list = new ArrayList<>();
        for (var entry : regions.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>(entry.getValue());
            item.put("id", entry.getKey());
            // 统计该区域的势力数
            long factionCount = gameData.getFactions().values().stream()
                    .filter(f -> entry.getKey().equals(f.getRegion())).count();
            item.put("faction_count", (int) factionCount);
            list.add(item);
        }
        return Map.of("regions", list);
    }

    /** GET /api/config — AI配置 */
    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        return aiProvider.getConfig();
    }

    /** POST /api/config — 保存AI配置 */
    @PostMapping("/config")
    public Map<String, Object> saveConfig(@RequestBody Map<String, Object> body) {
        Map<String, Object> cfg = aiProvider.setConfig(body);
        cfg.put("message", "配置已保存（本次会话有效）");
        return cfg;
    }

    /** GET /api/config/check — 测试AI供应商连接 */
    @GetMapping("/config/check")
    public Map<String, Object> checkConfig() {
        return aiProvider.checkConnection();
    }

    private String getRegionName(String regionId) {
        return switch (regionId) {
            case "northeast" -> "东北";
            case "huabei" -> "华北";
            case "southeast" -> "东南";
            case "southwest" -> "西南";
            case "lingnan" -> "岭南";
            case "nanyang" -> "南洋";
            case "xibei" -> "西北";
            default -> regionId;
        };
    }
}
