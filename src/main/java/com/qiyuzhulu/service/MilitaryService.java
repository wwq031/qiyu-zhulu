package com.qiyuzhulu.service;

import com.qiyuzhulu.model.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 军事系统 — 训练、战役、军事菜单。
 * 对应 Python qiyu_actions_military.py。
 */
@Service
public class MilitaryService {

    private final GameEngine engine;
    private final Random rng = new Random();

    /** 训练选项定义 */
    public static final Map<String, Map<String, Object>> TRAINING_OPTIONS = new LinkedHashMap<>();
    static {
        TRAINING_OPTIONS.put("infantry",  Map.of("name","步兵师","icon","🗡","turns",2,"cost",8,"atk",10,"def",8,"morale",55,"exp",20,"maintenance_cost",2));
        TRAINING_OPTIONS.put("cavalry",   Map.of("name","骑兵旅","icon","🐎","turns",3,"cost",12,"atk",14,"def",5,"morale",60,"exp",25,"maintenance_cost",3));
        TRAINING_OPTIONS.put("artillery", Map.of("name","炮兵营","icon","💣","turns",4,"cost",18,"atk",18,"def",3,"morale",50,"exp",30,"maintenance_cost",4));
        TRAINING_OPTIONS.put("engineer",  Map.of("name","工兵连","icon","🔧","turns",3,"cost",10,"atk",6,"def",12,"morale",50,"exp",20,"maintenance_cost",3));
        TRAINING_OPTIONS.put("naval",     Map.of("name","海军舰艇","icon","⚓","turns",5,"cost",20,"atk",16,"def",10,"morale",55,"exp",30,"maintenance_cost",5));
    }

    public MilitaryService(GameEngine engine) { this.engine = engine; }

