package com.qiyuzhulu.controller;

import com.qiyuzhulu.model.*;
import com.qiyuzhulu.repo.SaveRepo;
import com.qiyuzhulu.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 游戏状态API — /api/state, /api/action, /api/turn/end。
 * 对应 Python server.py 的核心游戏循环。
 */
@RestController
@RequestMapping("/api")
public class StateController {

    private final GameEngine engine;
    private final PanelRenderer renderer;
    private final TurnAdvanceService turnAdvance;
    private final MilitaryService military;
    private final CivilService civil;
    private final CampaignService campaign;
    private final DiplomacyService diplomacy;
    private final TechService techService;
    private final SaveRepo saveRepo;

    /** 当前游戏状态（全局单例，对应Python GAME变量） */
    private GameState game;
    private final java.util.Random rng = new java.util.Random();

    public StateController(GameEngine engine, PanelRenderer renderer,
                           TurnAdvanceService turnAdvance, MilitaryService military,
                           CivilService civil, CampaignService campaign,
                           DiplomacyService diplomacy, TechService techService, SaveRepo saveRepo) {
        this.engine = engine;
        this.renderer = renderer;
        this.turnAdvance = turnAdvance;
        this.military = military;
        this.civil = civil;
        this.campaign = campaign;
        this.diplomacy = diplomacy;
        this.techService = techService;
        this.saveRepo = saveRepo;
    }

    /** GET /api/state — 获取当前面板 */
    @GetMapping("/state")
    public Map<String, Object> getState() {
        if (game == null) return Map.of("error", "无存档。请先 POST /api/load 或启动新游戏。");
        return buildPanelResponse();
    }

