package com.qiyuzhulu.service;

import com.qiyuzhulu.model.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 战役系统 — 发动、结算、增援、撤退、授勋。
 * 对应 Python qiyu_actions_military.py 第382-1585行。
 */
@Service
public class CampaignService {

    private final GameEngine engine;
    private final Random rng = new Random();

    /** 地形防御加成 */
    private static final Map<String, Double> TERRAIN_BONUS = Map.ofEntries(
            Map.entry("山地", 1.3), Map.entry("高原", 1.3), Map.entry("雨林", 1.2),
            Map.entry("城市", 1.15), Map.entry("雪原", 1.2), Map.entry("沙漠", 1.1),
            Map.entry("戈壁", 1.1), Map.entry("长江水网", 1.15), Map.entry("黄河防线", 1.25),
            Map.entry("海峡", 1.2), Map.entry("群岛", 1.2), Map.entry("森林", 1.1),
            Map.entry("丘陵", 1.1), Map.entry("平原", 1.0), Map.entry("盆地", 1.0),
            Map.entry("港口", 0.9), Map.entry("沿海港口", 0.9), Map.entry("海岸线", 1.0),
            Map.entry("绿洲", 1.0), Map.entry("铁路网", 0.95)
    );

    /** 损伤比例(攻方,守方) */
    private static final Map<String, double[]> LOSS_MAPS = Map.of(
            "annihilate",    new double[]{0.08, 0.70},
            "decisive_win",  new double[]{0.15, 0.50},
            "costly_win",    new double[]{0.35, 0.35},
            "stalemate",     new double[]{0.20, 0.20},
            "setback",       new double[]{0.40, 0.12},
            "rout",          new double[]{0.55, 0.05}
    );

    /** 经验基础值 */
    private static final Map<String, Integer> BASE_EXP = Map.of(
            "annihilate", 12, "decisive_win", 10, "costly_win", 7,
            "stalemate", 4, "setback", 2, "rout", 1
    );

    /** 授勋效果 */
    private static final Map<String, Map<String, Integer>> HONOR_EFFECTS = Map.of(
            "annihilate",    Map.of("attack", 3, "morale", 5, "experience", 1),
            "decisive_win",  Map.of("attack", 2, "morale", 3, "experience", 1),
            "costly_win",    Map.of("attack", 1, "morale", 2, "experience", 0)
    );

    public CampaignService(GameEngine engine) {
        this.engine = engine;
    }

    // ═══════════════════════════════════════════ 发动战役 ═══════════════════════════════════════════

