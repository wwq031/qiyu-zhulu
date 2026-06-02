package com.qiyuzhulu.service;

import com.qiyuzhulu.model.*;
import com.qiyuzhulu.repo.GameDataRepo;
import static com.qiyuzhulu.service.GameUtils.mapOf;
import static com.qiyuzhulu.service.GameUtils.mapOfS;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 外交系统 — 外交/国策/情报。
 * 对应 Python qiyu_actions_diplomacy.py（1047行）。
 */
@Service
public class DiplomacyService {

    private final GameEngine engine;
    private final GameDataRepo gameData;
    private final Random rng = new Random();

    public DiplomacyService(GameEngine engine, GameDataRepo gameData) {
        this.engine = engine;
        this.gameData = gameData;
    }

    // ═══════════════════════════════════════════ 外交目标 ═══════════════════════════════════════════

    /** 收集可外交目标列表 */
    public List<Map<String, Object>> getDiploTargets(GameState state) {
        String fid = state.getPlayerFactionId();
        List<Map<String, Object>> targets = new ArrayList<>();
        Map<String, FactionDefinition> factions = gameData.getFactions();
        for (var entry : factions.entrySet()) {
            String tfid = entry.getKey();
            if (tfid.equals(fid)) continue;
            if (state.getDefeatedFactions().contains(tfid)) continue;
            FactionDefinition tf = entry.getValue();
            Map<String, Object> rel = new LinkedHashMap<>();
            var dr = state.getDiplomaticRelations().get(tfid);
            if (dr != null) {
                rel.put("score", dr.getScore());
                rel.put("pact", dr.getPact());
            }
            targets.add(mapOf(
                    "id", tfid, "name", tf.getName(), "region", tf.getRegion(),
                    "ideology", tf.getIdeology(), "military", tf.getStats().getMilitary(),
                    "economy", tf.getStats().getEconomy(), "leader", tf.getLeader() != null ? tf.getLeader().getName() : "",
                    "relation", rel.getOrDefault("score", 0), "pact", rel.get("pact"),
                    "at_war", state.getActiveWars().contains(tfid)));
        }
        targets.sort(Comparator.comparing((Map<String, Object> t) -> Boolean.TRUE.equals(t.get("at_war")) ? 0 : 1)
                .thenComparing(t -> -Math.abs(((Number) t.getOrDefault("relation", 0)).intValue())));
        return targets;
    }

    // ═══════════════════════════════════════════ 外交操作 ═══════════════════════════════════════════

