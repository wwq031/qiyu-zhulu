package com.qiyuzhulu.service;

import com.qiyuzhulu.model.*;
import com.qiyuzhulu.repo.GameDataRepo;
import com.qiyuzhulu.repo.MapDataRepo;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 核心游戏引擎。对应 Python 的 qiyu_core.py。
 */
@Service
public class GameEngine {

    private final GameDataRepo gameData;
    private final MapDataRepo mapData;

    /** 区域ID列表（有序） */
    public static final List<String> REGION_IDS = List.of(
            "northeast", "huabei", "southwest", "southeast", "lingnan", "nanyang", "xibei");

    /** 属性中文名 */
    public static final Map<String, String> STAT_NAMES = Map.of(
            "industry", "工业", "agriculture", "农业", "military", "军事",
            "economy", "经济", "ideology", "思想", "diplomacy", "外交",
            "naval_power", "海军");

    /** 属性emoji */
    public static final Map<String, String> STAT_EMOJI = Map.of(
            "industry", "🏭", "agriculture", "🌾", "military", "⚔",
            "economy", "💰", "ideology", "📖", "diplomacy", "🌐",
            "naval_power", "⚓");

    /** 兵种定义 */
    public static final Map<String, Map<String, Object>> UNIT_TYPES = new LinkedHashMap<>();
    static {
        UNIT_TYPES.put("infantry",  Map.of("name","步兵","icon","🗡","atk_bonus",3,"def_bonus",3,"cost",8,"turns",2,"military_gain",5,"maintenance_cost",2,"suffix","号"));
        UNIT_TYPES.put("cavalry",   Map.of("name","骑兵","icon","🐎","atk_bonus",6,"def_bonus",1,"cost",12,"turns",3,"military_gain",7,"maintenance_cost",3,"suffix","号"));
        UNIT_TYPES.put("artillery", Map.of("name","炮兵","icon","💣","atk_bonus",9,"def_bonus",1,"cost",18,"turns",4,"military_gain",10,"maintenance_cost",4,"suffix","号"));
        UNIT_TYPES.put("engineer",  Map.of("name","工兵","icon","🔧","atk_bonus",2,"def_bonus",6,"cost",10,"turns",3,"military_gain",6,"maintenance_cost",3,"suffix","号"));
        UNIT_TYPES.put("naval",     Map.of("name","海军舰艇","icon","⚓","atk_bonus",8,"def_bonus",4,"cost",20,"turns",5,"military_gain",10,"naval_gain",8,"maintenance_cost",5,"suffix","舰艇"));
    }

    /** 战术定义 */
    public static final Map<String, Map<String, Object>> TACTICS = new LinkedHashMap<>();
    static {
        TACTICS.put("assault",    Map.of("name","强攻","icon","⚔","atk_mult",1.4,"def_mult",0.6,"loss_mult",1.5));
        TACTICS.put("flanking",   Map.of("name","迂回","icon","🏃","atk_mult",1.1,"def_mult",0.8,"loss_mult",0.9));
        TACTICS.put("bombard",    Map.of("name","炮击","icon","💣","atk_mult",0.6,"def_mult",0.3,"loss_mult",0.3));
        TACTICS.put("ambush",     Map.of("name","设伏","icon","🌲","atk_mult",1.8,"def_mult",1.5,"loss_mult",0.5));
        TACTICS.put("fortify",    Map.of("name","设防","icon","🏰","atk_mult",0.4,"def_mult",2.0,"loss_mult",0.7));
        TACTICS.put("night_raid", Map.of("name","夜袭","icon","🌙","atk_mult",1.3,"def_mult",0.5,"loss_mult",1.1));
        TACTICS.put("probe",      Map.of("name","试探","icon","🔍","atk_mult",0.5,"def_mult",1.2,"loss_mult",0.4));
        TACTICS.put("all_out",    Map.of("name","总攻","icon","🔥","atk_mult",2.0,"def_mult",0.0,"loss_mult",2.5));
    }

