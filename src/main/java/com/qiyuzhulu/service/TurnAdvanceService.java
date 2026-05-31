package com.qiyuzhulu.service;

import com.qiyuzhulu.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 回合推进服务。对应 Python qiyu_actions_civil.py 中的 process_turn_advance。
 */
@Service
public class TurnAdvanceService {

    private final GameEngine engine;
    private final PanelRenderer renderer;
    private final AiFactionService aiService;
    private final EventService eventService;
    private final CampaignService campaignService;

    public TurnAdvanceService(GameEngine engine, PanelRenderer renderer,
                               AiFactionService aiService, EventService eventService,
                               CampaignService campaignService) {
        this.engine = engine;
        this.renderer = renderer;
        this.aiService = aiService;
        this.eventService = eventService;
        this.campaignService = campaignService;
    }

    /**
     * 推进一个回合。
     * @return 事件消息列表（前端展示用）
     */
    public List<String> advance(GameState state) {
        List<String> events = new ArrayList<>();

        // 清除上回合临时数据
        state.getEventsThisTurn().clear();
        state.getEpicEventsThisTurn().clear();
        state.getCampaignResultsThisTurn().clear();
        state.setMovingUnitsAdvancedThisTurn(false);

        FactionState fs = state.getFactionState();

        // 1. 互不侵犯协议倒计时
        Map<String, Integer> pacts = state.getNonAggressionPacts();
        List<String> expired = new ArrayList<>();
        for (var entry : pacts.entrySet()) {
            if (entry.getValue() <= 1) expired.add(entry.getKey());
        }
        expired.forEach(pacts::remove);
        pacts.replaceAll((k, v) -> v - 1);

        // 2. 建设队列推进
        List<String> completedMsgs = processConstructionQueue(state);
        events.addAll(completedMsgs);

        // 3. 训练队列推进
        List<String> trainingMsgs = processTrainingQueue(state);
        events.addAll(trainingMsgs);

        // 4. 清理被歼灭/投降的部队
        List<Unit> units = fs.getUnits();
        if (units != null) {
            List<Unit> removed = new ArrayList<>();
            List<Unit> kept = new ArrayList<>();
            for (Unit u : units) {
                if ("annihilated".equals(u.getStatus()) || "surrendered".equals(u.getStatus())) {
                    removed.add(u);
                } else {
                    kept.add(u);
                }
            }
            if (!removed.isEmpty()) {
                fs.setUnits(kept);
                fs.setArmy(engine.recountArmyFromUnits(kept));
                for (Unit rm : removed) {
                    events.add("💀 " + rm.getName() + " 覆灭");
                }
            }
        }

        // 5. 溃散部队恢复
        if (units != null) {
            for (Unit u : units) {
                if ("routed".equals(u.getStatus())) {
                    int routedTurns = u.getRoutedTurns() - 1;
                    u.setRoutedTurns(routedTurns);
                    if (routedTurns <= 0) {
                        u.setStatus("ready");
                        events.add("🔄 " + u.getName() + " 从溃散中恢复");
                    }
                }
            }
        }

        // 6. 收入 & 维持费
        int income = engine.calcIncome(fs);
        int maintenance = engine.calcTotalMaintenance(fs);
        // 贸易协定收入
        int tradeIncome = 0;
        for (var dr : state.getDiplomaticRelations().entrySet()) {
            var rel = dr.getValue();
            if ("trade".equals(rel.getPact()) && rel.getTurnsLeft() > 0) {
                tradeIncome += 3;
                rel.setTurnsLeft(rel.getTurnsLeft() - 1);
                if (rel.getTurnsLeft() <= 0) rel.setPact(null);
            }
        }
        fs.setTreasury(fs.getTreasury() + income - maintenance + tradeIncome);
        if (tradeIncome > 0) events.add("📈 贸易协定本回合带来" + tradeIncome + "💰收入");

        // 赤字惩罚
        if (fs.getTreasury() < 0) {
            events.add("⚠ 国库亏空！全军士气-10，兵力-3");
            if (units != null) {
                for (Unit u : units) {
                    if (u.isActive()) {
                        u.setMorale(Math.max(5, u.getMorale() - 10));
                        u.setStrength(Math.max(1, u.getStrength() - 3));
                    }
                }
            }
            fs.setTreasury(0);
        }

        // 6b. 自动占领（检查部队当前位置的无主地块）
        for (Unit u : fs.getUnits()) {
            if (!u.isActive() || "fighting".equals(u.getStatus())) continue;
            String pid = engine.resolvePositionToPid(u.getPosition());
            if (pid != null) {
                String claimMsg = engine.autoClaimArrival(state, u, pid);
                if (claimMsg != null) events.add(claimMsg);
            }
        }

        // 6c. 补给系统
        List<String> supplyMsgs = processUnitSupply(state);
        events.addAll(supplyMsgs);

        // 7. 民心自然波动
        int supportChange = new Random().nextInt(5) - 2;
        fs.setPopulationSupport(GameEngine.clamp(fs.getPopulationSupport() + supportChange, 0, 100));

        // 7b. 低民心叛乱/分离
        events.addAll(checkPopularUnrest(state, fs));

        // 7b. AI初始化（首次）
        aiService.initialize(state);

        // 7c. 世界传言
        events.addAll(generateRumors(state, fs));

        // 7d. AI势力回合处理
        List<String> aiResults = aiService.process(state);
        events.addAll(aiResults);

        // 7d. 随机事件
        List<String> eventResults = eventService.trigger(state);
        events.addAll(eventResults);

        // 7e. 史诗事件
        List<Map<String, Object>> epicResults = eventService.checkEpic(state);
        state.getEpicEventsThisTurn().addAll(
                epicResults.stream().map(e -> (String) e.getOrDefault("name", "")).toList());

        // 7f. AI投降检查
        for (var surr : engine.checkAiSurrender(state)) {
            String fid = surr.get("fid");
            String atkFid = surr.get("attacker_fid");
            Map<String, Object> result = engine.executeSurrender(state, fid, atkFid);
            if (result != null) events.add((String) result.getOrDefault("faction_name", "") + " 降伏！");
        }

        // 7g. 战役结算
        CampaignService.BattleResult br = campaignService.resolveAllCampaigns(state);
        events.addAll(br.messages);
        // 转换结构化结果为前端需要的格式
        state.getCampaignResultsThisTurn().clear();
        for (Map<String, Object> s : br.structured) {
            CampaignResult cr = new CampaignResult();
            cr.setId((String) s.get("id"));
            cr.setProvince((String) s.get("province"));
            cr.setProvinceName((String) s.get("province_name"));
            cr.setOutcome((String) s.get("outcome"));
            cr.setOutcomeCn((String) s.get("outcome_cn"));
            cr.setRound(((Number) s.get("round")).intValue());
            cr.setRatio(((Number) s.get("ratio")).doubleValue());
            cr.setAtkCasualties(((Number) s.get("atk_casualties")).intValue());
            cr.setDefCasualties(((Number) s.get("def_casualties")).intValue());
            cr.setProvinceFell(Boolean.TRUE.equals(s.get("province_fell")));
            cr.setAttackerFaction((String) s.get("attacker_faction"));
            cr.setDefenderFaction((String) s.get("defender_faction"));
            cr.setAttackerName((String) s.get("attacker_name"));
            cr.setDefenderName((String) s.get("defender_name"));
            @SuppressWarnings("unchecked")
            List<String> atkUnits = (List<String>) s.get("attacker_units");
            cr.setAttackerUnits(atkUnits != null ? atkUnits : List.of());
            cr.setPlayerAttacker(Boolean.TRUE.equals(s.get("is_player_attacker")));
            cr.setHonorAvailable(Boolean.TRUE.equals(s.get("honor_available")));
            cr.setHonorCost(((Number) s.get("honor_cost")).intValue());
            cr.setMessage((String) s.get("message"));
            state.getCampaignResultsThisTurn().add(cr);
        }

        // 8. 阶段推进
        int phase = state.getPhase();
        if (phase == 2 && state.getTurn() >= 2) {
            state.setPhase(3);
            events.add("📜 帝国正式崩溃！各地军阀进入区域统一战阶段。");
        } else if (phase == 3 && engine.isRegionUnified(state, state.getPlayerFactionId())) {
            state.setPhase(4);
            events.add("👑 本区已统一！七强并立时代开启。");
        }

        // 9. 更新回合计数和日期
        state.setTurn(state.getTurn() + 1);
        int turn = state.getTurn();
        int year = 1910 + turn / 12;
        int month = 3 + turn % 12;
        if (month > 12) { year++; month -= 12; }
        state.setGameDate(String.format("%04d-%02d", year, month));

        // 10. 重置AP
        state.setActionPoints(state.getApMax());

        // 11. 统计追踪
        @SuppressWarnings("unchecked")
        Map<String, Object> tracker = (Map<String, Object>) state.getStatsTracker();
        if (tracker != null) {
            tracker.put("total_turns", ((Number) tracker.getOrDefault("total_turns", 0)).intValue() + 1);
            tracker.put("peak_military", Math.max(
                    ((Number) tracker.getOrDefault("peak_military", 0)).intValue(),
                    fs.getStats().getMilitary()));
            tracker.put("peak_economy", Math.max(
                    ((Number) tracker.getOrDefault("peak_economy", 0)).intValue(),
                    fs.getStats().getEconomy()));
            tracker.put("peak_industry", Math.max(
                    ((Number) tracker.getOrDefault("peak_industry", 0)).intValue(),
                    fs.getStats().getIndustry()));
            tracker.put("max_territory_count", Math.max(
                    ((Number) tracker.getOrDefault("max_territory_count", 0)).intValue(),
                    fs.getTerritories().size()));
        }

        // 存储本回合事件列表（供前端渲染用）
        state.setEventsThisTurn(events);

        return events;
    }