    /**
     * 发动战役：攻击指定省份。
     * unitTactics: {unitName: tacticId} 或 null（默认assault）
     * 返回 (ok, message)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> startCampaign(GameState state, String provincePid,
                                              List<Integer> attackerUnitIndices,
                                              Map<String, String> unitTactics) {
        FactionState fs = state.getFactionState();
        List<Unit> myUnits = fs.getUnits();
        if (state.getActionPoints() < 1) return GameUtils.mapOf("ok", false, "message", "行动点不足");

        // 查找该省敌人
        EnemyProvince enemyInfo = null;
        List<EnemyProvince> enemies = listEnemyProvinces(state);
        for (EnemyProvince e : enemies) {
            if (provincePid.equals(e.getPid())) { enemyInfo = e; break; }
        }
        if (enemyInfo == null) {
            // 回退：检查是否为NPC地块
            String pname = engine.getProvince(provincePid).getName();
            if (pname != null) {
                for (var ne : engine.getGameData().getHostileNpcs().entrySet()) {
                    if (ne.getValue().getTerritories() != null && ne.getValue().getTerritories().contains(pname)) {
                        enemyInfo = new EnemyProvince(provincePid, pname, engine.getProvince(provincePid).getTerrain(),
                                engine.getProvince(provincePid).getType(), ne.getValue().getName(), ne.getKey(), "npc", false);
                        break;
                    }
                }
            }
            if (enemyInfo == null)
                return GameUtils.mapOf("ok", false, "message", "该省不是有效的攻击目标");
        }

        // 跨区/互不侵犯检查
        if ("faction".equals(enemyInfo.getOwnerType())) {
            String efid = enemyInfo.getOwnerFid();
            var ef = engine.getFaction(efid).orElse(null);
            var pf = engine.getFaction(state.getPlayerFactionId()).orElse(null);
            if (ef != null && pf != null && !ef.getRegion().equals(pf.getRegion())) {
                if (!engine.isRegionUnified(state, state.getPlayerFactionId()))
                    return GameUtils.mapOf("ok", false, "message", "尚未统一本区域，无法跨区作战");
            }
            if (engine.hasNonAggression(state, state.getPlayerFactionId(), efid))
                return GameUtils.mapOf("ok", false, "message", "与" + enemyInfo.getOwner() + "处于休战期，暂不可攻击");
        }
        if (enemyInfo.isInCampaign())
            return GameUtils.mapOf("ok", false, "message", "该省已有进行中的战役");

        if (attackerUnitIndices == null || attackerUnitIndices.isEmpty())
            return GameUtils.mapOf("ok", false, "message", "未选择攻击部队");

        if (unitTactics == null) unitTactics = Map.of();

        // 分类部队：距离0-1立即参战，距离2-3增援行军
        List<Unit> immediate = new ArrayList<>();
        List<Object[]> reinforcements = new ArrayList<>(); // [Unit, dist]
        for (int idx : attackerUnitIndices) {
            if (idx < 0 || idx >= myUnits.size())
                return GameUtils.mapOf("ok", false, "message", "无效部队索引：" + (idx + 1));
            Unit u = myUnits.get(idx);
            if ("routed".equals(u.getStatus()) || "annihilated".equals(u.getStatus()))
                return GameUtils.mapOf("ok", false, "message", u.getName() + "无法参战（状态：" + u.getStatus() + "）");
            String uPos = resolveUnitPid(u.getPosition());
            if (provincePid.equals(uPos)) {
                immediate.add(u);
            } else {
                Object[] distResult = engine.getDistance(uPos, provincePid);
                Number distNum = (Number) distResult[0];
                int dist = distNum != null ? distNum.intValue() : 999;
                if (dist > 3)
                    return GameUtils.mapOf("ok", false, "message", u.getName() + "距离目标太远（" + dist + "回合）");
                if (dist <= 1) immediate.add(u);
                else reinforcements.add(new Object[]{u, dist});
            }
        }
        if (immediate.isEmpty())
            return GameUtils.mapOf("ok", false, "message", "需要至少一支相邻部队发起进攻");

        state.setActionPoints(state.getActionPoints() - 1);

        // 构建攻击方逐部队战术
        Map<String, String> attackerTactics = new LinkedHashMap<>();
        Map<String, Map<String, Object>> allTactics = engine.getAllTactics(state);
        for (int i = 0; i < immediate.size(); i++) {
            Unit u = immediate.get(i);
            String tactic = unitTactics.getOrDefault(u.getName(),
                    unitTactics.getOrDefault(String.valueOf(i),
                            unitTactics.getOrDefault("_default", "assault")));
            attackerTactics.put(u.getName(), allTactics.containsKey(tactic) ? tactic : "assault");
        }

        // NPC地块无真实驻军 → 直接占领
        String enemyFid = enemyInfo.getOwnerFid();
        String enemyType = enemyInfo.getOwnerType();
        if ("npc".equals(enemyType) || "npc_faction".equals(enemyType)) {
            // 检查是否有真实AI部队在此
            boolean hasRealDefenders = false;
            for (var ae : state.getAiFactions().entrySet()) {
                FactionState afs = ae.getValue().getFactionState();
                if (afs != null && afs.getUnits() != null) {
                    for (Unit u : afs.getUnits()) {
                        if (u.isActive() && provincePid.equals(engine.resolvePositionToPid(u.getPosition()))) {
                            hasRealDefenders = true; break;
                        }
                    }
                }
                if (hasRealDefenders) break;
            }
            if (!hasRealDefenders) {
                // 直接占领
                String pname = engine.getProvince(provincePid).getName();
                fs.getTerritories().add(pname);
                state.setActionPoints(Math.max(0, state.getActionPoints() - 1));
                return GameUtils.mapOf("ok", true, "message", "🏴 兵不血刃占领 " + pname + "（无敌军驻守）");
            }
        }

        String terrain = enemyInfo.getTerrain();
        List<Unit> defending = generateEnemyGarrison(state, provincePid,
                "npc".equals(enemyType) || "npc_faction".equals(enemyType) ? null : enemyFid);

        // AI守方按地形/兵种自动选择战术
        Map<String, String> defenderTactics = new LinkedHashMap<>();
        for (Unit u : defending) {
            String defTac;
            String uType = u.getType();
            if ("artillery".equals(uType)) defTac = "bombard";
            else if (("森林".equals(terrain) || "丘陵".equals(terrain) || "山地".equals(terrain))
                    && ("infantry".equals(uType) || "engineer".equals(uType))) defTac = "ambush";
            else if ("城市".equals(terrain)) defTac = "fortify";
            else if (("平原".equals(terrain) || "盆地".equals(terrain)) && "cavalry".equals(uType)) defTac = "flanking";
            else defTac = "fortify";
            defenderTactics.put(u.getName(), defTac);
        }

        // 增援队列
        List<Map<String, Object>> reinfQueue = new ArrayList<>();
        for (Object[] r : reinforcements) {
            Unit u = (Unit) r[0];
            int dist = ((Number) r[1]).intValue();
            u.setStatus("marching");
            u.setReinforceCampaign("camp_" + state.getTurn() + "_" + state.getActiveCampaigns().size());
            String tactic = unitTactics.getOrDefault(u.getName(),
                    unitTactics.getOrDefault("_default", "assault"));
            Map<String, Object> re = new LinkedHashMap<>();
            re.put("unit_name", u.getName());
            re.put("arrives_round", dist);
            re.put("tactic", allTactics.containsKey(tactic) ? tactic : "assault");
            reinfQueue.add(re);
        }

        // 创建战役
        Campaign camp = new Campaign();
        camp.setId("camp_" + state.getTurn() + "_" + state.getActiveCampaigns().size());
        camp.setProvince(provincePid);
        camp.setProvinceName(enemyInfo.getName());
        camp.setTerrain(terrain);
        camp.setAttackerFaction(state.getPlayerFactionId());
        camp.setAttackerName(fs.getName());
        camp.setDefenderFaction(enemyFid);
        camp.setDefenderName(enemyInfo.getOwner());
        camp.setDefenderType(enemyType);
        camp.setAttackerUnits(immediate.stream().map(Unit::getName).collect(Collectors.toList()));
        camp.setDefenderUnits(defending.stream().map(Unit::getName).collect(Collectors.toList()));
        camp.setAttackerTactics(attackerTactics);
        camp.setDefenderTactics(defenderTactics);
        camp.setRound(0);
        camp.setMaxRounds(4);
        camp.setStatus("ongoing");
        camp.setProvinceValue(rng.nextInt(6) + 3); // 3-8

        // 缓存部队引用
        camp.setAttackerCache(new ArrayList<>(immediate));
        camp.setDefenderCache(new ArrayList<>(defending));
        camp.setReinforcementQueue(reinfQueue);

        if (state.getActiveCampaigns() == null) state.setActiveCampaigns(new ArrayList<>());
        state.getActiveCampaigns().add(camp);

        // 标记立即参战部队
        for (Unit u : immediate) {
            u.setStatus("fighting");
            u.setCampaignId(camp.getId());
        }

        // 消息
        Set<String> tacSet = new HashSet<>(attackerTactics.values());
        List<String> tacNames = new ArrayList<>();
        for (String t : tacSet) {
            Map<String, Object> tinfo = allTactics.get(t);
            tacNames.add(tinfo != null ? (String) tinfo.get("name") : t);
        }
        StringBuilder msg = new StringBuilder("⚔ 战役开始：进攻" + enemyInfo.getName()
                + " | 战术：" + String.join("/", tacNames)
                + " | 我军" + immediate.size() + "支 vs 敌军" + defending.size() + "支");
        if (!reinforcements.isEmpty()) msg.append(" | ").append(reinforcements.size()).append("支行军途中");
        return GameUtils.mapOf("ok", true, "message", msg.toString());
    }

    // ═══════════════════════════════════════════ 战役结算 ═══════════════════════════════════════════

    /** 结算所有进行中的战役（每回合自动调用一次） */
    @SuppressWarnings("unchecked")
    public BattleResult resolveAllCampaigns(GameState state) {
        List<String> msgs = new ArrayList<>();
        List<Map<String, Object>> structured = new ArrayList<>();
        List<Campaign> completed = new ArrayList<>();
        String playerFid = state.getPlayerFactionId();

        for (Campaign camp : state.getActiveCampaigns()) {
            if (!"ongoing".equals(camp.getStatus())) {
                completed.add(camp);
                continue;
            }

            boolean isPlyAtk = camp.getAttackerFaction().equals(playerFid);
            String atkName = camp.getAttackerName();
            String defName = camp.getDefenderName();

            // 0. 处理增援到达
            msgs.addAll(processReinforcementArrivals(state, camp));

            // 获取活跃部队
            List<Unit> attackers = activeUnits(camp.getAttackerCache());
            List<Unit> defenders = activeUnits(camp.getDefenderCache());

            // 一方全灭
            if (attackers.isEmpty() || defenders.isEmpty()) {
                camp.setStatus(attackers.isEmpty() ? "defender_held" : "attacker_occupied");
                completed.add(camp);
                for (Unit u : attackers) {
                    u.setPosition(camp.getProvince());
                    u.setStatus("ready");
                    u.setCampaignId(null);
                }
                String statusMsg;
                if (isPlyAtk) {
                    statusMsg = "战役结束：" + camp.getProvinceName() + " — "
                            + ("attacker_occupied".equals(camp.getStatus()) ? "我军占领" : "敌军固守");
                } else {
                    statusMsg = "战役结束：" + camp.getProvinceName() + " — " + atkName
                            + ("attacker_occupied".equals(camp.getStatus()) ? "占领" : "进攻受阻");
                }
                msgs.add(statusMsg);
                structured.add(buildCampaignResult(camp,
                        "attacker_occupied".equals(camp.getStatus()) ? "attacker_occupied" : "defender_held",
                        0, 0, 0, "attacker_occupied".equals(camp.getStatus()), statusMsg, isPlyAtk));
                if ("attacker_occupied".equals(camp.getStatus())) {
                    msgs.addAll(handleOccupation(state, camp));
                }
                continue;
            }

            // 1. 计算战斗力
            double atkPower = calcBattlePower(attackers, camp.getAttackerTactics(), camp.getTerrain(), true, state);
            double defPower = calcBattlePower(defenders, camp.getDefenderTactics(), camp.getTerrain(), false, state);
            double ratio = atkPower / Math.max(1, defPower);
            camp.setRound(camp.getRound() + 1);

            // 判定结果
            String outcome;
            if (ratio >= 2.5) outcome = "annihilate";
            else if (ratio >= 1.8) outcome = "decisive_win";
            else if (ratio >= 1.2) outcome = "costly_win";
            else if (ratio >= 0.7) outcome = "stalemate";
            else if (ratio >= 0.4) outcome = "setback";
            else outcome = "rout";

            // 2. 应用单位级损伤
            int[] casualties = applyUnitDamage(attackers, defenders, outcome);
            int atkCasualties = casualties[0];
            int defCasualties = casualties[1];
            camp.setAtkCasualties(camp.getAtkCasualties() + atkCasualties);
            camp.setDefCasualties(camp.getDefCasualties() + defCasualties);

            // 3. 逐部队撤退判定
            List<String> atkTerrs = engine.getFactionTerritories(state, camp.getAttackerFaction());
            List<String> defTerrs = engine.getFactionTerritories(state, camp.getDefenderFaction());
            String currentPid = camp.getProvince();

            int atkRetreated = 0, defRetreated = 0;
            for (Unit u : attackers) {
                if (!isDead(u) && "retreated".equals(checkUnitRetreat(u, atkTerrs, currentPid))) atkRetreated++;
            }
            for (Unit u : defenders) {
                if (!isDead(u) && "retreated".equals(checkUnitRetreat(u, defTerrs, currentPid))) defRetreated++;
            }

            // 4. 重新评估
            List<Unit> activeAttackers = attackers.stream()
                    .filter(u -> !isDead(u) && camp.getId().equals(u.getCampaignId()))
                    .collect(Collectors.toList());
            List<Unit> activeDefenders = defenders.stream()
                    .filter(u -> !isDead(u) && camp.getId().equals(u.getCampaignId()))
                    .collect(Collectors.toList());

            Map<String, String> outcomeCn = Map.of(
                    "annihilate", "歼灭性大胜", "decisive_win", "大胜", "costly_win", "惨胜",
                    "stalemate", "持平", "setback", "受挫", "rout", "溃败");

            String roundMsg = "⚔ " + camp.getProvinceName() + " 第" + camp.getRound() + "轮："
                    + outcomeCn.get(outcome) + " (战力比" + String.format("%.1f", ratio) + ":1) "
                    + (isPlyAtk ? "我军损" + atkCasualties + " 敌损" + defCasualties
                    : atkName + "损" + atkCasualties + " " + defName + "损" + defCasualties);
            if (atkRetreated > 0) roundMsg += " 攻方" + atkRetreated + "支撤退";
            if (defRetreated > 0) roundMsg += " 守方" + defRetreated + "支撤退";

            boolean provinceFell = false, battleEndedByWipe = false;

            if (activeAttackers.isEmpty() && activeDefenders.isEmpty()) {
                // 双方都打光了 → 攻方勉强占领
                provinceFell = true; battleEndedByWipe = true;
                camp.setStatus("attacker_occupied"); roundMsg += " 🏴 双方力竭，攻方占领！";
            } else if (activeAttackers.isEmpty()) {
                battleEndedByWipe = true;
                camp.setStatus("defender_held"); roundMsg += " 🛡 攻方溃散，守方固守！";
            } else if (activeDefenders.isEmpty() && camp.getRound() >= 2) {
                provinceFell = true; battleEndedByWipe = true;
                camp.setStatus("attacker_occupied"); roundMsg += " 🏴 守方溃散，占领！";
            } else if ("annihilate".equals(outcome) && camp.getRound() >= 2 && rng.nextDouble() < 0.5) {
                provinceFell = true; camp.setStatus("attacker_occupied");
            } else if ("decisive_win".equals(outcome) && camp.getRound() >= 3 && rng.nextDouble() < 0.4) {
                provinceFell = true; camp.setStatus("attacker_occupied");
            } else if ("costly_win".equals(outcome) && camp.getRound() >= 4 && rng.nextDouble() < 0.25) {
                provinceFell = true; camp.setStatus("attacker_occupied");
            }
            if (provinceFell) roundMsg += " 🏴 占领！";

            // 5. 结算结果
            if ("rout".equals(outcome) && !battleEndedByWipe) {
                camp.setStatus("attacker_routed"); completed.add(camp);
                msgs.add(roundMsg);
                structured.add(buildCampaignResult(camp, outcome, ratio, atkCasualties, defCasualties, false, roundMsg, isPlyAtk));
            } else if (provinceFell) {
                camp.setStatus("attacker_occupied");
                for (Unit u : attackers) {
                    if (!isDead(u)) { u.setStatus("ready"); u.setCampaignId(null); u.setPosition(camp.getProvince()); }
                }
                msgs.addAll(handleOccupation(state, camp));
                // 收编降军
                List<Unit> surrendered = defenders.stream().filter(u -> "surrendered".equals(u.getStatus())).collect(Collectors.toList());
                if (!surrendered.isEmpty()) {
                    FactionState atkFs = engine.getFactionState(state, camp.getAttackerFaction());
                    int limit = Math.min(2, surrendered.size());
                    for (int i = 0; i < limit; i++) {
                        Unit su = surrendered.get(i);
                        su.setOriginalName(su.getName());
                        su.setName("降军_" + su.getName());
                        su.setStatus("ready");
                        su.setMorale(Math.max(25, su.getMorale() - 25));
                        su.setStrength(Math.max(20, su.getStrength() / 2));
                        su.setPosition(camp.getProvince());
                        if (atkFs.getUnits() == null) atkFs.setUnits(new ArrayList<>());
                        atkFs.getUnits().add(su);
                    }
                    atkFs.setArmy(engine.recountArmyFromUnits(atkFs.getUnits()));
                }
                completed.add(camp); msgs.add(roundMsg);
                structured.add(buildCampaignResult(camp, outcome, ratio, atkCasualties, defCasualties, true, roundMsg, isPlyAtk));
            } else if (battleEndedByWipe && "defender_held".equals(camp.getStatus())) {
                for (Unit u : attackers) {
                    if (!isDead(u)) { u.setStatus("ready"); u.setCampaignId(null); }
                }
                completed.add(camp); msgs.add(roundMsg);
                structured.add(buildCampaignResult(camp, outcome, ratio, atkCasualties, defCasualties, false, roundMsg, isPlyAtk));
            } else if (camp.getRound() >= camp.getMaxRounds()) {
                camp.setStatus("stalemate_end");
                for (Unit u : attackers) {
                    if (!isDead(u)) { u.setStatus("ready"); u.setCampaignId(null); }
                }
                completed.add(camp); msgs.add(roundMsg + "（战线僵持，自动停战）");
                structured.add(buildCampaignResult(camp, outcome, ratio, atkCasualties, defCasualties, false, roundMsg + "（战线僵持，自动停战）", isPlyAtk));
            } else {
                camp.setLastResult(outcome);
                String aiDecision = campaignAiDecide(state, camp, outcome);
                if ("ceasefire".equals(aiDecision)) {
                    camp.setStatus("ceasefire");
                    for (Unit u : attackers) {
                        if (!isDead(u)) { u.setStatus("ready"); u.setCampaignId(null); u.setPosition(camp.getProvince()); }
                    }
                    completed.add(camp);
                    String cmsg = isPlyAtk ? "🤝 " + camp.getDefenderName() + "请求停战——我方接受。" + camp.getProvinceName() + "仍为敌占。"
                            : "🤝 " + camp.getDefenderName() + "请求停战——" + atkName + "接受。" + camp.getProvinceName() + "仍为" + defName + "控制。";
                    msgs.add(cmsg);
                    structured.add(buildCampaignResult(camp, "ceasefire", ratio, atkCasualties, defCasualties, false, cmsg, isPlyAtk));
                } else {
                    structured.add(buildCampaignResult(camp, outcome, ratio, atkCasualties, defCasualties, false, roundMsg, isPlyAtk));
                }
            }
        }

        // 清理已结束
        state.getActiveCampaigns().removeAll(completed);

        // 清理缓存
        for (Campaign camp : state.getActiveCampaigns()) {
            if ("ongoing".equals(camp.getStatus())) {
                camp.setAttackerCache(camp.getAttackerCache().stream().filter(u -> !isDead(u)).collect(Collectors.toList()));
                camp.setDefenderCache(camp.getDefenderCache().stream().filter(u -> !isDead(u)).collect(Collectors.toList()));
            }
        }

        return new BattleResult(msgs, structured);
    }

