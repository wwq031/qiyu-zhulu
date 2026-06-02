package com.qiyuzhulu.controller;

import com.qiyuzhulu.model.*;
import com.qiyuzhulu.repo.MapDataRepo;
import com.qiyuzhulu.service.GameEngine;
import com.qiyuzhulu.service.GameUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 地图API — /api/map, /api/map/reachable, move, attack, faction-info, province-detail。
 */
@RestController
@RequestMapping("/api/map")
public class MapController {

    private final GameEngine engine;
    private final MapDataRepo mapData;
    private final StateController stateCtrl;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private static final Map<String, String> REGION_NAMES = Map.of(
            "northeast","东北","huabei","华北","southeast","东南","southwest","西南",
            "lingnan","岭南","nanyang","南洋","xibei","西北","central","中枢");
    /** NPC颜色调色板 */
    private static final String[] NPC_COLORS = {
        "#a855f7","#13eb6e","#39c556","#1da447","#39c5af",
        "#e67e22","#e74c3c","#9b59b6","#3498db","#1abc9c",
        "#f39c12","#c0392b","#8e44ad","#2980b9","#16a085",
        "#d35400","#7f8c8d","#2ecc71","#e91e63","#00bcd4",
        "#ff5722","#795548","#607d8b","#ff9800","#4caf50",
        "#2196f3","#9c27b0","#f44336","#009688","#673ab7"
    };
    /** 根据名字hash取NPC颜色 */
    private static String npcColor(String name) {
        int h = name.hashCode();
        return NPC_COLORS[Math.abs(h) % NPC_COLORS.length];
    }

    private static final Map<String, String> REGION_TERRAIN = Map.of(
            "northeast","平原·森林","huabei","平原·山地","southeast","水网·丘陵",
            "southwest","山地·高原","lingnan","丘陵·海岸","nanyang","海洋·岛屿",
            "xibei","高原·沙漠","central","平原·交通枢纽");

    public MapController(GameEngine engine, MapDataRepo mapData, StateController stateCtrl) {
        this.engine = engine;
        this.mapData = mapData;
        this.stateCtrl = stateCtrl;
    }

