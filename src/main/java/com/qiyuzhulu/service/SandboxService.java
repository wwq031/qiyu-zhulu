package com.qiyuzhulu.service;

import com.qiyuzhulu.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 沙盒/自由指令系统 — AI GM裁决、自由行动。
 * 对应 Python qiyu_actions_sandbox.py。
 */
@Service
public class SandboxService {

    private final GameEngine engine;
    private final AiProviderService aiProvider;
    private final DiplomacyService diplomacy;
    private final MilitaryService military;
    private final CampaignService campaign;
    private final TechService techService;
    private final Random rng = new Random();

    public SandboxService(GameEngine engine, AiProviderService aiProvider,
                           DiplomacyService diplomacy, MilitaryService military,
                           CampaignService campaign, TechService techService) {
        this.engine = engine;
        this.aiProvider = aiProvider;
        this.diplomacy = diplomacy;
        this.military = military;
        this.campaign = campaign;
        this.techService = techService;
    }

    /** 构建自由指令裁决上下文（发送给AI GM或前端显示） */
    public Map<String, Object> buildContext(GameState state, String order) {
        FactionState fs = state.getFactionState();
        Stats s = fs.getStats();

        List<String> unitBriefs = new ArrayList<>();
        for (Unit u : fs.getUnits().subList(0, Math.min(8, fs.getUnits().size()))) {
            Province p = engine.getProvince(u.getPosition());
            String loc = p != null ? p.getName() : (u.getPosition() != null ? u.getPosition() : "?");
            unitBriefs.add(u.getName() + "[" + u.getType() + "]@" + loc + " 兵" + u.getStrength());
        }

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("order", order);
        ctx.put("faction", fs.getName());
        ctx.put("stats", s);
        ctx.put("treasury", fs.getTreasury());
        ctx.put("territories", fs.getTerritories().subList(0, Math.min(8, fs.getTerritories().size())));
        ctx.put("units", unitBriefs);
        ctx.put("turn", state.getTurn());
        ctx.put("date", state.getGameDate());
        ctx.put("phase", state.getPhase());
        ctx.put("ap", state.getActionPoints());
        ctx.put("request", "请根据上述游戏状态评估此自由行动。以JSON返回裁决。");
        return ctx;
    }

    /** 裁决——根据配置的provider分发（local/DeepSeek/OpenAI/Claude） */
    public Map<String, Object> aiAdjudicate(GameState state, String order) {
        return aiProvider.adjudicate(buildContext(state, order));
    }

    /** 本地模板裁决（无AI时的fallback） */
    public Map<String, Object> localAdjudicate(GameState state, String order) {
        FactionState fs = state.getFactionState();
        Map<String, Object> result = new LinkedHashMap<>();

        // 关键词匹配
        String lower = order.toLowerCase();
        if (lower.contains("间谍") || lower.contains("情报") || lower.contains("侦察")) {
            result.put("feasibility", "high");
            result.put("cost", Map.of("treasury", -5));
            result.put("effects", Map.of("military", 2));
            result.put("risk", "low");
            result.put("ap_cost", 1);
            result.put("narrative", fs.getName() + "的情报人员渗透入敌境，带回了宝贵情报。");
        } else if (lower.contains("外交") || lower.contains("谈判") || lower.contains("使节")) {
            result.put("feasibility", "medium");
            result.put("cost", Map.of("treasury", -8));
            result.put("effects", Map.of("diplomacy", 3));
            result.put("risk", "medium");
            result.put("ap_cost", 1);
            result.put("narrative", fs.getName() + "派出使节，在外交战场上纵横捭阖。");
        } else if (lower.contains("建设") || lower.contains("发展") || lower.contains("工业")) {
            result.put("feasibility", "high");
            result.put("cost", Map.of("treasury", -12));
            result.put("effects", Map.of("industry", 3, "economy", 2));
            result.put("risk", "low");
            result.put("ap_cost", 2);
            result.put("narrative", fs.getName() + "启动了新一轮建设计划，工业发展势头良好。");
        } else if (lower.contains("偷袭") || lower.contains("游击") || lower.contains("骚扰")) {
            result.put("feasibility", "medium");
            result.put("cost", Map.of("treasury", -3));
            result.put("effects", Map.of("military", 2));
            result.put("risk", "high");
            result.put("ap_cost", 1);
            result.put("narrative", "一支小分队对敌境发动了突袭，成果有限但震慑效果显著。");
        } else if (lower.contains("宣传") || lower.contains("动员") || lower.contains("民心")) {
            result.put("feasibility", "high");
            result.put("cost", Map.of("treasury", -5));
            result.put("effects", Map.of("ideology", 4));
            result.put("risk", "low");
            result.put("ap_cost", 1);
            result.put("narrative", "宣传机器全力运转，民众对" + fs.getName() + "的支持率稳步上升。");
        } else {
            result.put("feasibility", "medium");
            result.put("cost", Map.of("treasury", -5));
            result.put("effects", Map.of());
            result.put("risk", "medium");
            result.put("ap_cost", 1);
            result.put("narrative", fs.getName() + "执行了「" + order + "」行动。局势尚无大的变化，但已经埋下了种子。");
        }
        return result;
    }