    // ═══════════════════════════════════════════ 战斗力计算 ═══════════════════════════════════════════

    @SuppressWarnings("unchecked")
    double calcBattlePower(List<Unit> units, Map<String, String> unitTactics, String terrain,
                           boolean isAttacker, GameState state) {
        if (units == null || units.isEmpty()) return 0;
        double total = 0;
        Map<String, Map<String, Object>> allTactics = engine.getAllTactics(state);

        for (Unit u : units) {
            if (isDead(u)) continue;
            String tacticId = "assault";
            if (unitTactics instanceof Map) {
                tacticId = unitTactics.getOrDefault(u.getName(), "assault");
            }
            Map<String, Object> tac = allTactics.getOrDefault(tacticId, allTactics.get("assault"));
            if (tac == null) tac = Map.of("atk_mult", 1.0, "def_mult", 1.0);

            double mult = ((Number) tac.get(isAttacker ? "atk_mult" : "def_mult")).doubleValue();
            double atkDef = isAttacker ? u.getAttack() : u.getDefense();
            double strRatio = u.getStrength() / (double) Math.max(1, u.getMaxStrength());
            double moraleFactor = u.getMorale() / 100.0;
            double expFactor = 1.0 + u.getExperience() / 200.0;

            total += atkDef * strRatio * moraleFactor * expFactor * mult;
        }

        double terrainMult = TERRAIN_BONUS.getOrDefault(terrain, 1.0);
        if (!isAttacker) {
            total *= terrainMult;
        } else {
            total *= (1.0 + (1.0 - terrainMult) * 0.4);
        }
        return total;
    }

