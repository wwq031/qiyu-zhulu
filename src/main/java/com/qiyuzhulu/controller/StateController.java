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
        // 内政建设 2.1-2.11
        if (action.matches("2\\.\\d+")) {
            String locPid = body.get("location_pid") != null ? body.get("location_pid").toString() : null;
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

        // 设计局 — 自定义战术 1.5.1
        if ("1.5.1".equals(action)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) body.get("meta");
            if (meta == null) { resp = buildPanelResponse(); resp.put("output", "缺少 meta 参数"); return resp; }
            Map<String, Object> result = military.registerCustomTactic(game, meta);
            resp = buildPanelResponse();
            resp.put("result_type", Boolean.TRUE.equals(result.get("ok")) ? "ok" : "error");
            resp.put("output", result.get("message"));
            return resp;
        }
        // 设计局 — 自定义兵种 1.5.2
        if ("1.5.2".equals(action)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) body.get("meta");
            if (meta == null) { resp = buildPanelResponse(); resp.put("output", "缺少 meta 参数"); return resp; }
            Map<String, Object> result = military.registerCustomUnitType(game, meta);
            resp = buildPanelResponse();
            resp.put("result_type", Boolean.TRUE.equals(result.get("ok")) ? "ok" : "error");
            resp.put("output", result.get("message"));
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
            autoSave();
            Map<String, Object> resp = buildPanelResponse();
            resp.put("message", "新游戏已创建：" + game.getFactionState().getName());
            resp.put("policies", game.getPhase1Policies());
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
        resp.put("national_spirit", engine.getNationalSpirit(faction));
        resp.put("stats", fs.getStats());
        resp.put("treasury", fs.getTreasury());
        resp.put("population_support", fs.getPopulationSupport());
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
                camps.add(ci);
            }
        }
        resp.put("active_campaigns", camps);

        resp.put("panel_text", renderer.render(g));
        resp.put("game_over", false);
        resp.put("enacted_resolutions", g.getEnactedResolutions());
        resp.put("researched_techs", g.getResearchedTechs());
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

    private void autoSave() {
        try { saveRepo.save("auto", game); } catch (Exception ignored) {}
    }
}