    /** 推进建设队列，返回完成消息 */
    private List<String> processConstructionQueue(GameState state) {
        List<String> completed = new ArrayList<>();
        FactionState fs = state.getFactionState();
        List<ConstructionItem> queue = state.getConstructionQueue();
        List<ConstructionItem> remaining = new ArrayList<>();

        for (ConstructionItem item : queue) {
            item.setTurnsLeft(item.getTurnsLeft() - 1);
            if (item.getTurnsLeft() <= 0) {
                // 完成！
                completed.add("✅ " + item.getName() + " 建设完成！ " + renderer.formatEffects(item.getEffect()));

                // 应用效果
                Map<String, Integer> effect = item.getEffect();
                if (effect != null) {
                    Stats stats = fs.getStats();
                    for (var entry : effect.entrySet()) {
                        String k = entry.getKey();
                        int v = entry.getValue();
                        switch (k) {
                            case "infantry", "cavalry", "artillery", "engineer", "naval" -> {
                                Map<String, Integer> army = fs.getArmy();
                                if (army != null) army.merge(k, v, Integer::sum);
                            }
                            case "military_tech" -> fs.setMilitaryTech(Math.min(10, fs.getMilitaryTech() + v));
                            case "naval_power" -> stats.setNavalPower(GameEngine.clamp(stats.getNavalPower() + v));
                            default -> {
                                if (GameEngine.STAT_NAMES.containsKey(k)) {
                                    stats.set(k, GameEngine.clamp(stats.get(k) + v));
                                }
                            }
                        }
                    }
                }

                // 地块级建筑
                String bk = item.getBuildingKey();
                String lp = item.getLocationPid();
                if (bk != null && lp != null) {
                    fs.addBuilding(lp, bk, 1);
                }

                // 科技研发
                if (item.getTechId() != null) {
                    state.getResearchedTechs().add(item.getTechId());
                    String techMsg = "🔬 " + fs.getName() + " 完成了「" + item.getTechName() + "」研发！";
                    state.getTechEventsThisTurn().add(techMsg);
                }
            } else {
                remaining.add(item);
            }
        }
        state.setConstructionQueue(remaining);
        return completed;
    }