    // ═══════════════════════════════════════════ 损伤应用 ═══════════════════════════════════════════

    int[] applyUnitDamage(List<Unit> attackers, List<Unit> defenders, String outcome) {
        int atkLoss = 0, defLoss = 0;
        double[] losses = LOSS_MAPS.getOrDefault(outcome, new double[]{0.25, 0.25});
        double atkPct = losses[0], defPct = losses[1];

        // 攻方损伤
        for (Unit u : attackers) {
            if (isDead(u)) continue;
            int loss = (int)(u.getStrength() * atkPct * (0.5 + rng.nextDouble()));
            loss = Math.max(5, Math.min(loss, u.getStrength()));
            u.setStrength(Math.max(0, u.getStrength() - loss));
            atkLoss += loss;
            if (u.getStrength() <= 0) {
                u.setStatus("annihilated"); u.setStrength(0);
            } else if (u.getStrength() < 20 && ("rout".equals(outcome) || "setback".equals(outcome))) {
                u.setStatus("routed"); u.setRoutedTurns(2);
            } else if (u.getStrength() < 35 && "costly_win".equals(outcome)) {
                u.setStatus("damaged");
            } else if (("setback".equals(outcome) || "rout".equals(outcome)) && rng.nextDouble() < 0.3) {
                u.setStatus("repelled");
            }
        }
        // 守方损伤
        for (Unit u : defenders) {
            if (isDead(u)) continue;
            int loss = (int)(u.getStrength() * defPct * (0.5 + rng.nextDouble()));
            loss = Math.max(5, Math.min(loss, u.getStrength()));
            u.setStrength(Math.max(0, u.getStrength() - loss));
            defLoss += loss;
            if (u.getStrength() <= 0) {
                if (("annihilate".equals(outcome) || "decisive_win".equals(outcome)) && rng.nextDouble() < 0.4) {
                    u.setStatus("surrendered");
                } else {
                    u.setStatus("annihilated");
                }
                u.setStrength(0);
            } else if (u.getStrength() < 20) {
                u.setStatus("routed"); u.setRoutedTurns(2);
            } else if (("annihilate".equals(outcome) || "decisive_win".equals(outcome)) && rng.nextDouble() < 0.3) {
                u.setStatus("surrendered");
            }
        }

        // 经验增长
        int expGain = BASE_EXP.getOrDefault(outcome, 2);
        for (Unit u : attackers) {
            if (isDead(u) || "routed".equals(u.getStatus())) continue;
            u.setExperience(Math.min(100, u.getExperience() + expGain + rng.nextInt(6) - 2));
            if ("annihilate".equals(outcome) || "decisive_win".equals(outcome))
                u.setExperience(Math.min(100, u.getExperience() + 3));
        }
        for (Unit u : defenders) {
            if (isDead(u) || "routed".equals(u.getStatus())) continue;
            u.setExperience(Math.min(100, u.getExperience() + expGain + rng.nextInt(6) - 2));
        }

        return new int[]{atkLoss, defLoss};
    }

    // ═══════════════════════════════════════════ 撤退/增援/战术 ═══════════════════════════════════════════