    public static final Map<String, String> REGION_NAMES = Map.of(
            "northeast","东北","huabei","华北","southeast","东南","southwest","西南",
            "lingnan","岭南","nanyang","南洋","xibei","西北");

    public static final Map<Integer, String> PHASE_NAMES = Map.of(
            1,"帝国余晖",2,"帝国大崩溃",3,"区域统一战",4,"七强并立",5,"天下归一");

    public GameEngine(GameDataRepo gameData, MapDataRepo mapData) {
        this.gameData = gameData;
        this.mapData = mapData;
    }

    public MapDataRepo getMapData() { return mapData; }
    public GameDataRepo getGameData() { return gameData; }

    // ═══════════════════════════════════════════ 工具方法 ═══════════════════════════════════════════

    /** 限定值在 [lo, hi] 范围内 */
    public static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    public static int clamp(int v) { return clamp(v, 0, 100); }

    /** 获取省份数据 */
    public Province getProvince(String pid) {
        return mapData.get(pid);
    }

    /** 根据省份名获取PID */
    public String getPidByName(String name) {
        return mapData.getPidByName(name);
    }

    /** 将部队位置（领土名或PID）统一转为PID */
    public String resolvePositionToPid(String pos) {
        if (pos == null) return "beijing";
        String pid = getPidByName(pos);
        return pid != null ? pid : pos;
    }

    /** 获取势力定义 */
    public Optional<FactionDefinition> getFaction(String fid) {
        return gameData.getFaction(fid);
    }

    /** 获取当前玩家的势力定义 */
    public FactionDefinition getPlayerFaction(GameState state) {
        return getFaction(state.getPlayerFactionId()).orElse(null);
    }

    /** 获取玩家所在区域 */
    public String getPlayerRegion(GameState state) {
        FactionDefinition pf = getPlayerFaction(state);
        return pf != null ? pf.getRegion() : "";
    }

    /** 获取国家精神 */
    public NationalSpirit getNationalSpirit(FactionDefinition faction) {
        if (faction.getNationalSpirit() != null && faction.getNationalSpirit().getName() != null) {
            return faction.getNationalSpirit();
        }
        // 默认按意识形态匹配
        return getDefaultSpirit(faction.getIdeology());
    }

    private NationalSpirit getDefaultSpirit(String ideology) {
        Map<String, NationalSpirit> defaults = new LinkedHashMap<>();
        defaults.put("军阀独裁", ns("强人政治","一人号令，三军听命。",Map.of("military",5,"ideology",-3)));
        defaults.put("共产主义", ns("先锋队革命","彻底的革命，全新的世界。",Map.of("ideology",10,"economy",-5)));
        defaults.put("三民主义共和", ns("三民主义","民族、民权、民生。",Map.of("ideology",5,"economy",3,"diplomacy",2)));
        return defaults.getOrDefault(ideology, new NationalSpirit(){{setName("暂无国魂");setDesc("乱世中尚未形成独特的精神力量。");setEffects(Map.of());}});
    }

    private static NationalSpirit ns(String name, String desc, Map<String,Integer> effects) {
        NationalSpirit s = new NationalSpirit();
        s.setName(name); s.setDesc(desc); s.setEffects(effects);
        return s;
    }

    // ═══════════════════════════════════════════ 地图系统 ═══════════════════════════════════════════

