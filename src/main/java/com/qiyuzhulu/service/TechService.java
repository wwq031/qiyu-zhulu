package com.qiyuzhulu.service;

import com.qiyuzhulu.model.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 科技研发系统。对应 Python qiyu_actions_shared.py 的科技树部分。
 * 注：科技树内容待重做，此处提供完整框架。
 */
@Service
public class TechService {

    private final GameEngine engine;

    public TechService(GameEngine engine) {
        this.engine = engine;
    }

    /**
     * 获取当前可研发的科技列表。按tier组织，检查前置条件。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAvailableTechs(GameState state) {
        Map<String, Object> techTree = engine.getGameData().getTechTree();
        if (techTree == null || !techTree.containsKey("tiers"))
            return Map.of("available", List.of(), "branches", Map.of(), "researched", List.of());

        Map<String, Object> tiers = (Map<String, Object>) techTree.get("tiers");
        Set<String> researched = new HashSet<>(state.getResearchedTechs());
        int phase = state.getPhase();

        // 研发中科技
        Set<String> inProgress = state.getConstructionQueue().stream()
                .filter(ci -> ci.getTechId() != null)
                .map(ConstructionItem::getTechId)
                .collect(Collectors.toSet());

        List<Map<String, Object>> available = new ArrayList<>();
        for (String tierKey : List.of("1", "2", "3")) {
            Map<String, Object> tier = (Map<String, Object>) tiers.get(tierKey);
            if (tier == null) continue;
            int reqPhase = ((Number) tier.getOrDefault("require_phase", 3)).intValue();
            if (phase < reqPhase) continue;

            Map<String, Object> techs = (Map<String, Object>) tier.get("techs");
            if (techs == null) continue;

            for (var entry : techs.entrySet()) {
                String tid = entry.getKey();
                Map<String, Object> tdata = (Map<String, Object>) entry.getValue();
                if (researched.contains(tid)) continue;

                List<String> requires = (List<String>) tdata.getOrDefault("requires", List.of());
                boolean prereqsMet = researched.containsAll(requires);
                boolean alreadyQueued = inProgress.contains(tid);

                Map<String, Object> info = new LinkedHashMap<>();
                info.put("id", tid);
                info.put("name", tdata.get("name"));
                info.put("branch", tdata.getOrDefault("branch", ""));
                info.put("desc", tdata.getOrDefault("desc", ""));
                info.put("tier", Integer.parseInt(tierKey));
                info.put("tier_name", tier.getOrDefault("name", ""));
                info.put("cost", ((Number) tier.getOrDefault("cost_economy", 20)).intValue());
                info.put("turns", ((Number) tier.getOrDefault("turns", 4)).intValue());
                info.put("effects", tdata.getOrDefault("effect", Map.of()));
                info.put("requires", requires);
                info.put("prereqs_met", prereqsMet);
                info.put("already_queued", alreadyQueued);
                available.add(info);
            }
        }

        return Map.of(
                "available", available,
                "branches", techTree.getOrDefault("branches", Map.of()),
                "researched", state.getResearchedTechs(),
                "phase", state.getPhase()
        );
    }

    /**
     * 开始研发指定科技。将研发项加入建设队列。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> startResearch(GameState state, String techId) {
        Map<String, Object> all = getAvailableTechs(state);
        List<Map<String, Object>> available = (List<Map<String, Object>>) all.get("available");
        Map<String, Object> matched = null;
        for (Map<String, Object> t : available) {
            if (techId.equals(t.get("id"))) { matched = t; break; }
        }
        if (matched == null) return Map.of("ok", false, "message", "科技不存在或已研发");

        if (Boolean.TRUE.equals(matched.get("already_queued")))
            return Map.of("ok", false, "message", "该科技已在研发队列中");

        if (!Boolean.TRUE.equals(matched.get("prereqs_met")))
            return Map.of("ok", false, "message", "前置科技未完成");

        FactionState fs = state.getFactionState();
        int cost = ((Number) matched.get("cost")).intValue();
        if (fs.getTreasury() < cost)
            return Map.of("ok", false, "message", "国库不足！需要 " + cost + "💰");

        if (state.getActionPoints() < 1)
            return Map.of("ok", false, "message", "行动点不足");

        fs.setTreasury(fs.getTreasury() - cost);
        state.setActionPoints(state.getActionPoints() - 1);

        ConstructionItem item = new ConstructionItem();
        item.setName("研发：" + matched.get("name"));
        item.setTurnsLeft(((Number) matched.get("turns")).intValue());
        item.setEffect((Map<String, Integer>) (Object) matched.get("effects"));
        item.setTechId(techId);
        item.setTechName((String) matched.get("name"));

        if (state.getConstructionQueue() == null)
            state.setConstructionQueue(new ArrayList<>());
        state.getConstructionQueue().add(item);

        if (state.getTechEventsThisTurn() == null)
            state.setTechEventsThisTurn(new ArrayList<>());
        state.getTechEventsThisTurn().add(
                "🔬 " + fs.getName() + " 启动了「" + matched.get("name") + "」研发项目。");

        return Map.of("ok", true, "message",
                "🔬 启动研发：" + matched.get("name") + " 消耗" + cost + "💰 预计" + matched.get("turns") + "回合");
    }
}