    /** 主动撤退 */
    public Map<String, Object> retreatFromCampaign(GameState state, int campaignIndex) {
        List<Campaign> campaigns = state.getActiveCampaigns();
        if (campaignIndex < 0 || campaignIndex >= campaigns.size())
            return GameUtils.mapOf("ok", false, "message", "无效战役索引");
        Campaign camp = campaigns.get(campaignIndex);
        if (!"ongoing".equals(camp.getStatus()))
            return GameUtils.mapOf("ok", false, "message", "该战役已结束");
        if (!camp.getAttackerFaction().equals(state.getPlayerFactionId()))
            return GameUtils.mapOf("ok", false, "message", "只能从自己发动的战役撤退");

        camp.setStatus("attacker_retreat");
        List<String> friendly = state.getFactionState().getTerritories();
        if (camp.getAttackerCache() != null) {
            for (Unit u : camp.getAttackerCache()) {
                u.setStatus("ready"); u.setCampaignId(null);
                u.setMorale(Math.max(20, u.getMorale() - 15));
                u.setPosition(findNearestFriendly(camp.getProvince(), friendly));
                if (rng.nextDouble() < 0.2)
                    u.setStrength(Math.max(1, u.getStrength() - rng.nextInt(11) - 5));
            }
        }
        state.getActiveCampaigns().remove(camp);
        return GameUtils.mapOf("ok", true, "message", "🏳 我军从" + camp.getProvinceName() + "主动撤退。实力得以保存。");
    }

    /** 增援战役 */
    public Map<String, Object> reinforceCampaign(GameState state, int campaignIndex,
                                                  List<Integer> unitIndices, Map<String, String> unitTactics) {
        List<Campaign> campaigns = state.getActiveCampaigns();
        if (campaignIndex < 0 || campaignIndex >= campaigns.size())
            return GameUtils.mapOf("ok", false, "message", "无效战役索引");
        Campaign camp = campaigns.get(campaignIndex);
        if (!"ongoing".equals(camp.getStatus()))
            return GameUtils.mapOf("ok", false, "message", "该战役已结束");
        if (state.getActionPoints() < 1)
            return GameUtils.mapOf("ok", false, "message", "行动点不足");

        List<Unit> myUnits = state.getFactionState().getUnits();
        List<Object[]> reinfs = new ArrayList<>(); // [idx, Unit]
        for (int idx : unitIndices) {
            if (idx < 0 || idx >= myUnits.size()) continue;
            Unit u = myUnits.get(idx);
            if ("fighting".equals(u.getStatus()) || "routed".equals(u.getStatus()) || "annihilated".equals(u.getStatus()))
                continue;
            reinfs.add(new Object[]{idx, u});
        }
        if (reinfs.isEmpty())
            return GameUtils.mapOf("ok", false, "message", "没有可用的增援部队");

        state.setActionPoints(state.getActionPoints() - 1);
        if (unitTactics == null) unitTactics = Map.of();

        List<Map<String, Object>> queue = camp.getReinforcementQueue();
        if (queue == null) { queue = new ArrayList<>(); camp.setReinforcementQueue(queue); }
        int totalArrives = 0;
        for (Object[] r : reinfs) {
            int idx = (int) r[0];
            Unit u = (Unit) r[1];
            Object[] distResult = engine.getDistance(resolveUnitPid(u.getPosition()), camp.getProvince());
            int dist = distResult[0] != null ? ((Number) distResult[0]).intValue() : 2;
            int arrivesIn = Math.max(1, dist);
            int arrivesRound = camp.getRound() + arrivesIn;
            String tactic = unitTactics.getOrDefault(u.getName(), unitTactics.getOrDefault(String.valueOf(idx), "assault"));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("unit_name", u.getName()); entry.put("unit_idx", idx);
            entry.put("arrives_round", arrivesRound); entry.put("tactic", tactic); entry.put("side", "attacker");
            queue.add(entry);
            u.setStatus("reinforcing");
            u.setReinforceCampaign(camp.getId());
            totalArrives = Math.max(totalArrives, arrivesIn);
        }
        return GameUtils.mapOf("ok", true, "message", "增援" + reinfs.size() + "支部队，预计" + totalArrives + "回合后到达" + camp.getProvinceName());
    }

    /** 战中战术调整 */
    public Map<String, Object> changeCampaignTactics(GameState state, String campaignId,
                                                      Map<String, String> unitTacticChanges, String factionId) {
        Campaign camp = null;
        for (Campaign c : state.getActiveCampaigns()) {
            if (c.getId().equals(campaignId) && "ongoing".equals(c.getStatus())) { camp = c; break; }
        }
        if (camp == null) return GameUtils.mapOf("ok", false, "message", "战役不存在或已结束");

        String pid = factionId != null ? factionId : state.getPlayerFactionId();
        boolean isAttacker = camp.getAttackerFaction().equals(pid);
        Map<String, String> side = isAttacker ? camp.getAttackerTactics() : camp.getDefenderTactics();
        if (side == null) { side = new LinkedHashMap<>(); if (isAttacker) camp.setAttackerTactics(side); else camp.setDefenderTactics(side); }

        Map<String, Map<String, Object>> allTactics = engine.getAllTactics(state);
        int changed = 0;
        List<String> details = new ArrayList<>();
        for (var entry : unitTacticChanges.entrySet()) {
            String unitName = entry.getKey(), newTac = entry.getValue();
            if (!allTactics.containsKey(newTac)) continue;
            if (side.containsKey(unitName) && !newTac.equals(side.get(unitName))) {
                side.put(unitName, newTac); changed++;
                Map<String, Object> ti = allTactics.get(newTac);
                details.add(unitName + "→" + (ti != null ? ti.get("name") : newTac));
            }
        }
        if (changed == 0) return GameUtils.mapOf("ok", false, "message", "没有战术需要变更");
        return GameUtils.mapOf("ok", true, "message", "战术调整：" + String.join("，", details) + "（" + changed + "支部队）");
    }

    /** 战役胜利后授勋 */
    public Map<String, Object> honorCampaignUnits(GameState state, String campaignId) {
        FactionState fs = state.getFactionState();
        // 从本回合结果中找
        CampaignResult camp = null;
        if (state.getCampaignResultsThisTurn() != null) {
            for (CampaignResult c : state.getCampaignResultsThisTurn()) {
                if (campaignId.equals(c.getId())) { camp = c; break; }
            }
        }
        if (camp == null) return GameUtils.mapOf("ok", false, "message", "找不到该战役");
        if (!camp.isHonorAvailable()) return GameUtils.mapOf("ok", false, "message", "此战役不满足授勋条件");

        Set<String> honored = state.getHonoredCampaigns();
        if (honored == null) { honored = new HashSet<>(); state.setHonoredCampaigns(honored); }
        if (honored.contains(campaignId)) return GameUtils.mapOf("ok", false, "message", "已授勋过");

        int cost = camp.getHonorCost();
        if (fs.getTreasury() < cost) return GameUtils.mapOf("ok", false, "message", "国库不足（需" + cost + "金）");

        String outcome = camp.getOutcome();
        Map<String, Integer> effects = HONOR_EFFECTS.getOrDefault(outcome, HONOR_EFFECTS.get("costly_win"));

        List<String> unitNames = camp.getAttackerUnits();
        List<String> affected = new ArrayList<>();
        for (Unit u : fs.getUnits()) {
            if (unitNames != null && unitNames.contains(u.getName())
                    && !"annihilated".equals(u.getStatus()) && !"routed".equals(u.getStatus())) {
                u.setAttack(u.getAttack() + effects.getOrDefault("attack", 0));
                u.setMorale(Math.min(100, u.getMorale() + effects.getOrDefault("morale", 0)));
                u.setExperience(u.getExperience() + effects.getOrDefault("experience", 0));
                affected.add(u.getName());
            }
        }

        fs.setTreasury(fs.getTreasury() - cost);
        honored.add(campaignId);

        StringBuilder msg = new StringBuilder("🏅 授勋：" + String.join("、", affected));
        if (effects.getOrDefault("attack", 0) > 0) msg.append(" 攻击+").append(effects.get("attack"));
        if (effects.getOrDefault("morale", 0) > 0) msg.append(" 士气+").append(effects.get("morale"));
        if (effects.getOrDefault("experience", 0) > 0) msg.append(" 经验+").append(effects.get("experience"));
        msg.append("（消耗").append(cost).append("金）");

        return GameUtils.mapOf("ok", true, "message", msg.toString(),
                "cost", cost, "effects", effects, "affected_units", affected);
    }