    /** POST /api/action — 执行游戏行动 */
    @PostMapping("/action")
    public Map<String, Object> doAction(@RequestBody Map<String, Object> body) {
        if (game == null) return Map.of("error", "无存档");

        String action = (String) body.getOrDefault("action", "");
        if (action == null || action.isEmpty()) return Map.of("error", "缺少 action 参数");

        // 结束回合
        if ("E".equalsIgnoreCase(action)) {
            List<String> events = turnAdvance.advance(game);
            autoSave();
            Map<String, Object> resp = buildPanelResponse();
            resp.put("action", "end_turn");
            resp.put("output", String.join("\n", events));
            return resp;
        }

        // ── 行动分发 ──
        Map<String, Object> resp;
        int ap = game.getActionPoints();
        if (ap <= 0 && !"E".equalsIgnoreCase(action)) {
            resp = buildPanelResponse();
            resp.put("result_type", "no_ap");
            resp.put("output", "行动点不足！请结束回合。");
            return resp;
        }

        // 军事统帅部
        if ("1".equals(action)) {
            resp = buildPanelResponse();
            resp.put("result_type", "military_menu");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("military_menu", true);
            data.put("ap", ap);
            data.put("unit_count", game.getFactionState().getUnits().size());
            data.put("campaign_count", (int) game.getActiveCampaigns().stream().filter(c -> "ongoing".equals(c.getStatus())).count());
            data.put("training_count", game.getTrainingQueue().size());
            data.put("sections", List.of(
                    Map.of("id","1.1","name","军队训练","icon","🗡","desc","训练新部队"),
                    Map.of("id","1.2","name","当前战争","icon","⚔","desc","查看进行中的战役"),
                    Map.of("id","1.3","name","兵力部署","icon","📍","desc","查看部队驻地与详情"),
                    Map.of("id","1.4","name","军事行动","icon","🎯","desc","发动战役/调动部队")
            ));
            resp.put("data", data);
            return resp;
        }
        // 训练
        if ("1.1".equals(action)) {
            resp = buildPanelResponse();
            resp.put("result_type", "training_menu");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("training_menu", true);
            data.put("treasury", game.getFactionState().getTreasury());
            data.put("ap", ap);
            data.put("unit_types", military.getAllTrainingOptions(game).entrySet().stream().map(e -> {
                Map<String, Object> ut = new LinkedHashMap<>(e.getValue());
                ut.put("key", e.getKey());
                return ut;
            }).collect(Collectors.toList()));
            data.put("locations", military.listTrainingLocations(game));
            data.put("queue", game.getTrainingQueue());
            resp.put("data", data);
            return resp;
        }
        // 内政
        if ("2".equals(action)) {
            resp = buildPanelResponse();
            resp.put("result_type", "domestic_menu");
            resp.put("data", civil.getDomesticMenu(game));
            return resp;
        }
        // 税率调整 2.tax.{agri|commerce}.{value}
        if (action.startsWith("2.tax.")) {
            String[] parts = action.split("\\.");
            if (parts.length == 4) {
                String taxType = parts[2];
                int value = Integer.parseInt(parts[3]);
                Map<String, Object> result = civil.setTaxRate(game, taxType, value);
                resp = buildPanelResponse();
                resp.put("result_type", "domestic_menu");
                resp.put("data", civil.getDomesticMenu(game));
                resp.put("output", result.get("message"));
                return resp;
            }
        }
        // 内政建设 2.1-2.11
        if (action.matches("2\\.\\d+")) {
            // 支持 location_pid、province（根级）或 meta.province（前端封装）
            String locPid = body.get("location_pid") != null ? body.get("location_pid").toString() : null;
            if (locPid == null) locPid = body.get("province") != null ? body.get("province").toString() : null;
            if (locPid == null && body.get("meta") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = (Map<String, Object>) body.get("meta");
                locPid = meta.get("province") != null ? meta.get("province").toString() : null;
            }
            Map<String, Object> result = civil.build(game, action, locPid);
            if (Boolean.TRUE.equals(result.get("ok"))) {
                game.setActionPoints(ap - 1);
            }
            resp = buildPanelResponse();
            resp.put("result_type", "ok");
            resp.put("output", result.get("message"));
            return resp;
        }
        // 外交
        if ("3".equals(action)) {
            resp = buildPanelResponse();
            resp.put("result_type", "diplo_menu");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("diplo_targets", diplomacy.getDiploTargets(game));
            // 附加区域内势力间外交关系
            String myRegion = engine.getFaction(game.getPlayerFactionId()).map(FactionDefinition::getRegion).orElse("");
            List<Map<String,Object>> regionRelations = new ArrayList<>();
            for (var entry : game.getAllDiplomaticRelations().entrySet()) {
                String[] fids = entry.getKey().split("↔");
                if (fids.length != 2) continue;
                String aName = getNameForFid(fids[0]), bName = getNameForFid(fids[1]);
                if (aName == null || bName == null) continue;
                // 只要同区域的
                String aRegion = engine.getFaction(fids[0]).map(FactionDefinition::getRegion).orElse("");
                if (!myRegion.equals(aRegion)) {
                    aRegion = engine.getFaction(fids[1]).map(FactionDefinition::getRegion).orElse("");
                    if (!myRegion.equals(aRegion)) continue;
                }
                Map<String,Object> relEntry = new LinkedHashMap<>(entry.getValue());
                relEntry.put("a_name", aName); relEntry.put("b_name", bName);
                regionRelations.add(relEntry);
            }
            data.put("region_relations", regionRelations);
            resp.put("data", data);
            return resp;
        }
        // 外交操作 3.1-3.6
        if (action.matches("3\\.[1-6]")) {
            String sub = action.substring(2); // "1"-"6"
            Integer tIdx = body.get("target_index") != null ? ((Number) body.get("target_index")).intValue() : null;
            Map<String, Object> result = diplomacy.doDiploAction(game, sub, tIdx != null ? tIdx : -1);
            if (Boolean.TRUE.equals(result.get("ok"))) game.setActionPoints(Math.max(0, ap - 1));
            resp = buildPanelResponse();
            resp.put("result_type", Boolean.TRUE.equals(result.get("ok")) ? "ok" : "error");
            resp.put("output", result.get("message"));
            if (result.containsKey("type")) resp.put("intel_data", result);
            return resp;
        }
        // 情报
        if ("4".equals(action)) {
            resp = buildPanelResponse();
            resp.put("result_type", "intel_menu");
            resp.put("data", Map.of("intel_actions", List.of(
                    Map.of("id","4.1","name","侦察敌情","desc","详细侦察目标势力（六围估值/部队/动向）"),
                    Map.of("id","4.2","name","内部维稳","desc","5💰 提升思想与民心"),
                    Map.of("id","4.3","name","邻区侦察","desc","获取相邻区域势力概况"),
                    Map.of("id","4.4","name","反间谍行动","desc","6💰 清除敌方间谍网络")
            )));
            return resp;
        }
        // 情报操作 4.1-4.4
        if (action.matches("4\\.[1-4]")) {
            String sub = action.substring(2); // "1"-"4"
            Map<String, Object> result = diplomacy.doIntelAction(game, sub);
            if (Boolean.TRUE.equals(result.get("ok"))) game.setActionPoints(Math.max(0, ap - 1));
            resp = buildPanelResponse();
            resp.put("result_type", Boolean.TRUE.equals(result.get("ok")) ? "ok" : "error");
            resp.put("output", result.get("message"));
            if (result.containsKey("type")) resp.put("intel_data", result);
            return resp;
        }
        // 提前服役 1.1.early.{queueIndex}
        if (action.startsWith("1.1.early.")) {
            int qIdx = Integer.parseInt(action.substring("1.1.early.".length()));
            var queue = game.getTrainingQueue();
            if (qIdx < 0 || qIdx >= queue.size()) {
                resp = buildPanelResponse(); resp.put("output", "无效训练项目"); return resp;
            }
            TrainingItem item = queue.get(qIdx);
            item.setEarlyDeploy(true);
            item.setTurnsLeft(0);
            // 调用 turnAdvance 的方法结算训练队列
            List<String> msgs = turnAdvance.flushTrainingQueue(game);
            resp = buildPanelResponse();
            resp.put("result_type", "ok");
            resp.put("output", String.join("\n", msgs));
            if (game.getActionPoints() > 0) game.setActionPoints(game.getActionPoints() - 1);
            return resp;
        }

        // 训练具体兵种 1.1.{type}
        if (action.startsWith("1.1.")) {
            String[] parts = action.split("\\.");
            String unitType = parts.length >= 3 ? parts[2] : "";
            // 尝试自定义兵种
            if (game.getCustomUnitTypes() != null && game.getCustomUnitTypes().containsKey(unitType)) {
                // 自定义兵种直接训练
            }
            if (parts.length >= 4) {
                // 1.1.{type}.{locIdx} — 选择地点训练
                int locIdx = Integer.parseInt(parts[3]) - 1;
                List<Map<String, Object>> locs = military.listTrainingLocations(game);
                if (locIdx >= 0 && locIdx < locs.size()) {
                    String locPid = (String) locs.get(locIdx).get("pid");
                    Map<String, Object> result = military.startTraining(game, unitType, locPid, false);
                    resp = buildPanelResponse();
                    resp.put("result_type", "ok");
                    resp.put("output", result.get("message"));
                    return resp;
                }
            }
            resp = buildPanelResponse();
            resp.put("result_type", "training_menu");
            // 返回该兵种的训练地点列表
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("training_menu", true);
            data.put("selecting_location", true);
            data.put("unit_type", unitType);
            data.put("locations", military.listTrainingLocations(game));
            resp.put("data", data);
            return resp;
        }
        // 1.2 当前战争 — 列出进行中的战役
        if ("1.2".equals(action)) {
            resp = buildPanelResponse();
            resp.put("result_type", "campaigns_menu");
            List<Map<String, Object>> ongoing = new ArrayList<>();
            List<Map<String, Object>> completed = new ArrayList<>();
            for (Campaign c : game.getActiveCampaigns()) {
                Map<String, Object> ci = new LinkedHashMap<>();
                ci.put("id", c.getId());
                ci.put("province_name", c.getProvinceName());
                ci.put("terrain", c.getTerrain() != null ? c.getTerrain() : "");
                ci.put("round", c.getRound());
                ci.put("max_rounds", c.getMaxRounds());
                ci.put("attacker_name", c.getAttackerName());
                ci.put("defender_name", c.getDefenderName());
                ci.put("attacker_units", c.getAttackerCache() != null ? c.getAttackerCache().size() : 0);
                ci.put("defender_units", c.getDefenderCache() != null ? c.getDefenderCache().size() : 0);
                int atkStr = 0, defStr = 0;
                if (c.getAttackerCache() != null) for (Unit u : c.getAttackerCache()) atkStr += u.getStrength();
                if (c.getDefenderCache() != null) for (Unit u : c.getDefenderCache()) defStr += u.getStrength();
                ci.put("attacker_strength", atkStr);
                ci.put("defender_strength", defStr);
                ci.put("attacker_tactics", c.getAttackerTactics());
                ci.put("defender_tactics", c.getDefenderTactics());
                if ("ongoing".equals(c.getStatus())) {
                    ongoing.add(ci);
                } else {
                    completed.add(ci);
                }
            }
            resp.put("data", Map.of("campaigns_menu", true, "ongoing", ongoing, "completed", completed,
                    "has_ongoing", !ongoing.isEmpty(), "ap", ap));
            return resp;
        }
        // 1.3 兵力部署 — 按区域+省份列出部队
        if ("1.3".equals(action)) {
            resp = buildPanelResponse();
            resp.put("result_type", "deployment_menu");
            Map<String, List<Unit>> posMap = engine.listArmyPositions(game.getFactionState().getUnits());
            // 按区域分组
            Map<String, Map<String, List<Unit>>> regionProvs = new LinkedHashMap<>();
            for (var entry : posMap.entrySet()) {
                Province p = engine.getProvince(entry.getKey());
                String region = p != null && p.getRegion() != null ? p.getRegion() : "其他";
                String provName = p != null ? p.getName() : entry.getKey();
                regionProvs.computeIfAbsent(region, k -> new LinkedHashMap<>())
                        .put(provName, entry.getValue());
            }
            List<Map<String, Object>> regionUnits = new ArrayList<>();
            int totalUnits = 0, totalAtk = 0, totalDef = 0, totalMorale = 0;
            for (var re : regionProvs.entrySet()) {
                String rname = GameEngine.REGION_NAMES.getOrDefault(re.getKey(), re.getKey());
                List<Map<String, Object>> provinces = new ArrayList<>();
                for (var pe : re.getValue().entrySet()) {
                    List<Map<String, Object>> unitList = new ArrayList<>();
                    for (Unit u : pe.getValue()) {
                        Map<String, Object> ui = new LinkedHashMap<>();
                        ui.put("name", u.getName()); ui.put("icon", military.getUnitIcon(u.getType()));
                        ui.put("type_name", military.getUnitTypeName(u.getType()));
                        ui.put("attack", u.getAttack()); ui.put("defense", u.getDefense());
                        ui.put("strength", u.getStrength()); ui.put("max_strength", u.getMaxStrength());
                        ui.put("morale", u.getMorale()); ui.put("exp", u.getExperience());
                        ui.put("exp_tag", u.getExperience() >= 50 ? "★" : "");
                        ui.put("status", u.getStatus() != null ? u.getStatus() : "ready");
                        ui.put("supply", u.getSupply() != null ? u.getSupply() : "supplied");
                        ui.put("supply_tag", "cut_off".equals(u.getSupply()) ? "⚠断补" : "strained".equals(u.getSupply()) ? "⚠吃紧" : "");
                        ui.put("maintenance_cost", engine.calcUnitMaintenance(u, game.getFactionState()));
                        unitList.add(ui);
                        totalUnits++; totalAtk += u.getAttack(); totalDef += u.getDefense(); totalMorale += u.getMorale();
                    }
                    provinces.add(Map.of("name", pe.getKey(), "terrain", "—", "is_owned", true, "units", unitList));
                }
                regionUnits.add(Map.of("name", rname, "provinces", provinces));
            }
            int totalMaint = engine.calcTotalMaintenance(game.getFactionState());
            resp.put("data", Map.of("deployment_menu", true, "region_units", regionUnits,
                    "total_units", totalUnits, "total_atk", totalAtk, "total_def", totalDef,
                    "avg_morale", totalUnits > 0 ? totalMorale / totalUnits : 0,
                    "total_maintenance", totalMaint, "ap", ap));
            return resp;
        }
        // 1.4 军事行动菜单 — 发动战役/调动部队
        if ("1.4".equals(action)) {
            resp = buildPanelResponse();
            resp.put("result_type", "operations_menu");
            resp.put("data", Map.of("operations_menu", true, "ap", ap, "options", List.of(
                Map.of("id","1.4.1","name","发动战役","icon","⚔","desc","选择省份→选部队→发动进攻"),
                Map.of("id","1.4.2","name","调动部队","icon","🚚","desc","在地图上选择部队移动")
            )));
            return resp;
        }
        // 战役菜单 1.4.1 — 显示可攻击的敌方省份
        if ("1.4.1".equals(action)) {
            resp = buildPanelResponse();
            resp.put("result_type", "campaign_provinces");
            resp.put("data", Map.of("campaign_provinces", true, "ap", ap, "provinces", campaign.listEnemyProvinces(game)));
            return resp;
        }

        // 发动战役 1.4.1.{province_pid}
        if (action.startsWith("1.4.1.") && !"1.4.1".equals(action)) {
            String provincePid = action.substring("1.4.1.".length());
            @SuppressWarnings("unchecked")
            List<Integer> indices = (List<Integer>) body.get("unit_indices");
            @SuppressWarnings("unchecked")
            Map<String, String> tactics = (Map<String, String>) body.get("unit_tactics");
            if (indices == null || indices.isEmpty()) {
                resp = buildPanelResponse();
                resp.put("result_type", "error");
                resp.put("output", "未选择攻击部队");
                return resp;
            }
            Map<String, Object> result = campaign.startCampaign(game, provincePid, new ArrayList<>(indices), tactics);
            resp = buildPanelResponse();
            resp.put("result_type", Boolean.TRUE.equals(result.get("ok")) ? "ok" : "error");
            resp.put("output", result.get("message"));
            return resp;
        }
        // 区域攻略
        if ("5".equals(action)) {
            FactionState fs = game.getFactionState();
            String playerRegion = engine.getFaction(game.getPlayerFactionId())
                    .map(FactionDefinition::getRegion).orElse("");
            long regionFactions = game.getAiFactions().entrySet().stream()
                    .filter(e -> {
                        FactionDefinition f = engine.getFaction(e.getKey()).orElse(null);
                        return f != null && playerRegion.equals(f.getRegion());
                    }).count();
            boolean unified = regionFactions == 0;
            resp = buildPanelResponse();
            resp.put("result_type", "ok");
            resp.put("output", unified ?
                    "🎉 本区域已统一！解锁跨区作战权限。" :
                    "📋 本区域还有 " + regionFactions + " 个敌对势力需要清除。");
            return resp;
        }
        // 国策
        if ("7".equals(action)) {
            resp = buildPanelResponse();
            resp.put("result_type", "reso_menu");
            resp.put("data", diplomacy.listResolutions(game));
            return resp;
        }
        // 执行国策 7.{res_id}
        if (action.startsWith("7.")) {
            String resId = action.substring(2);
            Map<String, Object> result = diplomacy.executeResolution(game, resId);
            resp = buildPanelResponse();
            resp.put("result_type", Boolean.TRUE.equals(result.get("ok")) ? "ok" : "error");
            resp.put("output", result.get("message"));
            return resp;
        }
        // 科技
        if ("8".equals(action)) {
            resp = buildPanelResponse();
            resp.put("result_type", "tech_menu");
            resp.put("data", techService.getAvailableTechs(game));
            return resp;
        }
        // 研发科技 8.{tech_id}
        if (action.startsWith("8.")) {
            String techId = action.substring(2);
            Map<String, Object> result = techService.startResearch(game, techId);
            resp = buildPanelResponse();
            resp.put("result_type", Boolean.TRUE.equals(result.get("ok")) ? "ok" : "error");
            resp.put("output", result.get("message"));
            return resp;
        }
        // 休战提议 3.0
        if ("3.0".equals(action)) {
            String targetId = body.get("target_id") != null ? body.get("target_id").toString() : null;
            if (targetId == null) { resp = buildPanelResponse(); resp.put("output", "缺少target_id"); return resp; }
            Map<String, Object> result = diplomacy.proposeTruce(game, targetId);
            resp = buildPanelResponse();
            resp.put("result_type", Boolean.TRUE.equals(result.get("ok")) ? "ok" : "error");
            resp.put("output", result.get("message"));
            return resp;
        }

        // 存档退出 [6]
        if ("6".equals(action)) {
            autoSave();
            resp = buildPanelResponse();
            resp.put("result_type", "quit");
            resp.put("output", "存档已保存。窗口即将关闭...");
            return resp;
        }

        // 设计局 — 主菜单 1.5
        if ("1.5".equals(action)) {
            resp = buildPanelResponse();
            resp.put("result_type", "design_bureau");
            List<Map<String, Object>> options = new ArrayList<>();
            options.add(Map.of("id","1.5.1","name","设计自定义战术","icon","📐","desc","设定攻防倍率，损耗自动计算 · 设计费5💰"));
            options.add(Map.of("id","1.5.2","name","设计自定义兵种","icon","🏗","desc","设定攻防士气经验，造价自动推导 · 设计费10💰"));
            // 已注册的自定义战术
            if (game.getCustomTactics() != null) {
                for (var entry : game.getCustomTactics().entrySet()) {
                    CustomTactic ct = entry.getValue();
                    options.add(Map.of("id","tactic:"+entry.getKey(),"name","📐 "+ct.getName(),"icon","✅","desc","已注册 · 攻x"+String.format("%.1f",ct.getAtkMult())+" 防x"+String.format("%.1f",ct.getDefMult())));
                }
            }
            // 已注册的自定义兵种
            if (game.getCustomUnitTypes() != null) {
                for (var entry : game.getCustomUnitTypes().entrySet()) {
                    CustomUnitType cut = entry.getValue();
                    options.add(Map.of("id","unit:"+entry.getKey(),"name","🏗 "+cut.getName(),"icon","✅","desc","已注册 · 攻"+cut.getAtk()+" 防"+cut.getDef()+" 士"+cut.getMorale()));
                }
            }
            resp.put("data", Map.of("options", options,
                    "custom_tactics", game.getCustomTactics() != null ? game.getCustomTactics().keySet() : List.of(),
                    "custom_unit_types", game.getCustomUnitTypes() != null ? game.getCustomUnitTypes().keySet() : List.of()));
            return resp;
        }
        // 设计局 — 自定义战术 1.5.1
        if ("1.5.1".equals(action)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) body.get("meta");
            if (meta == null) { resp = buildPanelResponse(); resp.put("output", "缺少 meta 参数"); return resp; }
            Map<String, Object> result = military.registerCustomTactic(game, meta);
            resp = buildPanelResponse();
            resp.put("result_type", "design_result");
            resp.put("data", Map.of("ok", result.get("ok"), "message", result.get("message")));
            return resp;
        }
        // 设计局 — 自定义兵种 1.5.2
        if ("1.5.2".equals(action)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) body.get("meta");
            if (meta == null) { resp = buildPanelResponse(); resp.put("output", "缺少 meta 参数"); return resp; }
            Map<String, Object> result = military.registerCustomUnitType(game, meta);
            resp = buildPanelResponse();
            resp.put("result_type", "design_result");
            resp.put("data", Map.of("ok", result.get("ok"), "message", result.get("message")));
            return resp;
        }

        // 反腐行动 9
        if ("9".equals(action)) {
            int cost = 20;
            if (game.getFactionState().getTreasury() < cost) {
                resp = buildPanelResponse(); resp.put("output", "国库不足（需" + cost + "💰）"); return resp;
            }
            int reduction = rng.nextInt(11) + 5; // 5-15
            game.getFactionState().setTreasury(game.getFactionState().getTreasury() - cost);
            game.getFactionState().setCorruption(Math.max(0, game.getFactionState().getCorruption() - reduction));
            game.setActionPoints(Math.max(0, ap - 1));
            resp = buildPanelResponse();
            resp.put("result_type", "ok");
            resp.put("output", "🛡 反腐行动完成：腐败度-" + reduction + "（消耗" + cost + "💰）");
            return resp;
        }

        // 默认
        game.setActionPoints(ap - 1);
        resp = buildPanelResponse();
        resp.put("result_type", "ok");
        resp.put("output", "行动已执行");
        return resp;
    }

    /** POST /api/turn/end — 结束回合 */
    @PostMapping("/turn/end")
    public Map<String, Object> endTurn() {
        if (game == null) return Map.of("error", "无存档");
        List<String> events = turnAdvance.advance(game);
        autoSave();
        Map<String, Object> resp = buildPanelResponse();
        resp.put("action", "end_turn");
        resp.put("output", String.join("\n", events));
        return resp;
    }

    /** POST /api/new-game — 创建新游戏 */
    @PostMapping("/new-game")
    public Map<String, Object> newGame(@RequestBody Map<String, Object> body) {
        String factionId = (String) body.get("faction_id");
        if (factionId == null || factionId.isEmpty()) {
            return Map.of("error", "请选择势力");
        }

        @SuppressWarnings("unchecked")
        List<String> policies = (List<String>) body.get("policies");

        try {
            game = engine.newState(factionId, policies);
            turnAdvance.initBackgroundSimulation(game);
            autoSave();
            Map<String, Object> resp = buildPanelResponse();
            resp.put("message", "新游戏已创建：" + game.getFactionState().getName());
            resp.put("policies", game.getPhase1Policies());
            // Phase 1 帝国开场叙事
            if (game.getPhase() == 1) {
                resp.put("narrative", "宣统二年冬。紫禁城养心殿。\n\n帝国已到最危险的时刻——国库仅余二百万两白银，各省咨议局通电要求立宪，列强公使团日日催促，革命党在南方蠢蠢欲动。北洋六镇中已有五镇不听调遣。\n\n你是大清最后的决策者。\n\n七份紧急奏折已呈上御案。每一份都关乎帝国的存亡，但国帑有限，你不可能全部批准。\n\n请皇帝陛下御览批阅——帝国命运，在此一举。");
            }
            return resp;
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }

    /** POST /api/load — 读取存档 */
    @PostMapping("/load")
    public Map<String, Object> loadGame(@RequestBody Map<String, Object> body) {
        String slot = (String) body.getOrDefault("slot", "auto");
        try {
            game = saveRepo.load(slot);
            if (game == null) return Map.of("error", "存档不存在: " + slot);
            Map<String, Object> resp = buildPanelResponse();
            resp.put("message", "已加载存档 [" + slot + "]");
            return resp;
        } catch (Exception e) {
            return Map.of("error", "加载失败: " + e.getMessage());
        }
    }

    /** POST /api/save — 手动存档 */
    @PostMapping("/save")
    public Map<String, Object> saveGame(@RequestBody Map<String, Object> body) {
        if (game == null) return Map.of("error", "无游戏状态");
        String slot = (String) body.getOrDefault("slot", "auto");
        try {
            saveRepo.save(slot, game);
            return Map.of("message", "已保存到 [" + slot + "]", "slot", slot);
        } catch (Exception e) {
            return Map.of("error", "保存失败: " + e.getMessage());
        }
    }

    /** 获取当前游戏（供其他Controller访问） */
    public GameState getGame() { return game; }

    /** 构建完整面板响应（无参版本，使用当前游戏） */
    public Map<String, Object> buildPanelResponse() {
        return buildPanelResponse(game);
    }

    /** 构建完整面板响应（指定GameState，供其他Controller引用） */
    public Map<String, Object> buildPanelResponse(GameState g) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("save", "auto");
        resp.put("save_version", g.getVersion());
        resp.put("turn", g.getTurn());
        resp.put("date", g.getGameDate());
        resp.put("phase", g.getPhase());
        resp.put("phase_name", GameEngine.PHASE_NAMES.getOrDefault(g.getPhase(), ""));

        FactionState fs = g.getFactionState();
        FactionDefinition faction = engine.getFaction(g.getPlayerFactionId()).orElse(null);

        resp.put("faction", fs.getName());
        resp.put("ideology", faction != null ? faction.getIdeology() : "");
        resp.put("evolution", faction != null ? faction.getEvolution() : List.of("", "", ""));
        resp.put("leader", faction != null ? faction.getLeader() : null);
        // 国魂：优先Phase1分配的，其次势力自带的
        NationalSpirit spirit = fs.getNationalSpirit();
        if (spirit == null || spirit.getName() == null || spirit.getName().isEmpty())
            spirit = engine.getNationalSpirit(faction);
        resp.put("national_spirit", spirit);
        resp.put("stats", fs.getStats());
        resp.put("treasury", fs.getTreasury());
        resp.put("agri_tax_rate", fs.getAgriTaxRate());
        resp.put("commerce_tax_rate", fs.getCommerceTaxRate());
        resp.put("population_support", fs.getPopulationSupport());
        resp.put("corruption", fs.getCorruption());
        resp.put("active_wars", g.getActiveWars());
        resp.put("action_points", g.getActionPoints());
        resp.put("ap_max", g.getApMax());
        resp.put("total_maintenance", engine.calcTotalMaintenance(fs));
        resp.put("territories", fs.getTerritories());
        resp.put("territory_economy", engine.aggregateTerritoryEconomy(fs));
        resp.put("military_tech", fs.getMilitaryTech());
        resp.put("army", fs.getArmy());
        resp.put("construction", g.getConstructionQueue().size());
        resp.put("training", g.getTrainingQueue().size());

        // 驻军
        Map<String, List<Map<String, Object>>> garrisons = new LinkedHashMap<>();
        Map<String, List<Unit>> posMap = engine.listArmyPositions(fs.getUnits());
        for (var entry : posMap.entrySet()) {
            List<Map<String, Object>> unitList = new ArrayList<>();
            for (Unit u : entry.getValue()) {
                Map<String, Object> ui = new LinkedHashMap<>();
                ui.put("name", u.getName());
                ui.put("type", u.getType());
                ui.put("attack", u.getAttack());
                ui.put("defense", u.getDefense());
                ui.put("morale", u.getMorale());
                ui.put("strength", u.getStrength());
                ui.put("max_strength", u.getMaxStrength());
                ui.put("status", u.getStatus());
                ui.put("supply", u.getSupply());
                ui.put("index", fs.getUnits().indexOf(u));
                ui.put("is_player", true);
                ui.put("faction_name", fs.getName());
                unitList.add(ui);
            }
            garrisons.put(entry.getKey(), unitList);
        }
        resp.put("garrisons", garrisons);

        // 战役
        List<Map<String, Object>> camps = new ArrayList<>();
        for (Campaign c : game.getActiveCampaigns()) {
            if ("ongoing".equals(c.getStatus())) {
                Map<String, Object> ci = new LinkedHashMap<>();
                ci.put("id", c.getId());
                ci.put("province", c.getProvince());
                ci.put("province_name", c.getProvinceName());
                ci.put("terrain", c.getTerrain());
                ci.put("round", c.getRound());
                ci.put("max_rounds", c.getMaxRounds());
                ci.put("attacker_name", c.getAttackerName());
                ci.put("defender_name", c.getDefenderName());
                ci.put("attacker_tactics", c.getAttackerTactics());
                ci.put("defender_tactics", c.getDefenderTactics());
                ci.put("is_player_attacker", g.getPlayerFactionId().equals(c.getAttackerFaction()));
                ci.put("is_player_defender", g.getPlayerFactionId().equals(c.getDefenderFaction()));
                // 部队详情（供战斗弹窗显示）
                Map<String,String> atkTac = c.getAttackerTactics() != null ? c.getAttackerTactics() : Map.of();
                List<Map<String,Object>> atkUnits = new ArrayList<>();
                if (c.getAttackerCache() != null) for (Unit u : c.getAttackerCache())
                    atkUnits.add(Map.of("name",u.getName(),"type",u.getType(),"strength",u.getStrength(),
                            "tactic",atkTac.getOrDefault(u.getName(),"assault")));
                ci.put("attacker_units", atkUnits);
                Map<String,String> defTac = c.getDefenderTactics() != null ? c.getDefenderTactics() : Map.of();
                List<Map<String,Object>> defUnits = new ArrayList<>();
                if (c.getDefenderCache() != null) for (Unit u : c.getDefenderCache())
                    defUnits.add(Map.of("name",u.getName(),"type",u.getType(),"strength",u.getStrength(),
                            "tactic",defTac.getOrDefault(u.getName(),"fortify")));
                ci.put("defender_units", defUnits);
                // 增援队列
                ci.put("reinforcement_queue", c.getReinforcementQueue() != null ? c.getReinforcementQueue() : List.of());
                camps.add(ci);
            }
        }
        resp.put("active_campaigns", camps);

        resp.put("panel_text", renderer.render(g));
        resp.put("game_over", false);
        resp.put("enacted_resolutions", g.getEnactedResolutions());
        resp.put("researched_techs", g.getResearchedTechs());
        resp.put("policies", g.getPhase1Policies());
        resp.put("custom_order_flags", g.getCustomOrderFlags());
        resp.put("custom_tactics", g.getCustomTactics());
        resp.put("custom_unit_types", g.getCustomUnitTypes());

        // 瞬态
        resp.put("epic_events", g.getEpicEventsThisTurn());
        resp.put("turn_events", g.getEventsThisTurn());
        resp.put("defeated_factions", g.getDefeatedFactions());
        resp.put("defeat_events", g.getDefeatEvents());
        resp.put("campaign_results", g.getCampaignResultsThisTurn());
        resp.put("event_chain_choices", List.of());
        resp.put("rumors", List.of());
        resp.put("move_log", List.of());
        resp.put("moving_units", List.of());

        return resp;
    }

    /** POST /api/memorial/resolve — Phase1 批阅一份奏折（每回合最多1份） */
    @PostMapping("/memorial/resolve")
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveMemorial(@RequestBody Map<String, Object> body) {
        if (game == null || game.getPhase() != 1)
            return Map.of("error", "不在帝国阶段");

        FactionState fs = game.getFactionState();
        List<String> queue = game.getCustomOrderFlags(); // 暂存奏折队列
        String action = (String) body.getOrDefault("action", "next");

        if ("next".equals(action)) {
            // 本回合已处理过奏折→不再弹新的
            int turnKey = game.getTurn();
            String turnMarker = "_turn_" + turnKey;
            if (game.getPhase1Policies().contains(turnMarker))
                return Map.of("done", true, "message", "本回合已批阅");

            // 返回队列中下一份未处理的奏折
            for (String id : queue) {
                Map<String, Object> mem = GameEngine.MEMORIALS.get(id);
                if (mem != null && !game.getPhase1Policies().contains(id)
                        && !game.getPhase1Policies().contains("rej_" + id)) {
                    return Map.of("memorial_id", id,
                            "memorial", mem,
                            "treasury", fs.getTreasury(),
                            "support", fs.getPopulationSupport(),
                            "corruption", fs.getCorruption(),
                            "processed", (int) game.getPhase1Policies().stream().filter(p -> !p.startsWith("_turn")).count(),
                            "total", queue.stream().filter(q -> GameEngine.MEMORIALS.containsKey(q)).count());
                }
            }
            return Map.of("done", true, "message", "所有奏折已处理完毕");
        }

        // 批阅操作
        String memorialId = (String) body.get("memorial_id");
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        Map<String, Object> mem = GameEngine.MEMORIALS.get(memorialId);
        if (mem == null) return Map.of("error", "无效奏折");

        game.getPhase1Policies().add(approved ? memorialId : "rej_" + memorialId);
        game.getPhase1Policies().add("_turn_" + game.getTurn()); // 标记本回合已处理
        int cost = ((Number) mem.get("cost")).intValue();
        if (approved) {
            fs.setTreasury(Math.max(0, fs.getTreasury() - cost));
        } else {
            fs.setPopulationSupport(GameEngine.clamp(fs.getPopulationSupport() - 4));
            fs.setCorruption(GameEngine.clamp(fs.getCorruption() + 3));
        }

        return Map.of("ok", true, "treasury", fs.getTreasury(),
                "support", fs.getPopulationSupport(), "corruption", fs.getCorruption());
    }

    /** POST /api/empire/switch-faction — Phase1崩溃后切换势力 */
    @PostMapping("/empire/switch-faction")
    public Map<String, Object> switchFaction(@RequestBody Map<String, Object> body) {
        if (game == null) return Map.of("error", "无存档");
        String newFid = (String) body.get("faction_id");
        var faction = engine.getFaction(newFid).orElse(null);
        if (faction == null) return Map.of("error", "势力不存在");

        FactionState oldFs = game.getFactionState();
        FactionState fs = new FactionState();
        fs.setName(faction.getName());
        fs.setStats(faction.getStats().copy());
        fs.setTreasury(oldFs.getTreasury());
        fs.setPopulationSupport(oldFs.getPopulationSupport());
        fs.setCorruption(oldFs.getCorruption());
        fs.setMilitaryTech(1);
        fs.setCapital(faction.getInitialTerritory().isEmpty() ? "" : faction.getInitialTerritory().get(0));
        fs.setTerritories(new ArrayList<>(faction.getInitialTerritory()));
        fs.setForces(new ArrayList<>(faction.getInitialForces()));
        fs.setUnitSerial(new HashMap<>(Map.of("total", 0)));
        fs.setUnitPrefix(engine.deriveUnitPrefix(faction.getName()));
        // 继承奏折效果中的国魂
        Map<String, NationalSpirit> pending = game.getPendingSpirits();
        if (pending != null && pending.containsKey(newFid)) {
            NationalSpirit ns = pending.get(newFid);
            fs.setNationalSpirit(ns);
            if (ns.getEffects() != null)
                for (var e : ns.getEffects().entrySet())
                    fs.getStats().add(e.getKey(), e.getValue());
        }
        List<Unit> units = engine.autoGenerateUnits(faction);
        fs.setUnits(units);
        fs.setArmy(engine.recountArmyFromUnits(units));
        game.setFactionState(fs);
        game.setPlayerFactionId(newFid);
        // Phase设为3（直接进入区域统一战）
        game.setPhase(3);
        game.setActionPoints(3);
        autoSave();
        Map<String, Object> resp = buildPanelResponse();
        // 附加入场叙事 + 区域敌对势力
        String intro = faction.getCollapseIntro();
        if (intro != null && !intro.isEmpty()) {
            // 构建区域敌对势力描述
            StringBuilder rivals = new StringBuilder();
            String region = faction.getRegion();
            for (var fe : engine.getGameData().getFactions().entrySet()) {
                if (fe.getKey().equals(newFid)) continue;
                if (region.equals(fe.getValue().getRegion())) {
                    if (rivals.length() > 0) rivals.append("\n\n");
                    rivals.append("⚔ ").append(fe.getValue().getName())
                          .append(" · ").append(fe.getValue().getIdeology());
                }
            }
            String fullNarrative = intro + "\n\n———\n\n📋 区域争霸 · "
                + GameEngine.REGION_NAMES.getOrDefault(region, region)
                + "\n\n本区还有三支势力割据一方。只有消灭全部对手，才能统一本区域，获得对外扩张的资格：\n\n"
                + rivals.toString()
                + "\n\n———\n\n帝国崩塌给了所有人机会。但机会，只属于最后的胜利者。";
            resp.put("narrative", fullNarrative);
        }
        return resp;
    }

    /** POST /api/debug/switch — 调试用：随时切换势力 */
    @PostMapping("/debug/switch")
    public Map<String, Object> debugSwitch(@RequestBody Map<String, Object> body) {
        if (game == null) return Map.of("error", "无存档");
        String fid = (String) body.get("faction_id");
        // 在当前AI势力中查找
        var aiData = game.getAiFactions().get(fid);
        if (aiData != null && aiData.getFactionState() != null) {
            FactionState oldFs = game.getFactionState();
            // 把当前玩家放回AI
            AiFactionData oldAi = new AiFactionData();
            oldAi.setFactionState(oldFs);
            oldAi.setRegion(engine.getFaction(game.getPlayerFactionId()).map(FactionDefinition::getRegion).orElse(""));
            game.getAiFactions().put(game.getPlayerFactionId(), oldAi);
            // 切到新势力
            game.setFactionState(aiData.getFactionState());
            game.setPlayerFactionId(fid);
            game.getAiFactions().remove(fid);
            game.setActionPoints(game.getApMax());
        } else if (game.getPlayerFactionId().equals(fid)) {
            return Map.of("error", "已是当前势力");
        } else {
            return Map.of("error", "目标势力不存在或已灭亡");
        }
        autoSave();
        return buildPanelResponse();
    }

    /** GET /api/debug/rankings — 全势力排行榜 */
    @GetMapping("/debug/rankings")
    public Map<String, Object> debugRankings() {
        if (game == null) return Map.of("error", "无存档");
        List<Map<String, Object>> list = new ArrayList<>();
        // 玩家
        FactionState pfs = game.getFactionState();
        list.add(rankEntry(game.getPlayerFactionId(), pfs.getName(), pfs, true));
        // AI
        for (var ae : game.getAiFactions().entrySet()) {
            if (game.getDefeatedFactions().contains(ae.getKey())) continue;
            FactionState afs = ae.getValue().getFactionState();
            if (afs == null) continue;
            list.add(rankEntry(ae.getKey(), afs.getName(), afs, false));
        }
        list.sort((a,b) -> Integer.compare(
            ((Number)b.get("score")).intValue(), ((Number)a.get("score")).intValue()));
        return Map.of("rankings", list);
    }
    private Map<String,Object> rankEntry(String fid, String name, FactionState fs, boolean isPlayer) {
        Stats s = fs.getStats();
        int units = fs.getUnits() != null ? (int)fs.getUnits().stream().filter(Unit::isActive).count() : 0;
        int terrs = fs.getTerritories() != null ? fs.getTerritories().size() : 0;
        int score = s.getMilitary() * 3 + s.getEconomy() * 2 + s.getIndustry() + terrs * 2 + units * 5 + fs.getTreasury() / 10;
        Map<String,Object> e = new LinkedHashMap<>();
        e.put("fid",fid); e.put("name",name); e.put("is_player",isPlayer);
        e.put("score",score); e.put("military",s.getMilitary()); e.put("economy",s.getEconomy());
        e.put("industry",s.getIndustry()); e.put("territories",terrs); e.put("units",units);
        e.put("treasury",fs.getTreasury()); e.put("support",fs.getPopulationSupport());
        e.put("corruption",fs.getCorruption());
        return e;
    }

    /** POST /api/exit — 退出桌面应用 */
    @PostMapping("/exit")
    public Map<String, Object> exit() {
        autoSave();
        // 延迟关闭，等响应返回
        new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            System.exit(0);
        }).start();
        return Map.of("message", "Server shutting down...");
    }

    private String getNameForFid(String fid) {
        var f = engine.getFaction(fid).orElse(null);
        if (f != null) return f.getName();
        var n = engine.getGameData().getNpcFactions().get(fid);
        if (n != null) return n.getName();
        var h = engine.getGameData().getHostileNpcs().get(fid);
        if (h != null) return h.getName();
        return null;
    }

    private void autoSave() {
        try { saveRepo.save("auto", game); } catch (Exception ignored) {}
    }
}