    /** 推进训练队列，返回完成消息 */
    private List<String> processTrainingQueue(GameState state) {
        List<String> completed = new ArrayList<>();
        FactionState fs = state.getFactionState();
        List<TrainingItem> queue = state.getTrainingQueue();
        List<TrainingItem> remaining = new ArrayList<>();

        for (TrainingItem item : queue) {
            item.setTurnsLeft(item.getTurnsLeft() - 1);
            if (item.getTurnsLeft() <= 0) {
                // 训练完成，创建部队
                String unitType = item.getUnitType();
                @SuppressWarnings("unchecked")
                Map<String, Object> ut = (Map<String, Object>) GameEngine.UNIT_TYPES.getOrDefault(unitType,
                        GameEngine.UNIT_TYPES.get("infantry"));

                // 检查是否有自定义兵种
                Map<String, CustomUnitType> custom = state.getCustomUnitTypes();
                if (custom != null && custom.containsKey(unitType)) {
                    CustomUnitType cut = custom.get(unitType);
                    Unit u = createUnit(cut.getName(), unitType, item.getLocation(),
                            cut.getAtk(), cut.getDef(), cut.getMorale(), cut.getExp());
                    fs.getUnits().add(u);
                    completed.add("✅ " + cut.getName() + " 训练完成！部署于 " + item.getLocationName());
                } else {
                    int atk = ((Number) ut.get("atk_bonus")).intValue() + 7;
                    int def = ((Number) ut.get("def_bonus")).intValue() + 5;
                    int morale = 55;
                    int exp = 20;
                    String typeName = (String) ut.get("name");

                    // 统计番号
                    Map<String, Integer> serial = fs.getUnitSerial();
                    if (serial == null) serial = new HashMap<>();
                    serial.merge("total", 1, Integer::sum);
                    serial.merge(unitType, 1, Integer::sum);
                    fs.setUnitSerial(serial);

                    String unitName = (fs.getUnitPrefix() != null ? fs.getUnitPrefix() : "新编")
                            + typeName + "第" + serial.getOrDefault(unitType, 1) + ut.get("suffix");

                    Unit u = createUnit(unitName, unitType, item.getLocation(), atk, def, morale, exp);
                    fs.getUnits().add(u);
                    completed.add("✅ " + unitName + " 训练完成！部署于 " + item.getLocationName());
                }

                // 更新军力统计
                Map<String, Integer> army = fs.getArmy();
                if (army != null) army.merge(unitType, 1, Integer::sum);

                // 军事属性增长
                int militaryGain = ((Number) ut.getOrDefault("military_gain", 5)).intValue();
                fs.getStats().set("military", GameEngine.clamp(fs.getStats().getMilitary() + militaryGain));

            } else {
                remaining.add(item);
            }
        }
        state.setTrainingQueue(remaining);
        return completed;
    }