    // ═══════════════════════════════════════════ 辅助方法 ═══════════════════════════════════════════

    /** 列出所有可攻击的敌方省份 */
    @SuppressWarnings("unchecked")
    public List<EnemyProvince> listEnemyProvinces(GameState state) {
        FactionState fs = state.getFactionState();
        Set<String> myTerrs = new HashSet<>(fs.getTerritories());
        String myRegion = engine.getFaction(state.getPlayerFactionId()).map(FactionDefinition::getRegion).orElse("");
        Set<String> campaignProvs = state.getActiveCampaigns().stream()
                .filter(c -> "ongoing".equals(c.getStatus())).map(Campaign::getProvince)
                .collect(Collectors.toSet());

        // AI势力领土
        Map<String, List<String>> fidTerritories = new HashMap<>();
        Map<String, String> fidNames = new HashMap<>();
        for (var entry : state.getAiFactions().entrySet()) {
            String fid = entry.getKey();
            if (state.getDefeatedFactions().contains(fid)) continue;
            FactionState afs = entry.getValue().getFactionState();
            if (afs == null || afs.getTerritories() == null) continue;
            fidTerritories.put(fid, new ArrayList<>(afs.getTerritories()));
            fidNames.put(fid, afs.getName());
        }

        List<EnemyProvince> enemies = new ArrayList<>();
        Map<String, String> nameToOwner = new HashMap<>();
        Map<String, String> nameToOwnerFid = new HashMap<>();
        for (var e : fidTerritories.entrySet()) {
            for (String t : e.getValue()) { nameToOwner.put(t, fidNames.get(e.getKey())); nameToOwnerFid.put(t, e.getKey()); }
        }

        for (String tname : myTerrs) {
            String myPid = engine.getPidByName(tname);
            if (myPid == null) continue;
            for (var entry : engine.getMapData().getAll().entrySet()) {
                String pid = entry.getKey();
                Province p = entry.getValue();
                String pname = p.getName();
                if (myTerrs.contains(pname) || campaignProvs.contains(pid)) continue;
                String owner = nameToOwner.get(pname);
                if (owner == null) continue;
                String ofid = nameToOwnerFid.get(pname);
                var of = engine.getFaction(ofid).orElse(null);
                if (of == null) continue;
                // 跨区/互不侵犯
                if (!of.getRegion().equals(myRegion) && !engine.isRegionUnified(state, state.getPlayerFactionId()))
                    continue;
                if (engine.hasNonAggression(state, state.getPlayerFactionId(), ofid)) continue;
                // 距离检查放宽到5跳（西部地区稀疏）
                Object[] distResult = engine.getDistance(myPid, pid);
                int dist = distResult[0] != null ? ((Number) distResult[0]).intValue() : 999;
                if (dist > 5) continue;

                enemies.add(new EnemyProvince(pid, pname, p.getTerrain(), p.getType(),
                        owner, ofid, "faction", false));
            }
        }

        // NPC领地（同区域hostile）— 排除已被玩家占领的
        Map<String, NpcDefinition> npcs = engine.getGameData().getHostileNpcs();
        if (npcs != null) {
            for (var entry : npcs.entrySet()) {
                String nid = entry.getKey();
                if (state.getDefeatedFactions().contains(nid)) continue;
                NpcDefinition ndata = entry.getValue();
                if (!myRegion.equals(ndata.getRegion())) continue;
                List<String> terrList = ndata.getTerritories();
                if (terrList == null) continue;
                for (String tname : terrList) {
                    if (myTerrs.contains(tname)) continue;
                    String pid = engine.getPidByName(tname);
                    if (pid == null) continue;
                    if (enemies.stream().anyMatch(e -> pid.equals(e.getPid()))) continue;
                    Province p = engine.getProvince(pid);
                    enemies.add(new EnemyProvince(pid, tname,
                            p != null ? p.getTerrain() : "平原", p != null ? p.getType() : "",
                            ndata.getName(), nid, "npc", false));
                }
            }
        }

        return enemies;
    }

    /** 生成敌方驻军 */
    public List<Unit> generateEnemyGarrison(GameState state, String provincePid, String enemyFid) {
        List<Unit> units = new ArrayList<>();
        FactionDefinition tf = null;
        if (enemyFid != null) tf = engine.getFaction(enemyFid).orElse(null);

        int baseMil = tf != null ? tf.getStats().getMilitary() : 20;
        List<String> tForces = tf != null ? tf.getInitialForces() : List.of("守备队");
        int nUnits = Math.min(tForces.size(), rng.nextInt(4) + 2); // 2-5

        for (int i = 0; i < nUnits; i++) {
            String fname = i < tForces.size() ? tForces.get(i) : "守备第" + (i + 1) + "团";
            Unit u = new Unit();
            u.setName(fname);
            u.setType(engine.inferUnitType(fname));
            u.setAttack(GameEngine.clamp(baseMil / 8 + rng.nextInt(6) - 2, 3, 20));
            u.setDefense(GameEngine.clamp(baseMil / 6 + rng.nextInt(6) - 2, 3, 25));
            u.setMorale(rng.nextInt(31) + 40);
            u.setExperience(rng.nextInt(31) + 10);
            u.setStrength(rng.nextInt(51) + 50);
            u.setMaxStrength(100);
            u.setStatus("ready");
            u.setPosition(provincePid);
            units.add(u);
        }
        return units;
    }