    /**
     * 执行Web外交操作。
     * sub: '1'互不侵犯 '2'军事同盟 '3'宣战 '4'和谈 '5'列强援助 '6'贸易协定
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> doDiploAction(GameState state, String sub, int targetIdx) {
        FactionState fs = state.getFactionState();
        Stats s = fs.getStats();
        List<Map<String, Object>> targets = getDiploTargets(state);
        Map<String, Object> target = null;
        boolean needsTarget = List.of("1", "2", "3", "4", "6").contains(sub);
        if (needsTarget) {
            if (targetIdx < 0 || targetIdx >= targets.size())
                return mapOf("ok", false, "message", "无效外交目标");
            target = targets.get(targetIdx);
        }

        switch (sub) {
            case "1": return signNonAggression(state, fs, s, (String) target.get("id"), (String) target.get("name"),
                    (Integer) target.get("relation"), (String) target.get("pact"), (Boolean) target.get("at_war"));
            case "2": return formAlliance(state, fs, s, (String) target.get("id"), (String) target.get("name"),
                    (Integer) target.get("relation"), (String) target.get("pact"), (Boolean) target.get("at_war"));
            case "3": return declareWar(state, fs, (String) target.get("id"), (String) target.get("name"),
                    (Integer) target.get("relation"), (Boolean) target.get("at_war"));
            case "4": return makePeace(state, fs, s, (String) target.get("id"), (String) target.get("name"),
                    (Integer) target.get("relation"), (Boolean) target.get("at_war"));
            case "5": return requestForeignAid(state, fs, s);
            case "6": return establishTrade(state, fs, s, (String) target.get("id"), (String) target.get("name"),
                    (Integer) target.get("relation"), (Boolean) target.get("at_war"));
            default: return mapOf("ok", false, "message", "无效外交操作");
        }
    }

    private Map<String, Object> signNonAggression(GameState state, FactionState fs, Stats s,
                                                   String tid, String tname, int rel, String pact, boolean atWar) {
        if (s.getDiplomacy() < 40) return mapOf("ok", false, "message", "外交不足（需外交≥40，当前" + s.getDiplomacy() + "）");
        if (fs.getTreasury() < 15) return mapOf("ok", false, "message", "国库不足（需15💰）");
        if (pact != null && !pact.isEmpty()) return mapOf("ok", false, "message", "与该势力已有条约: " + pact);
        if (atWar) return mapOf("ok", false, "message", "正在交战，无法签订条约。请先和谈。");
        fs.setTreasury(fs.getTreasury() - 15);
        ensureRel(state, tid).setScore(rel + 30);
        ensureRel(state, tid).setPact("non_aggression");
        ensureRel(state, tid).setTurnsLeft(12);
        s.setDiplomacy(GameEngine.clamp(s.getDiplomacy() + rng.nextInt(4) + 2));
        incTracker(state, "pacts_signed");
        return mapOf("ok", true, "message", "✅ 与" + tname + "签订互不侵犯条约（12回合）。外交+" + (rng.nextInt(4) + 2));
    }

    private Map<String, Object> formAlliance(GameState state, FactionState fs, Stats s,
                                              String tid, String tname, int rel, String pact, boolean atWar) {
        if (s.getDiplomacy() < 60) return mapOf("ok", false, "message", "外交不足（需外交≥60，当前" + s.getDiplomacy() + "）");
        if (fs.getTreasury() < 25) return mapOf("ok", false, "message", "国库不足（需25💰）");
        if ("alliance".equals(pact)) return mapOf("ok", false, "message", "已与该势力结盟");
        if (atWar) return mapOf("ok", false, "message", "正在交战，无法结盟。请先和谈。");
        fs.setTreasury(fs.getTreasury() - 25);
        ensureRel(state, tid).setScore(rel + 50);
        ensureRel(state, tid).setPact("alliance");
        ensureRel(state, tid).setTurnsLeft(16);
        s.setDiplomacy(GameEngine.clamp(s.getDiplomacy() + rng.nextInt(4) + 3));
        s.setMilitary(GameEngine.clamp(s.getMilitary() + rng.nextInt(3) + 1));
        incTracker(state, "pacts_signed");
        return mapOf("ok", true, "message", "🤝 与" + tname + "结成军事同盟（16回合）。外交+ 军事+");
    }

    private Map<String, Object> declareWar(GameState state, FactionState fs,
                                            String tid, String tname, int rel, boolean atWar) {
        if (atWar) return mapOf("ok", false, "message", "已与该势力处于战争状态");
        if (!state.getActiveWars().contains(tid)) state.getActiveWars().add(tid);
        ensureRel(state, tid).setScore(rel - 50);
        ensureRel(state, tid).setPact(null);
        incTracker(state, "wars_started");
        return mapOf("ok", true, "message", "⚔ 向" + tname + "宣战！");
    }

    private Map<String, Object> makePeace(GameState state, FactionState fs, Stats s,
                                           String tid, String tname, int rel, boolean atWar) {
        if (!atWar) return mapOf("ok", false, "message", "未与该势力交战");
        if (s.getDiplomacy() < 35) return mapOf("ok", false, "message", "外交不足（需外交≥35，当前" + s.getDiplomacy() + "）");
        if (fs.getTreasury() < 10) return mapOf("ok", false, "message", "国库不足（需10💰）");
        fs.setTreasury(fs.getTreasury() - 10);
        state.getActiveWars().remove(tid);
        DiplomaticRelation dr = ensureRel(state, tid);
        dr.setScore(0);
        dr.setPact("non_aggression");
        dr.setTurnsLeft(8);
        s.setDiplomacy(GameEngine.clamp(s.getDiplomacy() + rng.nextInt(3) + 1));
        return mapOf("ok", true, "message", "🕊 与" + tname + "达成和约（互不侵犯8回合）。外交+");
    }

    private Map<String, Object> requestForeignAid(GameState state, FactionState fs, Stats s) {
        if (s.getDiplomacy() < 50) return mapOf("ok", false, "message", "外交不足（需外交≥50，当前" + s.getDiplomacy() + "）");
        if (fs.getTreasury() < 5) return mapOf("ok", false, "message", "国库不足（需5💰）");
        fs.setTreasury(fs.getTreasury() - 5);
        int bonusEco = rng.nextInt(16) + 10;  // 10-25
        int bonusMil = rng.nextInt(6) + 3;     // 3-8
        fs.setTreasury(fs.getTreasury() + bonusEco);
        s.setMilitary(GameEngine.clamp(s.getMilitary() + bonusMil));
        s.setDiplomacy(GameEngine.clamp(s.getDiplomacy() + rng.nextInt(3) + 2));
        StringBuilder msg = new StringBuilder("列强援助抵达：+" + bonusEco + "💰 +" + bonusMil + "军事 外交+");
        if (rng.nextDouble() < 0.15) {
            int ideoPenalty = rng.nextInt(4) + 3;
            s.setIdeology(GameEngine.clamp(s.getIdeology() - ideoPenalty));
            msg.append(" 但自主性受损 思想-").append(ideoPenalty);
        }
        incTracker(state, "foreign_powers_defied");
        return mapOf("ok", true, "message", msg.toString());
    }

    private Map<String, Object> establishTrade(GameState state, FactionState fs, Stats s,
                                                String tid, String tname, int rel, boolean atWar) {
        if (s.getDiplomacy() < 35) return mapOf("ok", false, "message", "外交不足（需外交≥35，当前" + s.getDiplomacy() + "）");
        if (fs.getTreasury() < 8) return mapOf("ok", false, "message", "国库不足（需8💰）");
        if (atWar) return mapOf("ok", false, "message", "正在交战，无法贸易。");
        fs.setTreasury(fs.getTreasury() - 8);
        ensureRel(state, tid).setScore(rel + 20);
        ensureRel(state, tid).setPact("trade");
        ensureRel(state, tid).setTurnsLeft(10);
        s.setEconomy(GameEngine.clamp(s.getEconomy() + rng.nextInt(4) + 2));
        s.setDiplomacy(GameEngine.clamp(s.getDiplomacy() + rng.nextInt(3) + 1));
        incTracker(state, "pacts_signed");
        return mapOf("ok", true, "message", "📈 与" + tname + "建立贸易协定（10回合，每回合+3💰收入）。经济+ 外交+");
    }

    // ═══════════════════════════════════════════ 休战提议 ═══════════════════════════════════════════

    /** 向目标提议休战，AI判定接受/拒绝 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> proposeTruce(GameState state, String targetId) {
        Map<String, Integer> pacts = state.getNonAggressionPacts();
        if (pacts.getOrDefault(targetId, 0) > 0) return mapOf("ok", false, "message", "双方已在休战期");
        var tf = engine.getFaction(targetId).orElse(null);
        if (tf == null) return mapOf("ok", false, "message", "目标势力不存在");

        FactionState fs = state.getFactionState();
        if (state.getActionPoints() < 1) return mapOf("ok", false, "message", "行动点不足");
        state.setActionPoints(state.getActionPoints() - 1);

        int myMil = fs.getStats().getMilitary();
        FactionState targetFs = engine.getFactionState(state, targetId);
        int targetMil = targetFs != null && targetFs.getStats() != null ? targetFs.getStats().getMilitary() : 30;

        double accept = 0.4;
        if (targetMil < myMil) accept += 0.3;
        boolean underAttack = state.getActiveCampaigns().stream()
                .anyMatch(c -> "ongoing".equals(c.getStatus()) && tf.getName().equals(c.getDefenderName()));
        if (underAttack) accept += 0.5;

        String aiPers = tf.getAiPersonality() != null ? tf.getAiPersonality() : "";
        if (aiPers.contains("扩张") || aiPers.contains("激进")) accept -= 0.3;
        if (aiPers.contains("保守")) accept += 0.2;

        if (rng.nextDouble() < accept) {
            pacts.put(targetId, 3);
            return mapOf("ok", true, "message", tf.getName() + "接受了休战提议，双方互不侵犯3回合");
        } else {
            return mapOf("ok", false, "message", tf.getName() + "拒绝了休战提议");
        }
    }

    // ═══════════════════════════════════════════ 情报操作 ═══════════════════════════════════════════

    /**
     * 执行Web情报操作。sub: '1'侦察 '2'维稳 '3'邻区 '4'反间谍
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> doIntelAction(GameState state, String sub) {
        FactionState fs = state.getFactionState();
        Stats s = fs.getStats();
        String fid = state.getPlayerFactionId();

        switch (sub) {
            case "1": return intelScout(state, fs, s, fid);
            case "2": return intelStability(state, fs, s);
            case "3": return intelNeighbor(state, fs, s, fid);
            case "4": return intelCounterSpy(state, fs, s, fid);
            default: return mapOf("ok", false, "message", "无效情报操作");
        }
    }

    /** 侦察：返回目标势力的情报报告 */
    public Map<String, Object> intelScout(GameState state, FactionState fs, Stats s, String fid) {
        String playerRegion = engine.getFaction(fid).map(FactionDefinition::getRegion).orElse("");
        List<String> validRegions = new ArrayList<>(GameEngine.REGION_ADJACENCY.getOrDefault(playerRegion, List.of()));
        validRegions.add(playerRegion);

        // 找同区敌对势力
        List<Map<String, Object>> hostiles = new ArrayList<>();
        Map<String, FactionDefinition> factions = gameData.getFactions();
        for (var entry : factions.entrySet()) {
            if (entry.getKey().equals(fid)) continue;
            if (state.getDefeatedFactions().contains(entry.getKey())) continue;
            if (validRegions.contains(entry.getValue().getRegion())) {
                hostiles.add(mapOf("id", entry.getKey(), "name", entry.getValue().getName(),
                        "ideology", entry.getValue().getIdeology(), "stats", entry.getValue().getStats()));
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("ok", true);
        report.put("type", "intel_scout");

        if (hostiles.isEmpty()) {
            report.put("message", "本区无敌对势力");
            return report;
        }

        // 随机选一个目标做侦察
        Map<String, Object> target = hostiles.get(rng.nextInt(hostiles.size()));
        Stats ts = (Stats) target.get("stats");
        int scoutQuality = Math.min(20, (s.getDiplomacy() + s.getIdeology()) / 5);
        int errorRange = Math.max(3, 15 - scoutQuality);

        List<Map<String, Object>> estimates = new ArrayList<>();
        String[] keys = {"industry", "agriculture", "military", "economy", "ideology", "diplomacy"};
        String[] labels = {"工业", "农业", "军事", "经济", "思想", "外交"};
        for (int i = 0; i < keys.length; i++) {
            int real = ts.get(keys[i]);
            int lo = Math.max(0, real - rng.nextInt(errorRange + 1) - 3);
            int hi = Math.min(100, real + rng.nextInt(errorRange + 1) + 3);
            String confidence = (hi - lo) < 8 ? "高" : ((hi - lo) < 15 ? "中" : "低");
            estimates.add(mapOf("key", keys[i], "label", labels[i], "lo", lo, "hi", hi, "confidence", confidence));
        }

        // 部队推算
        int forcesCount = factions.get(target.get("id")).getInitialForces().size();
        int estimated = Math.max(1, forcesCount + rng.nextInt(5) - 2);

        // 威胁评估
        int threat = rng.nextInt(41) + 30; // 30-70, simplified
        String stance = threat > 60 ? "⚔ 高度敌对 — 可能即将采取军事行动"
                : (threat > 35 ? "⚠ 谨慎观望 — 视局势变化调整立场" : "🤝 相对友好 — 短期内不太可能主动进攻");

        report.put("target_name", target.get("name"));
        report.put("target_ideology", target.get("ideology"));
        report.put("estimates", estimates);
        report.put("estimated_forces", estimated);
        report.put("stance", stance);
        report.put("threat", threat);
        report.put("error_range", errorRange);

        s.setDiplomacy(GameEngine.clamp(s.getDiplomacy() + rng.nextInt(2) + 1));
        report.put("message", "📡 情报侦察完成：已获取 " + target.get("name") + " 的军力估值（误差±" + errorRange + "）");
        return report;
    }

    private Map<String, Object> intelStability(GameState state, FactionState fs, Stats s) {
        if (fs.getTreasury() < 5) return mapOf("ok", false, "message", "国库不足（需5💰）");
        fs.setTreasury(fs.getTreasury() - 5);
        int ideoGain = rng.nextInt(3) + 1;
        int supportGain = rng.nextInt(6) + 3;
        s.setIdeology(GameEngine.clamp(s.getIdeology() + ideoGain));
        fs.setPopulationSupport(GameEngine.clamp(fs.getPopulationSupport() + supportGain));
        incTracker(state, "stability_ops");
        return mapOf("ok", true, "message", "✅ 内部维稳完成。思想+" + ideoGain + " 民心+" + supportGain + "% 消耗5💰");
    }

    private Map<String, Object> intelNeighbor(GameState state, FactionState fs, Stats s, String fid) {
        String playerRegion = engine.getFaction(fid).map(FactionDefinition::getRegion).orElse("");
        List<String> adj = GameEngine.REGION_ADJACENCY.getOrDefault(playerRegion, List.of());
        List<Map<String, Object>> regionReports = new ArrayList<>();

        for (String rid : adj) {
            List<Map<String, String>> rfList = gameData.getFactions().values().stream()
                    .filter(ff -> rid.equals(ff.getRegion()) && !state.getDefeatedFactions().contains(ff.getId()))
                    .map(ff -> mapOfS("name", ff.getName(), "ideology", ff.getIdeology(),
                            "mil", String.valueOf(ff.getStats().getMilitary()),
                            "eco", String.valueOf(ff.getStats().getEconomy())))
                    .collect(Collectors.toList());
            String regionName = GameEngine.REGION_NAMES.getOrDefault(rid, rid);
            regionReports.add(mapOf("region_id", rid, "region_name", regionName, "faction_count", rfList.size(), "factions", rfList));
        }
        s.setDiplomacy(GameEngine.clamp(s.getDiplomacy() + rng.nextInt(2) + 1));
        return mapOf("ok", true, "message", "邻区侦察完成", "type", "intel_neighbor", "regions", regionReports);
    }

    private Map<String, Object> intelCounterSpy(GameState state, FactionState fs, Stats s, String fid) {
        if (fs.getTreasury() < 6) return mapOf("ok", false, "message", "国库不足（需6💰）");
        fs.setTreasury(fs.getTreasury() - 6);
        s.setDiplomacy(GameEngine.clamp(s.getDiplomacy() + rng.nextInt(2) + 1));
        s.setIdeology(GameEngine.clamp(s.getIdeology() + rng.nextInt(2) + 1));
        incTracker(state, "espionage_ops");

        boolean spyFound = rng.nextDouble() < 0.4;
        StringBuilder msg = new StringBuilder("🛡 反间谍行动完成。外交+ 思想+ 消耗6💰");
        if (spyFound) {
            // 随机选同区敌对势力作为间谍源
            String playerRegion = engine.getFaction(fid).map(FactionDefinition::getRegion).orElse("");
            List<Map<String, String>> sameRegion = gameData.getFactions().values().stream()
                    .filter(ff -> playerRegion.equals(ff.getRegion()) && !ff.getId().equals(fid)
                            && !state.getDefeatedFactions().contains(ff.getId()))
                    .map(ff -> mapOfS("id", ff.getId(), "name", ff.getName()))
                    .collect(Collectors.toList());
            if (!sameRegion.isEmpty()) {
                Map<String, String> spy = sameRegion.get(rng.nextInt(sameRegion.size()));
                DiplomaticRelation dr = ensureRel(state, spy.get("id"));
                dr.setScore(dr.getScore() - 20);
                msg.append(" 发现间谍网络！源头：").append(spy.get("name")).append("（关系-20）");
            }
        }
        return mapOf("ok", true, "message", msg.toString());
    }

    // ═══════════════════════════════════════════ 国策系统 ═══════════════════════════════════════════

    /** 列出所有国策决议及可用状态 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> listResolutions(GameState state) {
        Map<String, Object> rsData = gameData.getResolutions();
        if (rsData == null || !rsData.containsKey("resolutions"))
            return mapOf("resolutions", List.of(), "chains", Map.of());

        FactionState fs = state.getFactionState();
        Stats s = fs.getStats();
        List<String> enacted = state.getEnactedResolutions();
        String fid = state.getPlayerFactionId();
        var faction = engine.getFaction(fid).orElse(null);
        if (faction == null) return mapOf("resolutions", List.of(), "chains", Map.of());

        List<Map<String, Object>> results = new ArrayList<>();
        List<Map<String, Object>> resList = (List<Map<String, Object>>) rsData.get("resolutions");
        Map<String, Map<String, Object>> resMap = (Map<String, Map<String, Object>>) rsData.getOrDefault("categories", Map.of());

        for (Map<String, Object> res : resList) {
            Map<String, Object> conds = (Map<String, Object>) res.getOrDefault("conditions", Map.of());
            boolean ok = true;
            List<String> missing = new ArrayList<>();
            int phaseMin = ((Number) conds.getOrDefault("phase_min", 0)).intValue();
            if (phaseMin > 0 && state.getPhase() < phaseMin) { ok = false; missing.add("阶段≥" + phaseMin); }
            int phaseMax = ((Number) conds.getOrDefault("phase_max", 99)).intValue();
            if (phaseMax < 99 && state.getPhase() > phaseMax) ok = false;
            if (conds.containsKey("military_min") && s.getMilitary() < ((Number) conds.get("military_min")).intValue())
            { ok = false; missing.add("军事≥" + conds.get("military_min")); }
            if (conds.containsKey("economy_min") && s.getEconomy() < ((Number) conds.get("economy_min")).intValue())
            { ok = false; missing.add("经济≥" + conds.get("economy_min")); }
            if (conds.containsKey("ideology_min") && s.getIdeology() < ((Number) conds.get("ideology_min")).intValue())
            { ok = false; missing.add("思想≥" + conds.get("ideology_min")); }
            if (conds.containsKey("diplomacy_min") && s.getDiplomacy() < ((Number) conds.get("diplomacy_min")).intValue())
            { ok = false; missing.add("外交≥" + conds.get("diplomacy_min")); }
            if (conds.containsKey("ideology_match")) {
                List<String> matches = (List<String>) conds.get("ideology_match");
                if (!matches.contains(faction.getIdeology())) { ok = false; missing.add("意识形态需为：" + String.join(",", matches)); }
            }
            if (conds.containsKey("requires")) {
                for (Object reqId : (List<Object>) conds.get("requires")) {
                    if (!enacted.contains((String) reqId)) { ok = false; missing.add("需先执行: " + reqId); }
                }
            }
            if (conds.containsKey("region") && !((String) conds.get("region")).equals(faction.getRegion())) ok = false;
            if (Boolean.TRUE.equals(conds.get("region_unified")) && !engine.isRegionUnified(state, fid))
            { ok = false; missing.add("需先统一本区域"); }

            boolean executed = enacted.contains(res.get("id"));
            if (Boolean.TRUE.equals(res.get("once")) && executed) ok = false;

            String catId = (String) res.get("category");
            String catName = resMap.containsKey(catId) ? (String) ((Map<String, Object>) resMap.get(catId)).get("name") : catId;

            results.add(mapOf("id", res.get("id"), "name", res.get("name"), "category", catName,
                    "description", res.getOrDefault("description", ""),
                    "available", ok && !executed, "executed", executed, "missing", missing,
                    "effects", res.getOrDefault("effects", Map.of()),
                    "chain", res.getOrDefault("chain", ""), "step", res.getOrDefault("step", 0),
                    "chain_name", res.getOrDefault("chain_name", "")));
        }
        return mapOf("resolutions", results, "chains", rsData.getOrDefault("chains", Map.of()));
    }

    /** 执行指定ID的国策 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeResolution(GameState state, String resId) {
        Map<String, Object> listResult = listResolutions(state);
        List<Map<String, Object>> resolutions = (List<Map<String, Object>>) listResult.get("resolutions");
        Map<String, Object> target = null;
        for (Map<String, Object> r : resolutions) {
            if (resId.equals(r.get("id"))) { target = r; break; }
        }
        if (target == null) return mapOf("ok", false, "message", "决议不存在");
        if (Boolean.TRUE.equals(target.get("executed"))) return mapOf("ok", false, "message", "决议已执行");
        if (!Boolean.TRUE.equals(target.get("available"))) {
            @SuppressWarnings("unchecked")
            List<String> missing = (List<String>) target.get("missing");
            return mapOf("ok", false, "message", "条件不足：" + (missing != null ? String.join("; ", missing) : "未知"));
        }

        // 从resolutions原始数据获取
        Map<String, Object> rsData = gameData.getResolutions();
        List<Map<String, Object>> rawList = (List<Map<String, Object>>) rsData.get("resolutions");
        Map<String, Object> raw = null;
        for (Map<String, Object> r : rawList) {
            if (resId.equals(r.get("id"))) { raw = r; break; }
        }
        if (raw == null) return mapOf("ok", false, "message", "决议数据缺失");

        FactionState fs = state.getFactionState();
        Stats s = fs.getStats();

        // 应用效果
        Map<String, Object> effects = (Map<String, Object>) raw.getOrDefault("effects", Map.of());
        for (var entry : effects.entrySet()) {
            String k = entry.getKey();
            int v = ((Number) entry.getValue()).intValue();
            if (GameEngine.STAT_NAMES.containsKey(k)) s.set(k, GameEngine.clamp(s.get(k) + v));
            else if ("treasury".equals(k)) fs.setTreasury(fs.getTreasury() + v);
            else if ("population_support".equals(k)) fs.setPopulationSupport(GameEngine.clamp(fs.getPopulationSupport() + v));
        }

        // 应用cost
        Map<String, Object> cost = (Map<String, Object>) raw.getOrDefault("cost", Map.of());
        for (var entry : cost.entrySet()) {
            String k = entry.getKey();
            int v = ((Number) entry.getValue()).intValue();
            if (GameEngine.STAT_NAMES.containsKey(k)) s.set(k, GameEngine.clamp(s.get(k) - Math.abs(v)));
            else if ("economy".equals(k)) fs.setTreasury(fs.getTreasury() - Math.abs(v));
        }

        if (state.getEnactedResolutions() == null) state.setEnactedResolutions(new ArrayList<>());
        state.getEnactedResolutions().add(resId);
        incTracker(state, "resolutions_enacted");

        // 应用国策国魂
        Map<String, Object> spiritData = (Map<String, Object>) raw.get("spirit");
        if (spiritData != null) {
            NationalSpirit spirit = new NationalSpirit();
            spirit.setName((String) spiritData.get("name"));
            spirit.setDesc((String) spiritData.get("desc"));
            Map<String, Integer> spiritEff = (Map<String, Integer>) (Object) spiritData.get("effects");
            spirit.setEffects(spiritEff);
            fs.setNationalSpirit(spirit);
            if (spiritEff != null) {
                for (var e : spiritEff.entrySet())
                    s.set(e.getKey(), GameEngine.clamp(s.get(e.getKey()) + e.getValue()));
            }
        }

        return mapOf("ok", true, "message", "📜 决议颁布：" + raw.get("name"),
                "narrative", raw.getOrDefault("narrative", ""), "effects", effects);
    }

    // ═══════════════════════════════════════════ 工具方法 ═══════════════════════════════════════════

    private DiplomaticRelation ensureRel(GameState state, String fid) {
        return state.getDiplomaticRelations().computeIfAbsent(fid, k -> {
            DiplomaticRelation dr = new DiplomaticRelation();
            dr.setScore(0);
            dr.setPact(null);
            dr.setTurnsLeft(0);
            return dr;
        });
    }

    @SuppressWarnings("unchecked")
    private void incTracker(GameState state, String key) {
        Map<String, Object> tracker = (Map<String, Object>) state.getStatsTracker();
        if (tracker != null) tracker.put(key, ((Number) tracker.getOrDefault(key, 0)).intValue() + 1);
    }

}