    private Unit createUnit(String name, String type, String pos, int atk, int def, int morale, int exp) {
        Unit u = new Unit();
        u.setName(name);
        u.setType(type);
        u.setAttack(atk);
        u.setDefense(def);
        u.setMorale(morale);
        u.setExperience(exp);
        u.setPosition(pos);
        u.setSpeed("cavalry".equals(type) ? 2 : 1);
        u.setStrength(100);
        u.setMaxStrength(100);
        u.setStatus("ready");
        return u;
    }

    /** 民心叛乱检查 */
    private List<String> checkPopularUnrest(GameState state, FactionState fs) {
        List<String> msgs = new ArrayList<>();
        int support = fs.getPopulationSupport();
        Random rng = new Random();

        if (support < 10 && rng.nextDouble() < 0.3) {
            // 军事哗变
            List<Unit> active = fs.getActiveUnits();
            if (!active.isEmpty()) {
                Unit deserter = active.get(rng.nextInt(active.size()));
                if (rng.nextDouble() < 0.5) {
                    deserter.setStatus("annihilated");
                    msgs.add("🔴 民心崩溃(" + support + "%)！" + deserter.getName() + " 发生哗变，部队溃散！");
                } else {
                    deserter.setMorale(Math.max(5, deserter.getMorale() - 30));
                    msgs.add("🔴 民心崩溃(" + support + "%)！" + deserter.getName() + " 士气暴跌！");
                }
            }
        }
        if (support < 20 && rng.nextDouble() < 0.12) {
            // 分离主义
            List<String> terrs = fs.getTerritories();
            if (terrs != null && !terrs.isEmpty()) {
                String seceding = terrs.get(rng.nextInt(terrs.size()));
                terrs.remove(seceding);
                msgs.add("🟠 民怨沸腾(" + support + "%)！" + seceding + " 宣告脱离" + fs.getName() + "独立！");
            }
        }
        if (support < 30 && rng.nextDouble() < 0.15) {
            msgs.add("⚠ 民心低迷(" + support + "%)，各地不满情绪正在蔓延。");
        }
        return msgs;
    }