    /** 处理增援到达 */
    @SuppressWarnings("unchecked")
    List<String> processReinforcementArrivals(GameState state, Campaign camp) {
        List<Map<String, Object>> queue = camp.getReinforcementQueue();
        if (queue == null || queue.isEmpty()) return List.of();
        List<String> msgs = new ArrayList<>();
        List<Map<String, Object>> arrived = new ArrayList<>(), stillEnRoute = new ArrayList<>();
        int currentRound = camp.getRound();
        boolean isPlyAtk = camp.getAttackerFaction().equals(state.getPlayerFactionId());

        for (Map<String, Object> entry : queue) {
            if (((Number) entry.get("arrives_round")).intValue() > currentRound) stillEnRoute.add(entry);
            else arrived.add(entry);
        }
        camp.setReinforcementQueue(stillEnRoute);

        for (Map<String, Object> entry : arrived) {
            Unit unit = findUnitByName(state, (String) entry.get("unit_name"));
            if (unit == null) continue;
            unit.setStatus("ready");
            unit.setReinforceCampaign(null);

            if ("ongoing".equals(camp.getStatus())) {
                unit.setStatus("fighting");
                unit.setCampaignId(camp.getId());
                unit.setPosition(camp.getProvince());
                if (camp.getAttackerCache() == null) camp.setAttackerCache(new ArrayList<>());
                camp.getAttackerCache().add(unit);
                if (!camp.getAttackerUnits().contains(unit.getName()))
                    camp.getAttackerUnits().add(unit.getName());
                if (camp.getAttackerTactics() == null) camp.setAttackerTactics(new LinkedHashMap<>());
                camp.getAttackerTactics().put(unit.getName(), (String) entry.get("tactic"));
                if (isPlyAtk) msgs.add("增援到达：" + unit.getName() + "加入" + camp.getProvinceName() + "战场");
            } else {
                unit.setPosition(camp.getProvince());
                String owner = getProvinceOwner(state, camp.getProvince());
                String atkFid = camp.getAttackerFaction();
                if (atkFid.equals(owner) || owner == null) {
                    if (isPlyAtk) msgs.add("增援到达：" + unit.getName() + "抵达" + camp.getProvinceName() + "（战役已结束，停在当地）");
                } else {
                    if (isPlyAtk) msgs.add("增援到达：" + unit.getName() + "抵达" + camp.getProvinceName() + "，发现被敌方占领，发起进攻！");
                    triggerAutoAttack(state, unit, camp.getProvince(), (String) entry.get("tactic"));
                }
            }
        }
        return msgs;
    }

    // ═══════════════════════════════════════════ 内部方法 ═══════════════════════════════════════════

    private String checkUnitRetreat(Unit unit, List<String> friendlyTerrs, String currentPid) {
        double pct = unit.getStrength() / (double) Math.max(1, unit.getMaxStrength());
        if (pct >= 0.35 || (pct >= 0.20 && unit.getMorale() >= 30)) return "fighting";
        String retreatPid = findNearestFriendly(currentPid, friendlyTerrs);
        if (retreatPid.equals(currentPid)) return "fighting";
        unit.setStatus("ready"); unit.setCampaignId(null);
        unit.setMorale(Math.max(10, unit.getMorale() - 10));
        unit.setPosition(retreatPid);
        return "retreated";
    }

    String findNearestFriendly(String fromPid, List<String> friendlyNames) {
        if (friendlyNames == null || friendlyNames.isEmpty()) return fromPid;
        List<Object[]> reachable = new ArrayList<>(); // [dist, pid, isCity]
        for (String tname : friendlyNames) {
            String tpid = engine.getPidByName(tname);
            if (tpid == null) continue;
            Object[] distResult = engine.getDistance(fromPid, tpid);
            if (distResult[0] == null) continue;
            int dist = ((Number) distResult[0]).intValue();
            Province p = engine.getProvince(tpid);
            reachable.add(new Object[]{dist, tpid, "city".equals(p != null ? p.getType() : "")});
        }
        if (reachable.isEmpty()) return fromPid;
        reachable.sort(Comparator.comparingInt(a -> (int) a[0]));
        List<Object[]> cities = reachable.stream().filter(r -> (boolean) r[2] && (int) r[0] <= 5).collect(Collectors.toList());
        if (!cities.isEmpty()) return (String) cities.get(0)[1];
        return (String) reachable.get(0)[1];
    }

    private String campaignAiDecide(GameState state, Campaign camp, String lastOutcome) {
        if ("rout".equals(lastOutcome)) return "continue";
        List<Unit> defenders = camp.getDefenderCache();
        int defTotalStr = defenders != null ? defenders.stream().mapToInt(Unit::getStrength).sum() : 0;
        int defInitial = Math.max(1, camp.getDefenderUnits().size()) * 100;
        double defPct = defTotalStr / (double) defInitial;
        int provinceValue = camp.getProvinceValue();

        if (defPct < 0.2) {
            if (provinceValue > 6 && rng.nextDouble() > 0.7) return "continue";
            if (rng.nextDouble() < 0.7) return "ceasefire";
        }
        if (defPct < 0.4) {
            if (provinceValue > 5 && rng.nextDouble() > 0.5) return "continue";
            if (rng.nextDouble() < 0.5) return "ceasefire";
        }
        if ("annihilate".equals(lastOutcome) && rng.nextDouble() < 0.6) return "ceasefire";
        return "continue";
    }

    @SuppressWarnings("unchecked")
    private List<String> handleOccupation(GameState state, Campaign camp) {
        List<String> msgs = new ArrayList<>();
        String atkFid = camp.getAttackerFaction();
        String defFid = camp.getDefenderFaction();
        String defType = camp.getDefenderType();
        FactionState atkFs = engine.getFactionState(state, atkFid);
        FactionState defFs = engine.getFactionState(state, defFid);
        String pname = camp.getProvinceName();
        String pid = camp.getProvince();

        // 防守方失去该省
        if (defFs != null && defFs.getTerritories() != null) {
            defFs.getTerritories().remove(pname);
            String relocMsg = engine.relocateCapitalIfLost(defFs, pname);
            if (relocMsg != null) msgs.add(relocMsg);
        }

        // 攻击方获得
        Province pdata = engine.getProvince(pid);
        boolean isCity = pdata != null && "city".equals(pdata.getType());
        boolean isClaimable = pdata == null || pdata.isClaimable();
        if (isClaimable || isCity) {
            if (atkFs.getTerritories() == null) atkFs.setTerritories(new ArrayList<>());
            if (!atkFs.getTerritories().contains(pname)) atkFs.getTerritories().add(pname);
        }

        // 城市 → 自动占领子地块
        if (isCity) {
            int claimed = autoClaimSuburbs(state, pname, atkFid);
            if (claimed > 0) msgs.add("  🏘 周边" + claimed + "个子地块自动归属");
        }

        // 防守方覆灭判定
        boolean defDead = false;
        if (defFs != null) {
            List<String> defTerrs = defFs.getTerritories();
            defDead = (defTerrs == null || defTerrs.isEmpty()) && defFid != null && !state.getDefeatedFactions().contains(defFid);
        } else if ("npc".equals(defType) || "npc_faction".equals(defType)) {
            // NPC：检查是否还有未被玩家占领的领土
            NpcDefinition ndata = engine.getGameData().getHostileNpcs().get(defFid);
            if (ndata != null) {
                defDead = ndata.isDefeatedBy(atkFs.getTerritories());
            }
        }

        if (defDead) {
            if (state.getDefeatedFactions() == null) state.setDefeatedFactions(new ArrayList<>());
            state.getDefeatedFactions().add(defFid);
            String defFullName = camp.getDefenderName();
            String atkFullName = camp.getAttackerName();
            String defeatMsg;
            if (state.getPlayerFactionId().equals(atkFid)) {
                defeatMsg = "💀 " + defFullName + " 已被我军彻底消灭！";
            } else if (state.getPlayerFactionId().equals(defFid)) {
                defeatMsg = "💀 我军已被" + atkFullName + "彻底消灭！";
            } else {
                defeatMsg = "💀 " + defFullName + " 已被" + atkFullName + "彻底消灭！";
            }
            msgs.add(defeatMsg);
            if (state.getDefeatEvents() == null) state.setDefeatEvents(new ArrayList<>());
            state.getDefeatEvents().add(GameUtils.mapOf("name", defFullName, "turn", state.getTurn(), "text", defeatMsg,
                    "eliminated_faction", defFullName, "eliminator_faction", atkFullName,
                    "eliminator_fid", atkFid, "eliminated_fid", defFid));
        }
        return msgs;
    }