    /** GET /api/map — 完整地图数据 */
    @GetMapping
    public Map<String, Object> getMap(@RequestParam(defaultValue = "0") String spectator) {
        Map<String, Province> provinces = mapData.getAll();
        Map<String, List<Map<String, Object>>> regional = new LinkedHashMap<>();

        for (var entry : provinces.entrySet()) {
            String pid = entry.getKey();
            Province p = entry.getValue();
            String rid = p.getRegion() != null ? p.getRegion() : "central";

            List<List<Object>> connList = new ArrayList<>();
            if (p.getConnections() != null) {
                for (var ce : p.getConnections().entrySet()) {
                    connList.add(List.of(ce.getKey(), ce.getValue()));
                }
            }

            Map<String, Object> pd = new LinkedHashMap<>();
            pd.put("id", pid);
            pd.put("name", p.getName());
            pd.put("type", p.getType());
            pd.put("claimable", p.isClaimable());
            pd.put("terrain", p.getTerrain());
            pd.put("lat", p.getLat());
            pd.put("lng", p.getLng());
            pd.put("connections", connList);
            pd.put("district", p.getDistrict());
            pd.put("industry", p.getIndustry());
            pd.put("agriculture", p.getAgriculture());
            pd.put("commerce", p.getCommerce());
            pd.put("railway", p.getRailway());
            pd.put("port", p.getPort());
            pd.put("population", p.getPopulation());
            pd.put("resources", p.getResources());
            regional.computeIfAbsent(rid, k -> new ArrayList<>()).add(pd);
        }

        // 组装区域
        List<Map<String, Object>> regionList = new ArrayList<>();
        for (String rid : GameEngine.REGION_IDS) {
            List<Map<String, Object>> rp = regional.get(rid);
            if (rp == null || rp.isEmpty()) continue;
            Map<String, Long> typeCounts = rp.stream()
                    .collect(Collectors.groupingBy(p -> (String) p.get("type"), Collectors.counting()));
            regionList.add(Map.of(
                    "id", rid,
                    "name", REGION_NAMES.getOrDefault(rid, rid),
                    "terrain", REGION_TERRAIN.getOrDefault(rid, ""),
                    "province_count", rp.size(),
                    "type_counts", typeCounts,
                    "provinces", rp));
        }
        // 中枢
        List<Map<String, Object>> central = regional.get("central");
        if (central != null && !central.isEmpty()) {
            regionList.add(Map.of("id","central","name","中枢","terrain","平原·交通枢纽",
                    "province_count", central.size(), "type_counts", Map.of(), "provinces", central));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_provinces", provinces.size());
        result.put("regions", regionList);
        result.put("cross_region_routes", mapData.getCrossRegionGates());

        // 名称→PID映射（多处使用）
        Map<String, String> nameToPid = new HashMap<>();
        for (var e : provinces.entrySet()) {
            nameToPid.put(e.getValue().getName(), e.getKey());
        }

        // 所有权
        GameState game = stateCtrl.getGame();
        boolean isEmpirePhase = game != null && game.getPhase() == 1;
        if (game != null && !"1".equals(spectator)) {
            Map<String, Object> ownership = new LinkedHashMap<>();

            // 玩家
            FactionState pfs = game.getFactionState();
            List<String> playerPids = new ArrayList<>();
            for (String t : pfs.getTerritories()) {
                String pid = nameToPid.get(t);
                if (pid != null) playerPids.add(pid);
            }
            FactionDefinition pf = engine.getFaction(game.getPlayerFactionId()).orElse(null);
            ownership.put("player", Map.of(
                    "faction_id", game.getPlayerFactionId(),
                    "name", pfs.getName(), // Phase1可能改名（大清帝国）
                    "region", pf != null ? pf.getRegion() : "",
                    "color", pf != null ? pf.getColor() : "#ffffff",
                    "territory_pids", playerPids));

            // AI（帝国阶段跳过）
            List<Map<String, Object>> aiList = new ArrayList<>();
            if (!isEmpirePhase)
            for (var ae : game.getAiFactions().entrySet()) {
                if (game.getDefeatedFactions().contains(ae.getKey())) continue;
                FactionState afs = ae.getValue().getFactionState();
                if (afs == null || afs.getTerritories() == null || afs.getTerritories().isEmpty()) continue;
                List<String> aiPids = new ArrayList<>();
                for (String t : afs.getTerritories()) {
                    String pid = nameToPid.get(t);
                    if (pid != null) aiPids.add(pid);
                }
                if (!aiPids.isEmpty()) {
                    FactionDefinition af = engine.getFaction(ae.getKey()).orElse(null);
                    aiList.add(Map.of(
                            "faction_id", ae.getKey(),
                            "name", af != null ? af.getName() : afs.getName(),
                            "region", af != null ? af.getRegion() : "",
                            "color", af != null ? af.getColor() : "#888",
                            "territory_pids", aiPids));
                }
            }
            ownership.put("ai", aiList);

            // NPC势力也加入ownership（友好NPC + hostile NPC）
            // NPC到ownership
            Map<String, NpcDefinition> allNpcs = engine.getGameData().getHostileNpcs();
            if (allNpcs != null) {
                for (var ne : allNpcs.entrySet()) {
                    if (game.getDefeatedFactions().contains(ne.getKey())) continue;
                    NpcDefinition ndata = ne.getValue();
                    if (ndata.getTerritories() == null) continue;
                    List<String> npcPids = new ArrayList<>();
                    for (String t : ndata.getTerritories()) {
                        String pid = nameToPid.get(t);
                        if (pid != null) npcPids.add(pid);
                    }
                    if (!npcPids.isEmpty()) {
                        String nColor = ndata.getColor() != null ? ndata.getColor() : npcColor(ndata.getName());
                        aiList.add(Map.of(
                                "faction_id", ne.getKey(), "name", ndata.getName(),
                                "region", ndata.getRegion() != null ? ndata.getRegion() : "",
                                "color", nColor, "territory_pids", npcPids));
                    }
                }
            }
            result.put("ownership", ownership);

            // 驻军 — key 用 PID（非中文名，前端用 PID 索引 mapProvinceData）
            Map<String, List<Map<String, Object>>> garrisons = new LinkedHashMap<>();
            // 玩家部队
            for (Unit u : pfs.getUnits()) {
                if (u.getPosition() != null) {
                    String posPid = engine.resolvePositionToPid(u.getPosition());
                    garrisons.computeIfAbsent(posPid, k -> new ArrayList<>())
                            .add(u.toMap(true, pfs.getName(), pfs.getUnits().indexOf(u)));
                }
            }
            // AI势力部队（帝国阶段跳过）
            if (!isEmpirePhase) for (var ae : game.getAiFactions().entrySet()) {
                if (game.getDefeatedFactions().contains(ae.getKey())) continue;
                FactionState afs = ae.getValue().getFactionState();
                if (afs == null || afs.getUnits() == null) continue;
                for (Unit u : afs.getUnits()) {
                    if (!u.isActive()) continue;
                    if (u.getPosition() != null) {
                        String posPid = engine.resolvePositionToPid(u.getPosition());
                        garrisons.computeIfAbsent(posPid, k -> new ArrayList<>())
                                .add(u.toMap(false, afs.getName(), afs.getUnits().indexOf(u)));
                    }
                }
            }
            // NPC驻军（帝国阶段跳过）
            if (!isEmpirePhase) {
            Map<String, NpcDefinition> npcDefs = engine.getGameData().getHostileNpcs();
            if (npcDefs != null) {
                for (var ne : npcDefs.entrySet()) {
                    if (game.getDefeatedFactions().contains(ne.getKey())) continue;
                    NpcDefinition ndef = ne.getValue();
                    List<String> terrs = ndef.getTerritories();
                    if (terrs == null || terrs.isEmpty()) continue;
                    String capPid = nameToPid.get(terrs.get(0));
                    if (capPid == null) continue;
                    List<Map<String, Object>> npcUnits = new ArrayList<>();
                    List<String> fnames = ndef.getForceNames();
                    if (fnames != null && !fnames.isEmpty()) {
                        for (int i = 0; i < Math.min(fnames.size(), 2); i++) {
                            npcUnits.add(Map.of(
                                    "name", fnames.get(i), "type", engine.inferUnitType(fnames.get(i)),
                                    "attack", 7, "defense", 5, "morale", 45, "strength", 70,
                                    "index", i, "is_player", false, "faction_name", ndef.getName()));
                        }
                    } else {
                        String forceDesc = ndef.getForces() != null ? ndef.getForces() : "守备队";
                        String[] parts = forceDesc.split("[+、,，]");
                        int showCount = Math.min(parts.length, 2);
                        for (int i = 0; i < showCount; i++) {
                            String fname = parts[i].trim();
                            if (fname.length() > 20) fname = fname.substring(0, 20);
                            npcUnits.add(Map.of(
                                    "name", fname, "type", engine.inferUnitType(fname),
                                    "attack", 7, "defense", 5, "morale", 45, "strength", 70,
                                    "index", i, "is_player", false, "faction_name", ndef.getName()));
                        }
                    }
                    garrisons.computeIfAbsent(capPid, k -> new ArrayList<>()).addAll(npcUnits);
                }
            }
            } // if (!isEmpirePhase)
            result.put("garrisons", garrisons);
        }

        // ── 构建 pid→owner 查找表 ──
        Map<String, Map<String, Object>> pidOwner = new LinkedHashMap<>();
        Map<String, String> pidOwnerName = new LinkedHashMap<>(); // pid → faction_name (for garrison check)
        if (game != null && !"1".equals(spectator)) {
            // 玩家
            FactionState pfs = game.getFactionState();
            FactionDefinition pf = engine.getFaction(game.getPlayerFactionId()).orElse(null);
            String pColor = pf != null ? pf.getColor() : "#ffffff";
            String pName = pfs.getName(); // 优先FactionState名（Phase1可改）
            for (String t : pfs.getTerritories()) {
                String pid = nameToPid.get(t);
                if (pid != null) {
                    pidOwner.put(pid, GameUtils.mapOf("owner_name", pName, "owner_color", pColor, "is_player", true));
                    pidOwnerName.put(pid, pfs.getName());
                }
            }
            // AI势力 — 动态state优先，静态game_data回退
            Map<String, FactionDefinition> allFactions = engine.getGameData().getFactions();
            if (allFactions != null) {
                for (var fe : allFactions.entrySet()) {
                    String fid = fe.getKey();
                    if (fid.equals(game.getPlayerFactionId())) continue;
                    if (game.getDefeatedFactions().contains(fid)) continue;
                    FactionDefinition fdef = fe.getValue();

                    // 优先动态territories
                    List<String> aiTerrs = null;
                    String aName = fdef.getName();
                    String aColor = fdef.getColor() != null ? fdef.getColor() : "#888";
                    var aiData = game.getAiFactions().get(fid);
                    if (aiData != null) {
                        FactionState afs = aiData.getFactionState();
                        if (afs != null && afs.getTerritories() != null && !afs.getTerritories().isEmpty()) {
                            aiTerrs = afs.getTerritories();
                            if (afs.getName() != null) aName = afs.getName();
                        }
                    }
                    // 回退：静态initial_territory
                    if (aiTerrs == null) aiTerrs = fdef.getInitialTerritory();
                    if (aiTerrs == null || aiTerrs.isEmpty()) continue;

                    for (String t : aiTerrs) {
                        String pid = nameToPid.get(t);
                        if (pid != null && !pidOwner.containsKey(pid)) {
                            pidOwner.put(pid, GameUtils.mapOf("owner_name", aName, "owner_color", aColor, "is_player", false));
                            pidOwnerName.put(pid, aName);
                        }
                    }
                }
            }
            // NPC领土（合并后的hostile_npcs，含完整领土+颜色）
            Map<String, NpcDefinition> npcs = engine.getGameData().getHostileNpcs();
            if (npcs != null) {
                for (var ne : npcs.entrySet()) {
                    if (game.getDefeatedFactions().contains(ne.getKey())) continue;
                    NpcDefinition ndata = ne.getValue();
                    if (ndata.getTerritories() == null) continue;
                    String nColor = ndata.getColor() != null ? ndata.getColor() : npcColor(ndata.getName());
                    for (String t : ndata.getTerritories()) {
                        String pid = nameToPid.get(t);
                        if (pid != null && !pidOwner.containsKey(pid)) {
                            pidOwner.put(pid, GameUtils.mapOf("owner_name", ndata.getName(), "owner_color", nColor, "is_player", false));
                            pidOwnerName.put(pid, ndata.getName());
                        }
                    }
                }
            }
        }

        // ── 加载 city_store_static.json（预建353城，含parent_city继承关系）──
        List<Map<String, Object>> staticStore = loadCityStoreStatic();

        // ── 合并动态所有权到静态city_store ──
        List<Map<String, Object>> cityStore = new ArrayList<>();
        Map<String, Object> capitals = new LinkedHashMap<>();

        // 先收集所有势力首都信息
        if (game != null) {
            FactionState pfs = game.getFactionState();
            String playerCap = pfs.getCapital();
            if (playerCap != null && !playerCap.isEmpty()) {
                String capPid = nameToPid.get(playerCap);
                if (capPid != null)
                    capitals.put(game.getPlayerFactionId(), Map.of("pid", capPid, "name", playerCap, "is_player", true));
            }
            for (var ae : game.getAiFactions().entrySet()) {
                FactionState afs = ae.getValue().getFactionState();
                String cap = (afs != null && afs.getCapital() != null && !afs.getCapital().isEmpty())
                        ? afs.getCapital() : null;
                if (cap == null) {
                    // 回退：静态initial_territory的第一个城市
                    FactionDefinition af = engine.getFaction(ae.getKey()).orElse(null);
                    if (af != null && af.getInitialTerritory() != null && !af.getInitialTerritory().isEmpty())
                        cap = af.getInitialTerritory().get(0);
                }
                if (cap != null) {
                    String capPid = nameToPid.get(cap);
                    if (capPid != null)
                        capitals.put(ae.getKey(), Map.of("pid", capPid, "name", cap, "is_player", false));
                }
            }
            // NPC capitals — 动态：跳过已被占领的领土，取第一个剩余领土
            Set<String> allOccupiedNames = new HashSet<>();
            allOccupiedNames.addAll(pfs.getTerritories());
            for (var ae : game.getAiFactions().entrySet()) {
                FactionState afs = ae.getValue().getFactionState();
                if (afs != null && afs.getTerritories() != null)
                    allOccupiedNames.addAll(afs.getTerritories());
            }
            Map<String, NpcDefinition> npcs = engine.getGameData().getHostileNpcs();
            if (npcs != null) {
                for (var ne : npcs.entrySet()) {
                    if (game.getDefeatedFactions().contains(ne.getKey())) continue;
                    NpcDefinition ndata = ne.getValue();
                    if (ndata.getTerritories() == null || ndata.getTerritories().isEmpty()) continue;
                    // 动态首都：跳过已被占领的，取第一个剩余领土
                    String cap = null;
                    for (String t : ndata.getTerritories()) {
                        if (!allOccupiedNames.contains(t)) { cap = t; break; }
                    }
                    if (cap == null) continue; // 全部被占
                    String capPid = nameToPid.get(cap);
                    if (capPid != null)
                        capitals.put(ne.getKey(), Map.of("pid", capPid, "name", cap, "is_player", false));
                }
            }
            // 28势力 capitals（从game_data静态数据回退）
            Map<String, FactionDefinition> allFactions = engine.getGameData().getFactions();
            if (allFactions != null) {
                for (var fe : allFactions.entrySet()) {
                    String fid = fe.getKey();
                    if (capitals.containsKey(fid)) continue;
                    FactionDefinition fdef = fe.getValue();
                    if (fdef.getInitialTerritory() != null && !fdef.getInitialTerritory().isEmpty()) {
                        String cap = fdef.getInitialTerritory().get(0);
                        String capPid = nameToPid.get(cap);
                        if (capPid != null)
                            capitals.put(fid, Map.of("pid", capPid, "name", cap, "is_player", false));
                    }
                }
            }
        }

        for (Map<String, Object> city : staticStore) {
            String cname = (String) city.get("name");
            String cpid = (String) city.get("pid");
            String parent = (String) city.get("parent_city");

            // 确定所有权
            Map<String, Object> owner = null;
            // 1) 直接PID匹配
            if (cpid != null && pidOwner.containsKey(cpid)) {
                owner = pidOwner.get(cpid);
            }
            // 2) 城市自身名字匹配（port/pass/rural等地块）
            if (owner == null) {
                String ownPid = nameToPid.get(cname);
                if (ownPid != null && pidOwner.containsKey(ownPid)) {
                    owner = pidOwner.get(ownPid);
                }
            }
            // 3) 父城继承：属地城市继承parent_city的归属
            if (owner == null && parent != null) {
                String parentPid = nameToPid.get(parent);
                if (parentPid != null && pidOwner.containsKey(parentPid)) {
                    owner = pidOwner.get(parentPid);
                }
            }

            Map<String, Object> cs = new LinkedHashMap<>();
            cs.put("name", cname);
            cs.put("lat", city.getOrDefault("lat", 0));
            cs.put("lng", city.getOrDefault("lng", 0));
            cs.put("region", city.getOrDefault("region", ""));
            cs.put("type", city.getOrDefault("type", "city"));
            cs.put("pid", cpid != null ? cpid : "");
            cs.put("district", city.getOrDefault("district", ""));
            if (parent != null) cs.put("parent_city", parent);
            if (owner != null) {
                cs.put("owner_name", owner.get("owner_name"));
                cs.put("owner_color", owner.get("owner_color"));
                cs.put("is_player", owner.get("is_player"));
            }
            cityStore.add(cs);
        }

        result.put("city_store", cityStore);
        result.put("capitals", capitals);
        result.put("faction_boundaries", Map.of("type", "FeatureCollection", "features", List.of()));

        // 进行中战役（前端战斗标记用）
        List<Map<String, Object>> activeCampaigns = new ArrayList<>();
        if (game != null) {
            for (Campaign c : game.getActiveCampaigns()) {
                if ("ongoing".equals(c.getStatus())) {
                    Map<String, Object> ci = new LinkedHashMap<>();
                    ci.put("id", c.getId());
                    ci.put("province", c.getProvince());
                    ci.put("province_name", c.getProvinceName());
                    ci.put("round", c.getRound());
                    ci.put("max_rounds", c.getMaxRounds());
                    ci.put("attacker_name", c.getAttackerName());
                    ci.put("defender_name", c.getDefenderName());
                    ci.put("is_player_attacker", game.getPlayerFactionId().equals(c.getAttackerFaction()));
                    ci.put("is_player_defender", game.getPlayerFactionId().equals(c.getDefenderFaction()));
                    activeCampaigns.add(ci);
                }
            }
        }
        result.put("active_campaigns", activeCampaigns);

        return result;
    }

    /** GET /api/map/province-detail?pid=X */
    @GetMapping("/province-detail")
    public Map<String, Object> provinceDetail(@RequestParam String pid) {
        Province p = engine.getProvince(pid);
        if (p == null) return Map.of("error", "省份不存在");

        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", pid); d.put("name", p.getName()); d.put("type", p.getType());
        d.put("terrain", p.getTerrain()); d.put("desc", p.getDesc()); d.put("district", p.getDistrict());
        d.put("industry", p.getIndustry()); d.put("agriculture", p.getAgriculture());
        d.put("commerce", p.getCommerce()); d.put("railway", p.getRailway());
        d.put("port", p.getPort()); d.put("population", p.getPopulation());
        d.put("resources", p.getResources());
        d.put("connections", p.getConnections());

        GameState game = stateCtrl.getGame();
        if (game != null) {
            FactionState fs = game.getFactionState();
            d.put("buildings", fs.getProvinceBuildings().getOrDefault(pid, Map.of()));
            d.put("is_owned_by_player", fs.getTerritories().contains(p.getName()));
            d.put("owner_faction_name", fs.getTerritories().contains(p.getName()) ? fs.getName() : "中立");
        }
        return d;
    }

    /** GET /api/map/faction-info?pid=X */
    @GetMapping("/faction-info")
    public Map<String, Object> factionInfo(@RequestParam String pid) {
        Province p = engine.getProvince(pid);
        if (p == null) return Map.of("error", "省份不存在");

        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无游戏");

        // 查找该省份的拥有者
        FactionState fs = game.getFactionState();
        if (fs.getTerritories().contains(p.getName())) {
            return buildFactionInfo(game.getPlayerFactionId(), fs, game);
        }
        for (var ae : game.getAiFactions().entrySet()) {
            FactionState afs = ae.getValue().getFactionState();
            if (afs != null && afs.getTerritories() != null && afs.getTerritories().contains(p.getName())) {
                return buildFactionInfo(ae.getKey(), afs, game);
            }
        }
        // 回退：静态faction数据
        for (var fe : engine.getGameData().getFactions().entrySet()) {
            if (fe.getValue().getInitialTerritory().contains(p.getName())) {
                return buildStaticFactionInfo(fe.getKey(), fe.getValue(), game);
            }
        }
        // 回退：NPC
        for (var ne : engine.getGameData().getHostileNpcs().entrySet()) {
            if (ne.getValue().getTerritories() != null && ne.getValue().getTerritories().contains(p.getName())) {
                return buildNpcInfo(ne.getKey(), ne.getValue(), game);
            }
        }
        return Map.of("error", "无主之地");
    }

    /** GET /api/spectator/map — 旁观模式地图（无游戏状态时使用） */
    @GetMapping("/spectator/map")
    public Map<String, Object> spectatorMap() {
        return getMap("1");
    }

    private Map<String, Object> buildStaticFactionInfo(String fid, FactionDefinition fdef, GameState game) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("faction_id", fid);
        info.put("is_player", fid.equals(game.getPlayerFactionId()));
        info.put("name", fdef.getName());
        info.put("stats", fdef.getStats());
        info.put("territories", fdef.getInitialTerritory());
        info.put("unit_count", fdef.getInitialForces().size());
        info.put("total_strength", fdef.getInitialForces().size() * 60);
        info.put("territory_count", fdef.getInitialTerritory().size());
        return info;
    }

