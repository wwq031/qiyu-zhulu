package com.qiyuzhulu.service;

import com.qiyuzhulu.model.*;
import com.qiyuzhulu.repo.GameDataRepo;
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
    private final GameDataRepo gameData;
    private final Random rng = new Random();

    public TurnAdvanceService(GameEngine engine, PanelRenderer renderer,
                               AiFactionService aiService, EventService eventService,
                               CampaignService campaignService, GameDataRepo gameData) {
        this.engine = engine;
        this.renderer = renderer;
        this.aiService = aiService;
        this.eventService = eventService;
        this.campaignService = campaignService;
        this.gameData = gameData;
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

        // 6. 腐败度增长
        int corruption = fs.getCorruption();
        int corrGrowth = rng.nextInt(3) + 1; // +1~3
        if (fs.getTreasury() > 500) corrGrowth += 1;
        int lastTurnTerrs = fs.getTerritories() != null ? fs.getTerritories().size() : 0;
        // 检查扩张速度（简化：单回合+3省）
        corrGrowth += 0; // 需要上回合领土记录，先跳过
        fs.setCorruption(GameEngine.clamp(corruption + corrGrowth, 0, 100));

        // 6b. 收入 & 维持费（腐败修正）
        double corrTaxMult = corruption > 70 ? 0.80 : corruption > 50 ? 0.90 : corruption > 30 ? 0.90 : 1.0;
        double corrMaintMult = corruption > 90 ? 1.20 : corruption > 50 ? 1.15 : 1.0;
        int income = (int)(engine.calcIncome(fs) * corrTaxMult);
        int maintenance = (int)(engine.calcTotalMaintenance(fs) * corrMaintMult);
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
        if (corruption > 30) events.add("⚠ 腐败度" + corruption + "%，税收效率下降，建设成本上升");
        if (corruption > 90 && rng.nextDouble() < 0.15 && fs.getActiveUnits().size() > 0) {
            Unit u = fs.getActiveUnits().get(rng.nextInt(fs.getActiveUnits().size()));
            u.setStatus("annihilated");
            events.add("🔴 腐败横行！" + u.getName() + " 因克扣粮饷发生哗变！");
        }
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

    /** 立即结算训练队列（提前服役用） */
    public List<String> flushTrainingQueue(GameState state) {
        return processTrainingQueue(state);
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
                    // 提前服役惩罚
                    if (item.isEarlyDeploy()) {
                        u.setStrength((int)(100 * 0.6));
                        u.setMorale(Math.max(10, u.getMorale() - 25));
                        u.setExperience(Math.max(1, u.getExperience() - 15));
                        completed.add("⚡ " + unitName + " 提前服役 @" + item.getLocationName() + "（兵力60% 士气-25 经验-15）");
                    } else {
                        completed.add("✅ " + unitName + " 训练完成！部署于 " + item.getLocationName());
                    }
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

    /** 生成世界传言（对标 Python get_rumors + _generate_dynamic_rumors） */
    @SuppressWarnings("unchecked")
    private List<String> generateRumors(GameState state, FactionState fs) {
        List<String> rumors = new ArrayList<>();
        Map<String, Object> bg = (Map<String, Object>) state.getBackgroundSimulation();
        if (bg == null) { state.setBackgroundSimulation(new LinkedHashMap<>()); bg = (Map<String, Object>) state.getBackgroundSimulation(); }
        int turn = state.getTurn();

        // 预设时间线传闻
        Map<String, Object> regions = (Map<String, Object>) bg.get("regions");
        if (regions != null) {
            for (var re : regions.entrySet()) {
                String rid = re.getKey();
                Map<String, Object> rdata = (Map<String, Object>) re.getValue();
                List<Map<String, Object>> timeline = (List<Map<String, Object>>) rdata.get("timeline");
                if (timeline != null) {
                    for (Map<String, Object> evt : timeline) {
                        if (((Number) evt.get("turn")).intValue() == turn) {
                            String rname = GameEngine.REGION_NAMES.getOrDefault(rid, rid);
                            rumors.add(rname + "：" + evt.get("narrative"));
                        }
                    }
                }
            }
        }
        List<Map<String, Object>> crossEvents = (List<Map<String, Object>>) bg.get("cross_region_events");
        if (crossEvents != null) {
            for (Map<String, Object> evt : crossEvents) {
                if (((Number) evt.get("turn")).intValue() == turn) rumors.add((String) evt.get("narrative"));
            }
        }
        List<Map<String, Object>> foreignEvents = (List<Map<String, Object>>) bg.get("foreign_interventions");
        if (foreignEvents != null) {
            for (Map<String, Object> evt : foreignEvents) {
                if (((Number) evt.get("turn")).intValue() == turn) rumors.add((String) evt.get("narrative"));
            }
        }

        // 动态传闻回退
        if (rumors.isEmpty()) rumors = generateDynamicRumors(state, fs);
        return rumors;
    }

    @SuppressWarnings("unchecked")
    private List<String> generateDynamicRumors(GameState state, FactionState fs) {
        List<String> rumors = new ArrayList<>();
        String playerRegion = engine.getPlayerRegion(state);
        Set<String> defeated = new HashSet<>(state.getDefeatedFactions());

        List<Object[]> candidates = new ArrayList<>(); // [category, name, region, territories, mil]
        for (var ae : state.getAiFactions().entrySet()) {
            if (defeated.contains(ae.getKey())) continue;
            FactionState afs = ae.getValue().getFactionState();
            if (afs == null || afs.getTerritories() == null || afs.getTerritories().isEmpty()) continue;
            int mil = afs.getStats().getMilitary();
            String region = "";
            var af = engine.getFaction(ae.getKey()).orElse(null);
            if (af != null) region = af.getRegion();
            if (mil >= 50) candidates.add(new Object[]{"strong", afs.getName(), region, afs.getTerritories(), mil});
            else if (mil >= 30) candidates.add(new Object[]{"medium", afs.getName(), region, afs.getTerritories(), mil});
            else if (rng.nextDouble() < 0.4) candidates.add(new Object[]{"weak", afs.getName(), region, afs.getTerritories(), mil});
        }

        // 邻区边境威胁
        Map<String, List<String>> adj = Map.of(
                "northeast", List.of("huabei"), "huabei", List.of("northeast","southeast","xibei"),
                "southeast", List.of("huabei","lingnan"), "lingnan", List.of("southeast","southwest","nanyang"),
                "southwest", List.of("lingnan","xibei"), "xibei", List.of("huabei","southwest"),
                "nanyang", List.of("southeast","lingnan"));
        List<String> neighbors = adj.getOrDefault(playerRegion, List.of());
        List<Object[]> threats = candidates.stream()
                .filter(c -> neighbors.contains((String) c[2]) && (int) c[4] >= 35).toList();
        if (!threats.isEmpty() && rng.nextDouble() < 0.6) {
            Object[] t = threats.get(rng.nextInt(threats.size()));
            String rname = GameEngine.REGION_NAMES.getOrDefault((String) t[2], "邻区");
            List<String> terrs = (List<String>) t[3];
            rumors.add(rname + "：" + t[1] + "在" + (terrs.isEmpty() ? "边境" : terrs.get(0)) + "集结约" + ((int) t[4] / 5) + "个团，边境局势趋紧。");
        }

        // 扩张动态
        List<Object[]> expanding = candidates.stream()
                .filter(c -> "strong".equals(c[0]) && ((List<String>) c[3]).size() >= 3).toList();
        if (!expanding.isEmpty() && rng.nextDouble() < 0.5) {
            Object[] e = expanding.get(rng.nextInt(expanding.size()));
            String rname = GameEngine.REGION_NAMES.getOrDefault((String) e[2], "某区");
            List<String> terrs = (List<String>) e[3];
            rumors.add(rname + "：" + e[1] + "已控制" + terrs.size() + "处领地，势力持续膨胀。");
        }

        if (rumors.isEmpty()) {
            rumors.add("各方势力按兵不动，暗流涌动。" );
            rumors.add("列强使馆区内灯火通明，密使往来不断。");
        }
        return rumors.subList(0, Math.min(2, rumors.size()));
    }

    /** 生成背景推演（新游戏时调用，对标 Python generate_background_simulation） */
    @SuppressWarnings("unchecked")
    public void initBackgroundSimulation(GameState state) {
        String playerRegion = engine.getPlayerRegion(state);
        if (playerRegion == null || playerRegion.isEmpty()) return;

        Map<String, Object> bg = new LinkedHashMap<>();
        bg.put("player_region", playerRegion);
        Map<String, Object> regions = new LinkedHashMap<>();
        List<Map<String, Object>> crossEvents = new ArrayList<>();
        List<Map<String, Object>> foreignEvents = new ArrayList<>();
        bg.put("regions", regions);
        bg.put("cross_region_events", crossEvents);
        bg.put("foreign_interventions", foreignEvents);

        // 为非玩家区生成预测胜者+时间线
        for (String rid : GameEngine.REGION_IDS) {
            if (rid.equals(playerRegion)) continue;
            var rFactions = new ArrayList<>(gameData.getFactions().values().stream()
                    .filter(f -> rid.equals(f.getRegion())).toList());
            var rNpcs = new ArrayList<>(gameData.getHostileNpcs().values().stream()
                    .filter(n -> rid.equals(n.getRegion())).toList());

            // 评分排序
            record Scored(String name, int military, int industry, int economy, int ideology, int diplomacy, boolean isFaction) {}
            List<Scored> scored = new ArrayList<>();
            for (var f : rFactions) scored.add(new Scored(f.getName(), f.getStats().getMilitary(), f.getStats().getIndustry(), f.getStats().getEconomy(), f.getStats().getIdeology(), f.getStats().getDiplomacy(), true));
            for (var n : rNpcs) scored.add(new Scored(n.getName(), n.getStats().getMilitary(), n.getStats().getIndustry(), n.getStats().getEconomy(), n.getStats().getIdeology(), n.getStats().getDiplomacy(), false));
            scored.sort((a, b) -> Double.compare(
                    b.military*0.4+b.industry*0.2+b.economy*0.2+b.ideology*0.1+b.diplomacy*0.1,
                    a.military*0.4+a.industry*0.2+a.economy*0.2+a.ideology*0.1+a.diplomacy*0.1));

            if (scored.isEmpty()) continue;
            // 60%/30%/8% 概率选择胜者
            Scored winner = scored.get(0); // fallback = highest score
            double roll = rng.nextDouble();
            double cumulative = 0;
            for (int i = 0; i < scored.size(); i++) {
                double prob = i == 0 ? 0.60 : i == 1 ? 0.30 : i == 2 ? 0.08 : 0.02 / Math.max(1, scored.size()-3);
                cumulative += prob;
                if (roll < cumulative) { winner = scored.get(i); break; }
            }
            int winnerMil = winner.military;
            int baseTurns = 12 + rng.nextInt(13) - 4;
            int bonus = Math.max(0, (winnerMil - 30) / 10);
            int uniTurn = Math.max(6, baseTurns - bonus);
            int t1 = Math.max(2, uniTurn / 3 + rng.nextInt(4) - 1);
            int t2 = Math.max(t1 + 2, uniTurn * 2 / 3 + rng.nextInt(5) - 2);
            String rname = GameEngine.REGION_NAMES.getOrDefault(rid, rid);
            String rivalName = scored.size() > 1 ? scored.get(1).name : "地方武装";
            String rTerrain = "平原"; // simplified

            List<Map<String, Object>> timeline = new ArrayList<>();
            timeline.add(Map.of("turn", t1, "type", "conflict", "narrative", winner.name + "在" + rTerrain + "地带击溃" + rivalName + "，控制" + rname + "北部。"));
            timeline.add(Map.of("turn", t2, "type", "conflict", "narrative", winner.name + "攻占" + rname + "重镇，" + rivalName + "残部退守边境。"));
            timeline.add(Map.of("turn", uniTurn, "type", "unification", "narrative", winner.name + "肃清" + rname + "全境，宣布统一。"));

            regions.put(rid, Map.of("winner_name", (Object) winner.name, "unification_turn", uniTurn, "timeline", (Object) timeline));
        }

        // 跨区事件
        String[][] pairs = {{"northeast","huabei"},{"huabei","southeast"},{"huabei","xibei"},{"southeast","lingnan"},{"southwest","lingnan"},{"southwest","xibei"},{"lingnan","nanyang"},{"southeast","nanyang"}};
        for (String[] pair : pairs) {
            if (rng.nextDouble() < 0.30) {
                String wa = getWinnerName(regions, pair[0]);
                String wb = getWinnerName(regions, pair[1]);
                int t = rng.nextInt(13) + 8;
                crossEvents.add(Map.of("turn", t, "type", "war", "narrative", wa + "与" + wb + "在边界爆发冲突，双方互有攻守。"));
            }
        }

        // 列强干涉
        Map<String, String> fpMap = Map.of("northeast","日本","huabei","日本","xibei","俄国","southwest","英国","lingnan","法国","nanyang","英国","southeast","美国");
        for (String rid : GameEngine.REGION_IDS) {
            if (rid.equals(playerRegion)) continue;
            String power = fpMap.get(rid);
            if (power != null && rng.nextDouble() < 0.35) {
                int t = rng.nextInt(11) + 5;
                foreignEvents.add(Map.of("turn", t, "power", power, "narrative", power + "以护侨为名向" + GameEngine.REGION_NAMES.getOrDefault(rid, rid) + "增兵，干涉风险上升。"));
            }
        }

        state.setBackgroundSimulation(bg);
    }

    private String getWinnerName(Map<String, Object> regions, String rid) {
        Map<String, Object> r = (Map<String, Object>) regions.get(rid);
        if (r != null && r.get("winner_name") != null) return (String) r.get("winner_name");
        return GameEngine.REGION_NAMES.getOrDefault(rid, rid);
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