    /** BFS计算两省份最短距离 */
    public Object[] getDistance(String fromPid, String toPid) {
        if (fromPid == null || toPid == null) return new Object[]{null, null};
        if (fromPid.equals(toPid)) return new Object[]{0, List.of(fromPid)};

        Set<String> visited = new HashSet<>();
        visited.add(fromPid);
        Queue<Object[]> queue = new LinkedList<>();
        queue.add(new Object[]{fromPid, new ArrayList<>(List.of(fromPid))});

        while (!queue.isEmpty()) {
            Object[] item = queue.poll();
            String current = (String) item[0];
            @SuppressWarnings("unchecked")
            List<String> path = (List<String>) item[1];
            Province p = getProvince(current);
            if (p == null || p.getConnections() == null) continue;
            for (String neighbor : p.getConnections().keySet()) {
                if (neighbor.equals(toPid)) {
                    List<String> fullPath = new ArrayList<>(path);
                    fullPath.add(neighbor);
                    return new Object[]{fullPath.size() - 1, fullPath};
                }
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(neighbor);
                    queue.add(new Object[]{neighbor, newPath});
                }
            }
        }
        return new Object[]{null, null}; // unreachable
    }

    /** 省份是否有铁路 */
    public boolean hasRailway(String pid) {
        Province p = getProvince(pid);
        return p != null && p.getRailway() > 0;
    }

    // ═══════════════════════════════════════════ 军队系统 ═══════════════════════════════════════════

    /** 获取按位置分组的部队部署 */
    public Map<String, List<Unit>> listArmyPositions(List<Unit> units) {
        Map<String, List<Unit>> result = new LinkedHashMap<>();
        if (units == null) return result;
        for (Unit u : units) {
            String pos = u.getPosition();
            if (pos != null && !pos.isEmpty()) {
                result.computeIfAbsent(pos, k -> new ArrayList<>()).add(u);
            }
        }
        return result;
    }

    /** 汇总领土经济 */
    public Map<String, Object> aggregateTerritoryEconomy(FactionState fs) {
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("industry", 0);
        totals.put("agriculture", 0);
        totals.put("commerce", 0);
        totals.put("railway_provinces", 0);
        totals.put("port_provinces", 0);
        totals.put("population", 0);
        totals.put("resources", new HashMap<String, Integer>());
        totals.put("province_count", 0);

        List<String> territories = fs.getTerritories();
        if (territories == null) return totals;

        Map<String, Map<String, Integer>> buildings = fs.getProvinceBuildings();
        if (buildings == null) buildings = Map.of();

        for (String tname : territories) {
            String pid = getPidByName(tname);
            if (pid == null) continue;
            Province p = getProvince(pid);
            if (p == null) continue;

            int ind = p.getIndustry();
            int agr = p.getAgriculture();
            Map<String, Integer> bld = buildings.getOrDefault(pid, Map.of());
            ind = Math.min(10, ind + bld.getOrDefault("factory", 0) * 2);
            agr = Math.min(10, agr + bld.getOrDefault("irrigation", 0) * 2);

            totals.put("industry", (int) totals.get("industry") + ind);
            totals.put("agriculture", (int) totals.get("agriculture") + agr);
            totals.put("commerce", (int) totals.get("commerce") + p.getCommerce());
            if (p.getRailway() > 0) totals.put("railway_provinces", (int) totals.get("railway_provinces") + 1);
            if (p.getPort() > 0) totals.put("port_provinces", (int) totals.get("port_provinces") + 1);
            totals.put("population", (int) totals.get("population") + p.getPopulation());
            @SuppressWarnings("unchecked")
            Map<String, Integer> resMap = (Map<String, Integer>) totals.get("resources");
            for (String r : p.getResources()) {
                resMap.merge(r, 1, Integer::sum);
            }
            totals.put("province_count", (int) totals.get("province_count") + 1);
        }
        return totals;
    }

    /** 计算城市有效农业（铁路权重输送） */
    public int calcCitySupplyAgriculture(String cityPid, List<String> controlledNames,
                                          Map<String, Map<String, Integer>> buildings) {
        Province cityP = getProvince(cityPid);
        if (cityP == null) return 0;
        if (buildings == null) buildings = Map.of();

        int ownAgri = cityP.getAgriculture() + buildings.getOrDefault(cityPid, Map.of()).getOrDefault("irrigation", 0) * 2;
        ownAgri = Math.min(10, ownAgri);
        Map<Integer, Double> railWeights = Map.of(0, 0.3, 1, 0.6, 2, 0.8, 3, 1.0);
        double adjacentAgri = 0.0;

        if (cityP.getConnections() != null) {
            for (String adjPid : cityP.getConnections().keySet()) {
                Province adjP = getProvince(adjPid);
                if (adjP == null) continue;
                if (!controlledNames.contains(adjP.getName())) continue;
                if (!"rural".equals(adjP.getType())) continue;
                int rw = adjP.getRailway();
                double weight = railWeights.getOrDefault(rw, 0.3);
                int baseAgri = adjP.getAgriculture() + buildings.getOrDefault(adjPid, Map.of()).getOrDefault("irrigation", 0) * 2;
                adjacentAgri += Math.min(10, baseAgri) * weight;
            }
        }
        return (int)(ownAgri + adjacentAgri);
    }

    /** 计算单支部队维持费 */
    public int calcUnitMaintenance(Unit unit, FactionState fs) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ut = (Map<String, Object>) UNIT_TYPES.getOrDefault(unit.getType(), UNIT_TYPES.get("infantry"));
        int base = ut.containsKey("maintenance_cost") ? ((Number) ut.get("maintenance_cost")).intValue() : 2;

        String pos = unit.getPosition();
        String posPid = getPidByName(pos);
        if (posPid == null) return base;

        Province p = getProvince(posPid);
        if (p == null) return base;

        Map<String, Map<String, Integer>> bld = fs.getProvinceBuildings();
        if (bld == null) bld = Map.of();

        if ("rural".equals(p.getType())) {
            int bonusAgri = bld.getOrDefault(posPid, Map.of()).getOrDefault("irrigation", 0) * 2;
            int agri = Math.max(1, Math.min(10, p.getAgriculture() + bonusAgri));
            return Math.max(1, (int) Math.round((double) base / agri));
        } else {
            int effectiveAgri = Math.max(1, calcCitySupplyAgriculture(posPid, fs.getTerritories(), bld));
            return Math.max(1, (int) Math.round((double) base / effectiveAgri));
        }
    }

    /** 计算势力总维持费 */
    public int calcTotalMaintenance(FactionState fs) {
        if (fs.getUnits() == null) return 0;
        return fs.getUnits().stream()
                .filter(Unit::isActive)
                .mapToInt(u -> calcUnitMaintenance(u, fs))
                .sum();
    }

    /** 计算回合收入 */
    public int calcIncome(FactionState fs) {
        Map<String, Object> eco = aggregateTerritoryEconomy(fs);
        int commerce = (int) eco.getOrDefault("commerce", 0);
        int agriculture = (int) eco.getOrDefault("agriculture", 0);
        int industry = (int) eco.getOrDefault("industry", 0);
        int economy = fs.getStats().getEconomy();
        return (int)(commerce * 1.5 + agriculture * 0.8) + economy / 8 + industry / 10;
    }

    // ═══════════════════════════════════════════ 状态初始化 ═══════════════════════════════════════════

    /** 创建新游戏状态 */
    public GameState newState(String factionId, List<String> policies) {
        FactionDefinition faction = gameData.getFaction(factionId)
                .orElseThrow(() -> new IllegalArgumentException("势力不存在: " + factionId));

        GameState state = new GameState();
        state.setVersion(7);
        state.setPhase(2);
        state.setTurn(0);
        state.setGameDate("1910-03");
        state.setPlayerFactionId(factionId);
        state.setPhase1Policies(policies != null ? policies : List.of());
        state.setActionPoints(3);
        state.setApMax(3);

        // 势力状态
        FactionState fs = new FactionState();
        fs.setName(faction.getName());
        fs.setStats(faction.getStats().copy());
        fs.setTreasury(faction.getStats().getEconomy() * 2);
        fs.setPopulationSupport(50);
        fs.setMilitaryTech(1);
        fs.setCapital(faction.getInitialTerritory().isEmpty() ? "" : faction.getInitialTerritory().get(0));
        fs.setTerritories(new ArrayList<>(faction.getInitialTerritory()));
        fs.setForces(new ArrayList<>(faction.getInitialForces()));
        fs.setEvolutionStage(0);
        fs.setUnitSerial(new HashMap<>(Map.of("total", 0)));

        // 生成部队
        List<Unit> units = autoGenerateUnits(faction);
        fs.setUnits(units);
        fs.setArmy(recountArmyFromUnits(units));

        // 番号前缀
        fs.setUnitPrefix(deriveUnitPrefix(faction.getName()));

        state.setFactionState(fs);
        return state;
    }

    /** 从势力初始部队名生成Unit列表 */
    private List<Unit> autoGenerateUnits(FactionDefinition faction) {
        List<Unit> units = new ArrayList<>();
        Map<String, Integer> serial = new HashMap<>();
        serial.put("total", 0);
        serial.put("infantry", 0);
        serial.put("cavalry", 0);
        serial.put("artillery", 0);
        serial.put("engineer", 0);
        serial.put("naval", 0);

        List<String> forces = faction.getInitialForces();
        String prefix = deriveUnitPrefix(faction.getName());

        for (String forceName : forces) {
            String type = inferUnitType(forceName);
            serial.merge("total", 1, Integer::sum);
            serial.merge(type, 1, Integer::sum);

            Unit u = new Unit();
            u.setName(generateUnitName(prefix, type, serial.get(type)));
            u.setType(type);
            @SuppressWarnings("unchecked")
            Map<String, Object> ut = (Map<String, Object>) UNIT_TYPES.getOrDefault(type, UNIT_TYPES.get("infantry"));
            u.setAttack(((Number) ut.get("atk_bonus")).intValue() + 7);
            u.setDefense(((Number) ut.get("def_bonus")).intValue() + 5);
            u.setMorale(55);
            u.setExperience(20);
            u.setSpeed("cavalry".equals(type) ? 2 : 1);
            u.setStrength(100);
            u.setMaxStrength(100);
            u.setStatus("ready");

            // 部署位置：有领土则放在首府，否则随机
            List<String> territories = faction.getInitialTerritory();
            if (!territories.isEmpty()) {
                u.setPosition(territories.get(units.size() % territories.size()));
            }
            units.add(u);
        }
        return units;
    }

    /** 根据名称推断兵种 */
    public String inferUnitType(String name) {
        if (name.contains("骑兵") || name.contains("马队") || name.contains("蒙旗")) return "cavalry";
        if (name.contains("炮兵") || name.contains("炮队")) return "artillery";
        if (name.contains("工兵") || name.contains("铁道")) return "engineer";
        if (name.contains("海军") || name.contains("舰") || name.contains("水师")) return "naval";
        return "infantry";
    }

    /** 生成部队番号 */
    public String generateUnitName(String prefix, String type, int serialNum) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ut = (Map<String, Object>) UNIT_TYPES.getOrDefault(type, UNIT_TYPES.get("infantry"));
        String suffix = (String) ut.get("suffix");
        return prefix + ut.get("name") + "第" + serialNum + suffix;
    }

    /** 从势力名推断番号前缀 */
    private String deriveUnitPrefix(String name) {
        if (name.length() <= 4) return name;
        // 简单截取前3-4字
        return name.substring(0, Math.min(4, name.length()));
    }

    /** 从部队列表重算各兵种计数 */
    public Map<String, Integer> recountArmyFromUnits(List<Unit> units) {
        Map<String, Integer> army = new HashMap<>();
        army.put("infantry", 0);
        army.put("cavalry", 0);
        army.put("artillery", 0);
        army.put("engineer", 0);
        army.put("naval", 0);
        if (units != null) {
            for (Unit u : units) {
                if (u.isActive()) {
                    army.merge(u.getType(), 1, Integer::sum);
                }
            }
        }
        return army;
    }

    // ═══════════════════════════════════════════ 外交/区域/战术辅助 ═══════════════════════════════════════════

    /** 检查势力所在区域是否已统一（该区无其他存活的AI势力） */
    public boolean isRegionUnified(GameState state, String factionId) {
        var faction = getFaction(factionId).orElse(null);
        if (faction == null) return false;
        String region = faction.getRegion();
        for (var entry : state.getAiFactions().entrySet()) {
            if (state.getDefeatedFactions().contains(entry.getKey())) continue;
            var af = getFaction(entry.getKey()).orElse(null);
            if (af != null && region.equals(af.getRegion())) return false;
        }
        return true;
    }

    /** 检查两势力间是否有互不侵犯协议 */
    public boolean hasNonAggression(GameState state, String fid1, String fid2) {
        Map<String, Integer> pacts = state.getNonAggressionPacts();
        if (pacts == null) return false;
        String key1 = fid1 + "↔" + fid2;
        String key2 = fid2 + "↔" + fid1;
        return (pacts.containsKey(key1) && pacts.get(key1) > 0)
                || (pacts.containsKey(key2) && pacts.get(key2) > 0);
    }

    /** 获取全部战术（内置+自定义），返回 {tacticId: {name, icon, atk_mult, def_mult, loss_mult}} */
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> getAllTactics(GameState state) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (var entry : TACTICS.entrySet()) {
            merged.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        if (state != null && state.getCustomTactics() != null) {
            for (var entry : state.getCustomTactics().entrySet()) {
                CustomTactic ct = entry.getValue();
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("name", ct.getName());
                t.put("icon", ct.getIcon());
                t.put("atk_mult", ct.getAtkMult());
                t.put("def_mult", ct.getDefMult());
                t.put("loss_mult", ct.deriveLossMult());
                t.put("pro", ct.getPro());
                t.put("con", ct.getCon());
                merged.put(entry.getKey(), t);
            }
        }
        return merged;
    }

    /** 如果失去的领土是首都，迁移到剩余领土中的第一个 */
    public String relocateCapitalIfLost(FactionState fs, String lostName) {
        if (fs.getCapital() == null || !fs.getCapital().equals(lostName)) return null;
        List<String> territories = fs.getTerritories();
        if (territories == null || territories.isEmpty()) {
            fs.setCapital("");
            return "⚠ 首都" + lostName + "沦陷！已无领土可迁都！";
        }
        fs.setCapital(territories.get(0));
        return "⚠ 首都" + lostName + "沦陷！迁都至" + territories.get(0) + "。";
    }

    /** 获取某势力的领土列表 */
    public List<String> getFactionTerritories(GameState state, String fid) {
        if (fid == null) return List.of();
        if (fid.equals(state.getPlayerFactionId())) {
            return state.getFactionState() != null && state.getFactionState().getTerritories() != null
                    ? state.getFactionState().getTerritories() : List.of();
        }
        var aiData = state.getAiFactions().get(fid);
        if (aiData == null) return List.of();
        FactionState afs = aiData.getFactionState();
        return afs != null && afs.getTerritories() != null ? afs.getTerritories() : List.of();
    }

    /** 获取某势力的 faction_state */
    public FactionState getFactionState(GameState state, String fid) {
        if (fid == null) return null;
        if (fid.equals(state.getPlayerFactionId())) return state.getFactionState();
        var aiData = state.getAiFactions().get(fid);
        if (aiData == null) return null;
        return aiData.getFactionState() != null ? aiData.getFactionState()
                : buildLegacyFactionState(aiData);
    }

    private FactionState buildLegacyFactionState(AiFactionData aiData) {
        FactionState fs = aiData.getFactionState();
        if (fs == null) {
            fs = new FactionState();
            fs.setTerritories(aiData.getTerritories());
        }
        return fs;
    }
}