    private Map<String, Object> buildNpcInfo(String nid, NpcDefinition ndata, GameState game) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("faction_id", nid);
        info.put("is_player", false);
        info.put("name", ndata.getName());
        info.put("stats", ndata.getStats());
        info.put("territories", ndata.getTerritories());
        info.put("unit_count", 2);
        info.put("total_strength", 120);
        info.put("territory_count", ndata.getTerritories() != null ? ndata.getTerritories().size() : 0);
        return info;
    }

    private Map<String, Object> buildFactionInfo(String fid, FactionState fs, GameState game) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("faction_id", fid);
        info.put("is_player", fid.equals(game.getPlayerFactionId()));
        info.put("name", fs.getName());
        info.put("stats", fs.getStats());
        info.put("territories", fs.getTerritories());
        info.put("unit_count", fs.getUnits().size());
        info.put("total_strength", fs.getUnits().stream().mapToInt(Unit::getStrength).sum());
        info.put("territory_count", fs.getTerritories().size());
        info.put("territory_economy", engine.aggregateTerritoryEconomy(fs));
        return info;
    }

    /** 从 classpath 加载 city_store_static.json（预建353城） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadCityStoreStatic() {
        try {
            var resource = new org.springframework.core.io.ClassPathResource("static/vendor/city_store_static.json");
            return mapper.readValue(resource.getInputStream(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

}