    /** 获取所有训练选项（含自定义兵种） */
    public Map<String, Map<String, Object>> getAllTrainingOptions(GameState state) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>(TRAINING_OPTIONS);
        if (state != null && state.getCustomUnitTypes() != null) {
            for (var entry : state.getCustomUnitTypes().entrySet()) {
                CustomUnitType td = entry.getValue();
                int cost = (int) td.deriveCost();
                Map<String, Object> opt = new LinkedHashMap<>();
                opt.put("name", td.getName());
                opt.put("icon", td.getIcon() != null ? td.getIcon() : "✦");
                opt.put("turns", td.deriveTurns());
                opt.put("cost", cost);
                opt.put("atk", td.getAtk());
                opt.put("def", td.getDef());
                opt.put("morale", td.getMorale());
                opt.put("exp", td.getExp());
                opt.put("maintenance_cost", Math.max(1, cost / 4));
                merged.put(entry.getKey(), opt);
            }
        }
        return merged;
    }

    /** 列出可部署新部队的地点 */
    public List<Map<String, Object>> listTrainingLocations(GameState state) {
        FactionState fs = state.getFactionState();
        Set<String> activeProvs = state.getActiveCampaigns().stream()
                .filter(c -> "ongoing".equals(c.getStatus()))
                .map(Campaign::getProvince)
                .collect(Collectors.toSet());

        List<Map<String, Object>> locations = new ArrayList<>();
        for (var entry : engine.getMapData().getAll().entrySet()) {
            String pid = entry.getKey();
            Province p = entry.getValue();
            if (fs.getTerritories().contains(p.getName()) && !activeProvs.contains(pid)) {
                int cnt = (int) fs.getUnits().stream().filter(u -> pid.equals(u.getPosition())).count();
                Map<String, Object> loc = new LinkedHashMap<>();
                loc.put("pid", pid);
                loc.put("name", p.getName());
                loc.put("terrain", p.getTerrain());
                loc.put("garrison_count", cnt);
                locations.add(loc);
            }
        }
        if (locations.isEmpty() && !fs.getTerritories().isEmpty()) {
            String first = fs.getTerritories().get(0);
            String pid = engine.getPidByName(first);
            if (pid != null) {
                Map<String, Object> loc = new LinkedHashMap<>();
                loc.put("pid", pid);
                loc.put("name", first);
                loc.put("terrain", "平原");
                loc.put("garrison_count", 0);
                locations.add(loc);
            }
        }
        return locations;
    }

    /** 开始训练部队 */
    public Map<String, Object> startTraining(GameState state, String unitType, String locationPid, boolean earlyDeploy) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> ut = getAllTrainingOptions(state).get(unitType);
        if (ut == null) {
            ut = Map.of("name", unitType, "icon", "✦", "turns", 4, "cost", 18, "atk", 14, "def", 8, "morale", 60, "exp", 25);
        }

        FactionState fs = state.getFactionState();
        int cost = ((Number) ut.get("cost")).intValue();
        if (earlyDeploy) cost = Math.max(1, cost / 2);

        if (fs.getTreasury() < cost) {
            result.put("ok", false);
            result.put("message", "国库不足（需" + cost + "💰，当前" + fs.getTreasury() + "💰）");
            return result;
        }
        if (state.getActionPoints() < 1) {
            result.put("ok", false);
            result.put("message", "行动点不足");
            return result;
        }

        Province loc = engine.getProvince(locationPid);
        if (loc == null) {
            result.put("ok", false);
            result.put("message", "无效部署地点");
            return result;
        }

        fs.setTreasury(fs.getTreasury() - cost);
        state.setActionPoints(state.getActionPoints() - 1);

        int turns = Math.max(1, ((Number) ut.get("turns")).intValue() - 1);
        if (!earlyDeploy) turns = ((Number) ut.get("turns")).intValue();

        TrainingItem item = new TrainingItem();
        item.setName("训练" + ut.get("name"));
        item.setUnitType(unitType);
        item.setTurnsLeft(turns);
        item.setTotalTurns(((Number) ut.get("turns")).intValue());
        item.setLocation(locationPid);
        item.setLocationName(loc.getName());
        item.setEarlyDeploy(earlyDeploy);
        item.setCost(cost);
        state.getTrainingQueue().add(item);

        result.put("ok", true);
        result.put("message", "开始训练" + ut.get("name") + " @" + loc.getName() + "（" + turns + "回合 · " + cost + "💰）");
        return result;
    }

    /** 补给部队 */
    public Map<String, Object> resupplyUnit(GameState state, String unitName, int amount) {
        Map<String, Object> result = new LinkedHashMap<>();
        FactionState fs = state.getFactionState();
        Unit unit = null;
        for (Unit u : fs.getUnits()) {
            if (u.getName().equals(unitName)) { unit = u; break; }
        }
        if (unit == null) {
            result.put("ok", false); result.put("message", "部队不存在"); return result;
        }
        if (unit.getStrength() >= unit.getMaxStrength()) {
            result.put("ok", false); result.put("message", "兵力已满"); return result;
        }
        int cost = Math.max(1, amount / 10);
        if (fs.getTreasury() < cost) {
            result.put("ok", false); result.put("message", "国库不足"); return result;
        }
        fs.setTreasury(fs.getTreasury() - cost);
        unit.setStrength(Math.min(unit.getMaxStrength(), unit.getStrength() + amount));
        result.put("ok", true);
        result.put("message", unitName + " 补给完成 +" + amount + "兵力（" + cost + "💰）");
        return result;
    }

    /** 列出敌对省份 */
    public List<Map<String, Object>> listEnemyProvinces(GameState state) {
        FactionState fs = state.getFactionState();
        String playerFid = state.getPlayerFactionId();
        Set<String> playerTerrs = new HashSet<>(fs.getTerritories());
        Set<String> campaignProvs = state.getActiveCampaigns().stream()
                .filter(c -> "ongoing".equals(c.getStatus())).map(Campaign::getProvince)
                .collect(Collectors.toSet());

        List<Map<String, Object>> enemies = new ArrayList<>();
        Map<String, String> nameToOwner = new HashMap<>();

        // AI势力领土
        for (var entry : state.getAiFactions().entrySet()) {
            String fid = entry.getKey();
            AiFactionData ad = entry.getValue();
            FactionState afs = ad.getFactionState();
            if (afs == null || afs.getTerritories() == null) continue;
            if (state.getDefeatedFactions().contains(fid)) continue;
            for (String tname : afs.getTerritories()) {
                nameToOwner.put(tname, afs.getName());
            }
        }

        // 遍历地图找接壤的敌对省份
        for (String tname : playerTerrs) {
            String pid = engine.getPidByName(tname);
            if (pid == null) continue;
            Province p = engine.getProvince(pid);
            if (p == null || p.getConnections() == null) continue;
            for (String nbPid : p.getConnections().keySet()) {
                Province nb = engine.getProvince(nbPid);
                if (nb == null) continue;
                String nbName = nb.getName();
                if (!playerTerrs.contains(nbName) && !campaignProvs.contains(nbPid)) {
                    String owner = nameToOwner.get(nbName);
                    if (owner != null) {
                        Map<String, Object> ep = new LinkedHashMap<>();
                        ep.put("pid", nbPid);
                        ep.put("owner", owner);
                        ep.put("terrain", nb.getTerrain());
                        ep.put("in_campaign", false);
                        enemies.add(ep);
                    }
                }
            }
        }
        return enemies;
    }

    /** 获取部队列表（用于前端部署显示） */
    public List<Map<String, Object>> getUnitList(FactionState fs) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (fs.getUnits() == null) return list;
        for (int i = 0; i < fs.getUnits().size(); i++) {
            Unit u = fs.getUnits().get(i);
            Map<String, Object> ui = new LinkedHashMap<>();
            ui.put("index", i);
            ui.put("name", u.getName());
            ui.put("icon", getUnitIcon(u.getType()));
            ui.put("type", u.getType());
            ui.put("type_name", getUnitTypeName(u.getType()));
            ui.put("attack", u.getAttack());
            ui.put("defense", u.getDefense());
            ui.put("morale", u.getMorale());
            ui.put("strength", u.getStrength());
            ui.put("max_strength", u.getMaxStrength());
            ui.put("position", u.getPosition());
            ui.put("maintenance_cost", engine.calcUnitMaintenance(u, fs));
            list.add(ui);
        }
        return list;
    }

    public String getUnitIcon(String type) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ut = (Map<String, Object>) GameEngine.UNIT_TYPES.getOrDefault(type, Map.of());
        return (String) ut.getOrDefault("icon", "🗡");
    }

    public String getUnitTypeName(String type) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ut = (Map<String, Object>) GameEngine.UNIT_TYPES.getOrDefault(type, Map.of());
        return (String) ut.getOrDefault("name", type);
    }

    // ═══════════════════════════════════════════ 自定义战术/兵种 ═══════════════════════════════════════════

    /** 注册自定义战术 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> registerCustomTactic(GameState state, Map<String, Object> data) {
        String tacticId = ((String) data.getOrDefault("tactic_id", "")).strip();
        if (tacticId == null || tacticId.isEmpty()) return GameUtils.mapOf("ok", false, "message", "未指定战术id");
        if (GameEngine.TACTICS.containsKey(tacticId))
            return GameUtils.mapOf("ok", false, "message", "战术id \"" + tacticId + "\" 与内置战术冲突");

        String name = ((String) data.getOrDefault("name", "")).strip();
        if (name == null || name.isEmpty()) return GameUtils.mapOf("ok", false, "message", "未指定战术名称");

        double atkMult = Double.parseDouble(String.valueOf(data.getOrDefault("atk_mult", 1.0)));
        double defMult = Double.parseDouble(String.valueOf(data.getOrDefault("def_mult", 1.0)));
        if (atkMult < 0.1 || atkMult > 5.0) return GameUtils.mapOf("ok", false, "message", "攻击倍率需在0.1~5.0之间");
        if (defMult < 0 || defMult > 5.0) return GameUtils.mapOf("ok", false, "message", "防御倍率需在0~5.0之间");

        FactionState fs = state.getFactionState();
        int fee = data.containsKey("design_fee") ? ((Number) data.get("design_fee")).intValue() : 5;
        if (fs.getTreasury() < fee) return GameUtils.mapOf("ok", false, "message", "设计费不足（需" + fee + "💰）");
        fs.setTreasury(fs.getTreasury() - fee);

        CustomTactic ct = new CustomTactic();
        ct.setName(name);
        ct.setIcon((String) data.getOrDefault("icon", "✦"));
        ct.setAtkMult(atkMult);
        ct.setDefMult(defMult);
        ct.setPro((String) data.getOrDefault("pro", "灵活应变"));
        ct.setCon((String) data.getOrDefault("con", "无专精"));
        if (state.getCustomTactics() == null) state.setCustomTactics(new LinkedHashMap<>());
        state.getCustomTactics().put(tacticId, ct);

        double loss = deriveLossMult(atkMult, defMult);
        return GameUtils.mapOf("ok", true, "message",
                "自定义战术「" + name + "」已创建（攻x" + String.format("%.1f", atkMult)
                        + " 防x" + String.format("%.1f", defMult)
                        + " 损耗x" + String.format("%.1f", loss) + "）消耗" + fee + "💰");
    }

    /** 注册自定义兵种 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> registerCustomUnitType(GameState state, Map<String, Object> data) {
        String typeId = ((String) data.getOrDefault("type_id", "")).strip();
        if (typeId == null || typeId.isEmpty()) return GameUtils.mapOf("ok", false, "message", "未指定兵种id");
        if (GameEngine.UNIT_TYPES.containsKey(typeId))
            return GameUtils.mapOf("ok", false, "message", "兵种id \"" + typeId + "\" 与内置兵种冲突");

        String name = ((String) data.getOrDefault("name", "")).strip();
        if (name == null || name.isEmpty()) return GameUtils.mapOf("ok", false, "message", "未指定兵种名称");

        int atk = ((Number) data.getOrDefault("atk", 14)).intValue();
        int def = ((Number) data.getOrDefault("def", 8)).intValue();
        int morale = ((Number) data.getOrDefault("morale", 55)).intValue();
        int exp = ((Number) data.getOrDefault("exp", 25)).intValue();
        if (atk < 5 || atk > 50) return GameUtils.mapOf("ok", false, "message", "攻击力需在5~50之间");
        if (def < 3 || def > 50) return GameUtils.mapOf("ok", false, "message", "防御力需在3~50之间");
        if (morale < 20 || morale > 100) return GameUtils.mapOf("ok", false, "message", "士气需在20~100之间");
        if (exp < 10 || exp > 80) return GameUtils.mapOf("ok", false, "message", "经验需在10~80之间");

        FactionState fs = state.getFactionState();
        int fee = data.containsKey("design_fee") ? ((Number) data.get("design_fee")).intValue() : 10;
        if (fs.getTreasury() < fee) return GameUtils.mapOf("ok", false, "message", "设计费不足（需" + fee + "💰）");
        fs.setTreasury(fs.getTreasury() - fee);

        CustomUnitType cut = new CustomUnitType();
        cut.setName(name);
        cut.setIcon((String) data.getOrDefault("icon", "✦"));
        cut.setAtk(atk);
        cut.setDef(def);
        cut.setMorale(morale);
        cut.setExp(exp);
        cut.setSuffix((String) data.getOrDefault("suffix", "号"));
        if (state.getCustomUnitTypes() == null) state.setCustomUnitTypes(new LinkedHashMap<>());
        state.getCustomUnitTypes().put(typeId, cut);

        int cost = deriveUnitCost(atk, def, morale, exp);
        int turns = deriveUnitTurns(cost);
        return GameUtils.mapOf("ok", true, "message",
                "自定义兵种「" + name + "」已创建（攻" + atk + " 防" + def + " 造价" + cost + "💰/" + turns + "回合）消耗" + fee + "💰");
    }

    // ── 推导公式 ──

    public static double deriveLossMult(double atkMult, double defMult) {
        return Math.max(0.2, Math.min(3.0,
                Math.round((atkMult * 0.8 + Math.max(0, 1.0 - defMult) * 0.4) * 10) / 10.0));
    }

    public static int deriveUnitCost(int atk, int def, int morale, int exp) {
        return Math.max(5, (int) Math.round(atk * 0.55 + def * 0.40 + morale * 0.04 + exp * 0.03));
    }

    public static int deriveUnitTurns(int cost) {
        return Math.max(2, (int) Math.round(cost / 4.5));
    }
}