    /** 应用裁决结果 */
    public Map<String, Object> apply(GameState state, String order, Map<String, Object> adjudication) {
        Map<String, Object> resp = new LinkedHashMap<>();
        FactionState fs = state.getFactionState();

        String feasibility = (String) adjudication.getOrDefault("feasibility", "medium");
        if ("impossible".equals(feasibility)) {
            resp.put("success", false);
            resp.put("narrative", adjudication.getOrDefault("feasibility_reason", "该行动在当前条件下无法执行。"));
            return resp;
        }

        int ap = state.getActionPoints();
        int apCost = ((Number) adjudication.getOrDefault("ap_cost", 1)).intValue();
        apCost = Math.min(apCost, ap);
        state.setActionPoints(ap - apCost);

        // 应用cost
        @SuppressWarnings("unchecked")
        Map<String, Object> cost = (Map<String, Object>) adjudication.get("cost");
        if (cost != null) {
            for (var entry : cost.entrySet()) {
                String k = entry.getKey();
                int v = ((Number) entry.getValue()).intValue();
                if ("treasury".equals(k)) fs.setTreasury(fs.getTreasury() + v);
                else if (GameEngine.STAT_NAMES.containsKey(k))
                    fs.getStats().set(k, GameEngine.clamp(fs.getStats().get(k) + v));
                else if ("population_support".equals(k))
                    fs.setPopulationSupport(GameEngine.clamp(fs.getPopulationSupport() + v, 0, 100));
            }
        }

        // 应用effects
        @SuppressWarnings("unchecked")
        Map<String, Object> effects = (Map<String, Object>) adjudication.get("effects");
        if (effects != null) {
            for (var entry : effects.entrySet()) {
                String k = entry.getKey();
                int v = ((Number) entry.getValue()).intValue();
                if (GameEngine.STAT_NAMES.containsKey(k))
                    fs.getStats().set(k, GameEngine.clamp(fs.getStats().get(k) + v));
            }
        }

        // 风险掷骰
        String risk = (String) adjudication.getOrDefault("risk", "low");
        boolean riskTriggered = false;
        double threshold = switch (risk) {
            case "high" -> 0.35; case "medium" -> 0.20; default -> 0.10;
        };
        if (rng.nextDouble() < threshold) {
            riskTriggered = true;
            int penalty = rng.nextInt(switch (risk) {
                case "high" -> 4; case "medium" -> 2; default -> 1;
            }) + 1;
            fs.setPopulationSupport(GameEngine.clamp(fs.getPopulationSupport() - penalty, 0, 100));
        }

        // 持久标记
        String special = (String) adjudication.get("special_notes");
        if (special != null && !special.isEmpty()) {
            state.getCustomOrderFlags().add("T" + state.getTurn() + ": " + special);
        }

        resp.put("success", true);
        resp.put("order", order);
        resp.put("narrative", adjudication.get("narrative"));
        resp.put("cost", cost);
        resp.put("effects", effects);
        resp.put("feasibility", feasibility);
        resp.put("risk", risk);
        resp.put("risk_triggered", riskTriggered);
        resp.put("special", special);
        resp.put("provider", adjudication.getOrDefault("provider", "local"));

        // ── actions 数组 → 原子执行器分发 ──
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) adjudication.get("actions");
        if (actions != null && !actions.isEmpty()) {
            var results = dispatchActions(state, actions);
            resp.put("action_results", results.get("executed"));
            if (!((List<?>) results.get("failed")).isEmpty())
                resp.put("action_errors", results.get("failed"));
        }
        return resp;
    }

    // ═══════════════════════════════════════════ actions 执行器 ═══════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> dispatchActions(GameState state, List<Map<String, Object>> actions) {
        List<String> ok = new ArrayList<>(), fail = new ArrayList<>();
        if (actions.size() > 5) actions = actions.subList(0, 5);
        for (Map<String, Object> a : actions) {
            try {
                String type = (String) a.getOrDefault("type", "");
                String r = switch (type) {
                    case "train_unit" -> exeTrain(state, a);
                    case "move", "move_unit" -> exeMove(state, a);
                    case "build" -> exeBuild(state, a);
                    case "campaign", "attack" -> exeCampaign(state, a);
                    case "seize_province" -> exeSeize(state, a);
                    case "diplo", "declare_war" -> exeDiplo(state, a, type);
                    case "tech", "research" -> exeTech(state, a);
                    case "annex_faction", "annex" -> exeAnnex(state, a);
                    case "disband_unit" -> exeDisband(state, a);
                    case "modify_unit" -> exeModify(state, a);
                    case "reinforce" -> exeReinforce(state, a);
                    default -> "❌未知: " + type;
                };
                if (r.startsWith("✅")) ok.add(r.substring(2));
                else if (r.startsWith("❌")) fail.add(type + ": " + r.substring(2));
                else ok.add(r);
            } catch (Exception e) { fail.add("异常: " + e.getMessage()); }
        }
        return Map.of("executed", (Object) ok, "failed", (Object) fail);
    }

    private String exeTrain(GameState s, Map<String, Object> a) {
        String type = (String) a.getOrDefault("unit_type", "infantry");
        String loc = (String) a.getOrDefault("location", "");
        if (loc.isEmpty()) loc = s.getFactionState().getTerritories().get(0);
        String pid = engine.getPidByName(loc); if (pid == null) pid = loc;
        Map<String, Object> r = military.startTraining(s, type, pid, false);
        return (Boolean.TRUE.equals(r.get("ok")) ? "✅" : "❌") + r.get("message");
    }
    private String exeMove(GameState s, Map<String, Object> a) {
        String name = (String) a.getOrDefault("unit_name", "");
        String dest = (String) a.getOrDefault("dest", (String) a.getOrDefault("destination", ""));
        String pid = engine.getPidByName(dest); if (pid == null) pid = dest;
        for (Unit u : s.getFactionState().getUnits())
            if (name.isEmpty() || u.getName().contains(name)) { u.setPosition(pid); return "✅" + u.getName() + "→" + dest; }
        return "❌未找到: " + name;
    }
    private String exeBuild(GameState s, Map<String, Object> a) {
        String bid = (String) a.getOrDefault("build_id", "2.1");
        String loc = (String) a.getOrDefault("location", ""); if (loc.isEmpty()) loc = s.getFactionState().getTerritories().get(0);
        s.getFactionState().getStats().add("industry", 3);
        return "✅建造 " + bid + " @" + loc + " (工业+3)";
    }
    private String exeCampaign(GameState s, Map<String, Object> a) {
        String tgt = (String) a.getOrDefault("target", "");
        String pid = engine.getPidByName(tgt); if (pid == null) pid = tgt;
        var ready = s.getFactionState().getActiveUnits().stream().filter(u -> "ready".equals(u.getStatus())).toList();
        if (ready.isEmpty()) return "❌无可用部队";
        int idx = s.getFactionState().getUnits().indexOf(ready.get(0));
        Map<String, Object> r = campaign.startCampaign(s, pid, List.of(idx), null);
        return (Boolean.TRUE.equals(r.get("ok")) ? "✅" : "❌") + r.get("message");
    }
    private String exeSeize(GameState s, Map<String, Object> a) {
        String prov = (String) a.getOrDefault("province", "");
        String pid = engine.getPidByName(prov);
        String msg = engine.autoClaimArrival(s, null, pid != null ? pid : prov);
        return msg != null ? "✅" + msg : "❌无法占领 " + prov;
    }
    private String exeDiplo(GameState s, Map<String, Object> a, String t) {
        String sub = switch (t) { default -> "3.1"; case "declare_war" -> "3.3"; case "peace" -> "3.4"; case "alliance" -> "3.2"; };
        var r = diplomacy.doDiploAction(s, sub, 0);
        return (Boolean.TRUE.equals(r.get("ok")) ? "✅" : "❌") + r.get("message");
    }
    private String exeTech(GameState s, Map<String, Object> a) {
        String tid = (String) a.getOrDefault("tech_id", "t1_infantry");
        var r = techService.startResearch(s, tid);
        return (Boolean.TRUE.equals(r.get("ok")) ? "✅" : "❌") + r.get("message");
    }
    @SuppressWarnings("unchecked")
    private String exeAnnex(GameState s, Map<String, Object> a) {
        String tgt = (String) a.getOrDefault("target", "");
        var fs = s.getFactionState();
        for (var e : s.getAiFactions().entrySet()) {
            var afs = e.getValue().getFactionState();
            if (afs != null && tgt.contains(afs.getName())) {
                if (afs.getTerritories() != null) for (String tn : afs.getTerritories()) if (!fs.getTerritories().contains(tn)) fs.getTerritories().add(tn);
                if (afs.getUnits() != null) { for (Unit u : afs.getUnits()) { u.setStatus("surrendered"); fs.getUnits().add(u); } }
                s.getDefeatedFactions().add(e.getKey());
                return "✅吞并" + afs.getName();
            }
        }
        return "❌未找到: " + tgt;
    }
    private String exeDisband(GameState s, Map<String, Object> a) {
        String n = (String) a.getOrDefault("unit_name", "");
        for (Unit u : s.getFactionState().getUnits()) if (u.getName().contains(n)) { u.setStatus("annihilated"); return "✅解散" + u.getName(); }
        return "❌未找到: " + n;
    }
    private String exeModify(GameState s, Map<String, Object> a) {
        String n = (String) a.getOrDefault("unit_name", "");
        int atk = ((Number) a.getOrDefault("attack", 0)).intValue(), def = ((Number) a.getOrDefault("defense", 0)).intValue();
        for (Unit u : s.getFactionState().getUnits()) if (u.getName().contains(n)) {
            if (atk != 0) u.setAttack(u.getAttack() + atk); if (def != 0) u.setDefense(u.getDefense() + def);
            return "✅修改" + u.getName();
        }
        return "❌未找到: " + n;
    }
    private String exeReinforce(GameState s, Map<String, Object> a) {
        String n = (String) a.getOrDefault("unit_name", "");
        int str = ((Number) a.getOrDefault("strength", 30)).intValue();
        for (Unit u : s.getFactionState().getUnits()) if (u.getName().contains(n)) {
            u.setStrength(Math.min(u.getMaxStrength(), u.getStrength() + str)); return "✅补给" + u.getName() + "+" + str;
        }
        return "❌未找到: " + n;
    }
}
