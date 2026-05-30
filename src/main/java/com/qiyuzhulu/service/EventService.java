package com.qiyuzhulu.service;

import com.qiyuzhulu.model.*;
import com.qiyuzhulu.repo.GameDataRepo;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 事件系统 — 随机事件、事件链、史诗事件。
 * 对应 Python qiyu_actions_events.py。
 */
@Service
public class EventService {

    private final GameDataRepo gameData;
    private final Random rng = new Random();

    public EventService(GameDataRepo gameData) {
        this.gameData = gameData;
    }

    /** 获取所有小型随机事件（排除链步骤事件） */
    public List<Map<String, Object>> getEvents() {
        List<Map<String, Object>> events = new ArrayList<>();
        List<Map<String, Object>> all = gameData.getSmallEvents();
        for (Map<String, Object> e : all) {
            String id = (String) e.get("id");
            if (id != null && !id.startsWith("chain_")) {
                events.add(e);
            }
        }
        return events;
    }

    /** 获取事件链映射（chain_key → 步骤列表） */
    public Map<String, List<Map<String, Object>>> getChains() {
        Map<String, List<Map<String, Object>>> chains = new LinkedHashMap<>();
        for (Map<String, Object> e : gameData.getSmallEvents()) {
            String id = (String) e.get("id");
            if (id != null && id.startsWith("chain_")) {
                String prefix = id.substring(0, id.lastIndexOf('_'));
                chains.computeIfAbsent(prefix, k -> new ArrayList<>()).add(e);
            }
        }
        chains.forEach((k, v) -> v.sort(Comparator.comparing(e -> (String) e.get("id"))));
        return chains;
    }

    /** 每回合触发随机事件。返回事件描述列表。 */
    @SuppressWarnings("unchecked")
    public List<String> trigger(GameState state) {
        List<Map<String, Object>> events = getEvents();
        Map<String, List<Map<String, Object>>> chains = getChains();
        if (events.isEmpty()) return List.of();

        FactionState fs = state.getFactionState();
        List<String> results = new ArrayList<>();

        // 随机事件：30%概率触发
        if (rng.nextDouble() < 0.30) {
            Map<String, Object> evt = events.get(rng.nextInt(events.size()));
            applyEffects(fs.getStats(), (Map<String, Object>) evt.get("effect"));
            String tag = isPositive(evt) ? "🍀" : "⚠";
            results.add(tag + " " + evt.get("name") + "：" + evt.getOrDefault("msg", ""));
        }

        // 事件链触发：3%概率开始新链
        List<Map<String, Object>> active = state.getActiveChains();
        if (active.isEmpty() && !chains.isEmpty() && rng.nextDouble() < 0.03) {
            String chainKey = new ArrayList<>(chains.keySet()).get(rng.nextInt(chains.size()));
            List<Map<String, Object>> steps = chains.get(chainKey);
            if (steps != null && !steps.isEmpty()) {
                Map<String, Object> step1 = steps.get(0);
                applyEffects(fs.getStats(), (Map<String, Object>) step1.get("effect"));
                Map<String, Object> chainInfo = new LinkedHashMap<>();
                chainInfo.put("key", chainKey);
                chainInfo.put("step", 1);
                chainInfo.put("total_steps", steps.size());
                chainInfo.put("start_turn", state.getTurn());
                active.add(chainInfo);
                results.add("🔗 事件链·" + step1.get("name") + "：" + step1.getOrDefault("msg", step1.getOrDefault("desc", "")));
            }
        }

        // 推进已有事件链
        for (int i = active.size() - 1; i >= 0; i--) {
            Map<String, Object> ci = active.get(i);
            int step = ((Number) ci.get("step")).intValue();
            int total = ((Number) ci.get("total_steps")).intValue();
            if (step >= total) continue;

            int startTurn = ((Number) ci.get("start_turn")).intValue();
            int expectedStep = step + 1;
            int expectedTurn = expectedStep * (rng.nextInt(3) + 2);
            if (state.getTurn() - startTurn >= expectedTurn) {
                String chainKey = (String) ci.get("key");
                List<Map<String, Object>> chainSteps = chains.get(chainKey);
                if (chainSteps != null && step < chainSteps.size()) {
                    Map<String, Object> nextStep = chainSteps.get(step);
                    ci.put("step", step + 1);
                    if (nextStep.get("effect") != null) {
                        applyEffects(fs.getStats(), (Map<String, Object>) nextStep.get("effect"));
                        results.add("🔗 " + nextStep.get("name") + "：" + nextStep.getOrDefault("msg", nextStep.getOrDefault("desc", "")));
                    } else {
                        results.add("🔗 ⚡ 抉择：" + nextStep.get("name") + "：" + nextStep.getOrDefault("desc", ""));
                    }
                }
            }
        }

        return results;
    }

    /** 检查史诗事件触发器 */
    public List<Map<String, Object>> checkEpic(GameState state) {
        // 简化版：基于势力统计阈值触发
        List<Map<String, Object>> triggered = new ArrayList<>();
        FactionState fs = state.getFactionState();
        Stats s = fs.getStats();

        // 势力覆灭检测
        if (!state.getDefeatedFactions().isEmpty()) {
            int newDefeats = state.getDefeatedFactions().size() -
                    state.getTriggeredEpicEvents().size();
            if (newDefeats > 0 && rng.nextDouble() < 0.3) {
                Map<String, Object> epic = new LinkedHashMap<>();
                epic.put("name", "势力覆灭");
                epic.put("quote", "一将功成万骨枯。");
                epic.put("scene", state.getDefeatedFactions().size() + "个势力已从乱世中消失。");
                epic.put("effects", Map.of("military", 3, "ideology", 2));
                triggered.add(epic);
                state.getTriggeredEpicEvents().add("faction_eliminated_" + state.getTurn());
            }
        }

        // 区域统一检测
        if (s.getMilitary() > 60 && fs.getTerritories().size() > 20 && rng.nextDouble() < 0.15) {
            Map<String, Object> epic = new LinkedHashMap<>();
            epic.put("name", "区域霸主崛起");
            epic.put("quote", "卧榻之侧，岂容他人鼾睡。");
            epic.put("scene", fs.getName() + "已控制" + fs.getTerritories().size() + "省，区域统一指日可待。");
            epic.put("effects", Map.of("diplomacy", 5, "ideology", 3));
            triggered.add(epic);
        }

        return triggered;
    }

    @SuppressWarnings("unchecked")
    private void applyEffects(Stats stats, Map<String, Object> effects) {
        if (effects == null) return;
        for (var entry : effects.entrySet()) {
            String k = entry.getKey();
            int v = ((Number) entry.getValue()).intValue();
            stats.add(k, v);
        }
    }

    private boolean isPositive(Map<String, Object> evt) {
        @SuppressWarnings("unchecked")
        Map<String, Object> effect = (Map<String, Object>) evt.get("effect");
        if (effect == null) return true;
        return effect.values().stream().anyMatch(v -> ((Number) v).intValue() > 0);
    }
}