    int autoClaimSuburbs(GameState state, String cityName, String factionId) {
        Map<String, Province> provinces = engine.getMapData().getAll();
        Set<String> garrisoned = new HashSet<>();
        // 收集所有驻军位置
        collectGarrisonedPids(state.getFactionState().getUnits(), garrisoned);
        for (var ad : state.getAiFactions().entrySet()) {
            FactionState afs = ad.getValue().getFactionState();
            if (afs != null && afs.getUnits() != null) collectGarrisonedPids(afs.getUnits(), garrisoned);
        }

        FactionState targetFs = engine.getFactionState(state, factionId);
        int claimed = 0;
        for (var entry : provinces.entrySet()) {
            Province p = entry.getValue();
            if (cityName.equals(p.getParentCity()) && !garrisoned.contains(entry.getKey())) {
                if (targetFs.getTerritories() == null) targetFs.setTerritories(new ArrayList<>());
                if (!targetFs.getTerritories().contains(p.getName())) {
                    targetFs.getTerritories().add(p.getName());
                    claimed++;
                }
            }
        }
        return claimed;
    }

    private void collectGarrisonedPids(List<Unit> units, Set<String> set) {
        if (units == null) return;
        for (Unit u : units) {
            String pos = u.getPosition();
            if (pos != null && !pos.isEmpty()) set.add(pos);
        }
    }

    private String getProvinceOwner(GameState state, String pid) {
        Province p = engine.getProvince(pid);
        if (p == null) return null;
        if (state.getFactionState().getTerritories().contains(p.getName())) return state.getPlayerFactionId();
        for (var ae : state.getAiFactions().entrySet()) {
            FactionState afs = ae.getValue().getFactionState();
            if (afs != null && afs.getTerritories() != null && afs.getTerritories().contains(p.getName()))
                return ae.getKey();
        }
        return null;
    }

    private void triggerAutoAttack(GameState state, Unit unit, String targetPid, String tactic) {
        FactionState fs = state.getFactionState();
        int unitIdx = -1;
        for (int i = 0; i < fs.getUnits().size(); i++) {
            if (unit.getName().equals(fs.getUnits().get(i).getName())) { unitIdx = i; break; }
        }
        if (unitIdx < 0) return;
        startCampaign(state, targetPid, List.of(unitIdx), Map.of(unit.getName(), tactic));
    }

    Unit findUnitByName(GameState state, String unitName) {
        for (Unit u : state.getFactionState().getUnits()) {
            if (unitName.equals(u.getName())) return u;
        }
        for (var ad : state.getAiFactions().entrySet()) {
            FactionState afs = ad.getValue().getFactionState();
            if (afs != null && afs.getUnits() != null) {
                for (Unit u : afs.getUnits()) {
                    if (unitName.equals(u.getName())) return u;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildCampaignResult(Campaign camp, String outcome, double ratio,
                                                     int atkCasualties, int defCasualties,
                                                     boolean provinceFell, String msg, boolean isPlayerAttacker) {
        Map<String, String> outcomeMap = isPlayerAttacker ? Map.of(
                "annihilate", "歼灭性大胜", "decisive_win", "大胜", "costly_win", "惨胜",
                "stalemate", "持平", "setback", "受挫", "rout", "溃败",
                "ceasefire", "停战", "attacker_occupied", "我军占领", "defender_held", "敌军固守",
                "stalemate_end", "战线僵持") : Map.of(
                "annihilate", camp.getAttackerName() + "歼灭性大胜", "decisive_win", camp.getAttackerName() + "大胜",
                "costly_win", camp.getAttackerName() + "惨胜", "stalemate", "双方持平",
                "setback", camp.getAttackerName() + "受挫", "rout", camp.getAttackerName() + "溃败",
                "ceasefire", camp.getAttackerName() + "与" + camp.getDefenderName() + "停战",
                "attacker_occupied", camp.getAttackerName() + "占领", "defender_held", camp.getDefenderName() + "固守",
                "stalemate_end", "战线僵持");

        List<String> atkUnitNames = camp.getAttackerCache() != null ?
                camp.getAttackerCache().stream().map(Unit::getName).collect(Collectors.toList()) : List.of();
        boolean honorAvailable = isPlayerAttacker && (List.of("annihilate", "decisive_win", "costly_win").contains(outcome) || provinceFell);
        int honorCost = Map.of("annihilate", 8, "decisive_win", 5, "costly_win", 3).getOrDefault(outcome, 3);

        return GameUtils.mapOf("id", camp.getId(), "province", camp.getProvince(), "province_name", camp.getProvinceName(),
                "outcome", outcome, "outcome_cn", outcomeMap.getOrDefault(outcome, outcome),
                "round", camp.getRound(), "ratio", Math.round(ratio * 10) / 10.0,
                "atk_casualties", atkCasualties, "def_casualties", defCasualties,
                "province_fell", provinceFell, "attacker_faction", camp.getAttackerFaction(),
                "defender_faction", camp.getDefenderFaction(), "attacker_name", camp.getAttackerName(),
                "defender_name", camp.getDefenderName(), "attacker_units", atkUnitNames,
                "is_player_attacker", isPlayerAttacker, "honor_available", honorAvailable,
                "honor_cost", honorCost, "message", msg);
    }

    private boolean isDead(Unit u) {
        return "annihilated".equals(u.getStatus()) || "surrendered".equals(u.getStatus());
    }

    private List<Unit> activeUnits(List<Unit> units) {
        return units == null ? List.of() : units.stream().filter(u -> !isDead(u)).collect(Collectors.toList());
    }

    /** 将部队位置（可能是领土名或PID）统一转为PID */
    private String resolveUnitPid(String pos) {
        return engine.resolvePositionToPid(pos);
    }

    /** 战役结算结果 */
    public static class BattleResult {
        public final List<String> messages;
        public final List<Map<String, Object>> structured;
        public BattleResult(List<String> messages, List<Map<String, Object>> structured) {
            this.messages = messages; this.structured = structured;
        }
    }
}