    /** 生成世界传言 */
    private List<String> generateRumors(GameState state, FactionState fs) {
        List<String> rumors = new ArrayList<>();
        Random rng = new Random();
        String[] templates = {
            "📰 据传，COLUMN1正在秘密扩充军备。",
            "📰 COLUMN1的特使被目击出现在COLUMN2首都。",
            "📰 列强银行团向COLUMN1提供了新一轮贷款。",
            "📰 COLUMN1境内爆发了小规模农民起义，但被迅速镇压。",
            "📰 国际市场上橡胶价格暴涨，COLUMN1港口的商船数量翻了一倍。",
            "📰 COLUMN1与COLUMN2在边境发生了小规模武装冲突。",
            "📰 某大国驻COLUMN1领事馆遭到不明身份者袭击。",
        };
        if (rng.nextDouble() < 0.4 && rumors.size() < 2) {
            String t = templates[rng.nextInt(templates.length)];
            var aiFactions = new ArrayList<>(state.getAiFactions().entrySet());
            String col1 = fs.getName();
            String col2 = !aiFactions.isEmpty()
                    ? aiFactions.get(rng.nextInt(aiFactions.size())).getValue().getFactionState().getName()
                    : "邻国";
            rumors.add(t.replace("COLUMN1", col1).replace("COLUMN2", col2));
        }
        return rumors;
    }

    /** 补给系统：每回合检查部队补给状态 */
    private List<String> processUnitSupply(GameState state) {
        List<String> msgs = new ArrayList<>();
        FactionState fs = state.getFactionState();
        List<String> terrNames = fs.getTerritories();
        List<Unit> units = fs.getUnits();
        if (units == null) return msgs;
        for (Unit u : units) {
            if (!u.isActive() || "fighting".equals(u.getStatus())) continue;
            Object[] s = engine.calcSupply(engine.resolvePositionToPid(u.getPosition()), terrNames);
            String lvl = (String) s[0];
            u.setSupply(lvl);
            switch (lvl) {
                case "isolated" -> {
                    u.setMorale(Math.max(5, u.getMorale() - 15));
                    u.setStrength(Math.max(1, u.getStrength() - 5));
                    msgs.add("⚠ " + u.getName() + " 补给断绝！士气-15 兵力-5");
                }
                case "cut_off" -> {
                    u.setMorale(Math.max(10, u.getMorale() - 5));
                    msgs.add("⚠ " + u.getName() + " 补给线中断！士气-5");
                }
                case "strained" -> u.setMorale(Math.max(15, u.getMorale() - 2));
            }
        }
        return msgs;
    }
}
