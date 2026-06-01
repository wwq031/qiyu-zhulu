package com.qiyuzhulu.service;

import com.qiyuzhulu.model.*;
import com.qiyuzhulu.repo.GameDataRepo;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 事件系统 — 随机事件、事件链、事件链抉择、史诗事件。
 * 对应 Python qiyu_actions_events.py（374行）。
 */
@Service
public class EventService {

    private final GameDataRepo gameData;
    private final GameEngine engine;
    private final Random rng = new Random();

    /** 事件链抉择预设（对标 Python outcomes 字典） */
    private static final Map<String, List<Map<String, Object>>> CHAIN_OUTCOMES = Map.of(
            "chain_drought", List.of(
                    Map.of("name","开仓放粮","effect",Map.of("economy",-8,"agriculture",3,"ideology",5),"msg","开仓放粮，民心稍定。但库府见底。"),
                    Map.of("name","听天由命","effect",Map.of("agriculture",-5,"economy",-3,"ideology",-8),"msg","饥民四散，饿殍载道。民心尽失。")),
            "chain_spy", List.of(
                    Map.of("name","公开审判","effect",Map.of("ideology",5,"diplomacy",-5),"msg","公开审判间谍，民心大振但外交关系恶化。"),
                    Map.of("name","秘密交换","effect",Map.of("diplomacy",5,"ideology",-3),"msg","暗中交换俘虏，外交关系改善但舆论不满。")),
            "chain_labor", List.of(
                    Map.of("name","武力镇压","effect",Map.of("industry",-3,"ideology",-8,"military",3),"msg","军队入厂，罢工平息。但工人心寒。"),
                    Map.of("name","谈判妥协","effect",Map.of("economy",-5,"ideology",5,"industry",2),"msg","劳资双方达成协议，工厂复工。")),
            "chain_border", List.of(
                    Map.of("name","全面反击","effect",Map.of("military",5,"economy",-6,"diplomacy",-5),"msg","边境全线出击，虽胜但消耗巨大。"),
                    Map.of("name","外交斡旋","effect",Map.of("diplomacy",5,"military",-3,"economy",-2),"msg","通过谈判化解危机，但军队不满。")),
            "chain_culture", List.of(
                    Map.of("name","支持新学","effect",Map.of("ideology",8,"industry",3,"military",-2),"msg","新学得势，文化焕然一新。"),
                    Map.of("name","维护传统","effect",Map.of("ideology",-3,"diplomacy",5,"military",2),"msg","传统派获胜，社会趋于稳定。"))
    );

    public EventService(GameDataRepo gameData, GameEngine engine) {
        this.gameData = gameData;
        this.engine = engine;
    }

    // ═══════════════════════════════════════════ 随机事件 + 事件链 ═══════════════════════════════════════════

    public List<Map<String, Object>> getEvents() {
        List<Map<String, Object>> events = new ArrayList<>();
        for (Map<String, Object> e : gameData.getSmallEvents()) {
            String id = (String) e.get("id");
            if (id != null && !id.startsWith("chain_")) events.add(e);
        }
        return events;
    }

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

    @SuppressWarnings("unchecked")
    public List<String> trigger(GameState state) {
        List<Map<String, Object>> events = getEvents();
        Map<String, List<Map<String, Object>>> chains = getChains();
        if (events.isEmpty()) return List.of();
        FactionState fs = state.getFactionState();
        List<String> results = new ArrayList<>();

        // 45%概率触发随机事件
        if (rng.nextDouble() < 0.45) {
            Map<String, Object> evt = events.get(rng.nextInt(events.size()));
            applyEffects(fs.getStats(), (Map<String, Object>) evt.get("effect"));
            String tag = isPositive(evt) ? "🍀" : "⚠";
            results.add(tag + " " + evt.get("name") + "：" + evt.getOrDefault("msg", ""));
        }

        // 3%概率开始新事件链
        List<Map<String, Object>> active = state.getActiveChains();
        if (active.isEmpty() && !chains.isEmpty() && rng.nextDouble() < 0.03) {
            String chainKey = new ArrayList<>(chains.keySet()).get(rng.nextInt(chains.size()));
            List<Map<String, Object>> steps = chains.get(chainKey);
            if (steps != null && !steps.isEmpty()) {
                Map<String, Object> step1 = steps.get(0);
                applyEffects(fs.getStats(), (Map<String, Object>) step1.get("effect"));
                Map<String, Object> ci = new LinkedHashMap<>();
                ci.put("key", chainKey); ci.put("step", 1);
                ci.put("total_steps", steps.size()); ci.put("start_turn", state.getTurn());
                active.add(ci);
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
            int expectedTurn = (step + 1) * (rng.nextInt(3) + 2);
            if (state.getTurn() - startTurn >= expectedTurn) {
                String chainKey = (String) ci.get("key");
                List<Map<String, Object>> chainSteps = chains.get(chainKey);
                if (chainSteps != null && step < chainSteps.size()) {
                    Map<String, Object> nextStep = chainSteps.get(step);
                    ci.put("step", step + 1);
                    if (nextStep.get("effect") != null && !((Map<?,?>)nextStep.get("effect")).isEmpty()) {
                        applyEffects(fs.getStats(), (Map<String, Object>) nextStep.get("effect"));
                        results.add("🔗 " + nextStep.get("name") + "：" + nextStep.getOrDefault("msg", nextStep.getOrDefault("desc", "")));
                    } else {
                        ci.put("awaiting_choice", true);
                        results.add("🔗 ⚡ 抉择：" + nextStep.get("name") + "（请前往决议菜单处理）");
                    }
                }
            }
        }
        // 清理已完成链
        active.removeIf(ci -> ((Number) ci.get("step")).intValue() >= ((Number) ci.get("total_steps")).intValue()
                && !Boolean.TRUE.equals(ci.get("awaiting_choice")));
        return results;
    }

    // ═══════════════════════════════════════════ 事件链抉择 ═══════════════════════════════════════════

    /** 列出当前等待抉择的事件链节点 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listChainChoices(GameState state) {
        List<Map<String, Object>> choices = new ArrayList<>();
        Map<String, List<Map<String, Object>>> chains = getChains();
        for (Map<String, Object> ci : state.getActiveChains()) {
            if (!Boolean.TRUE.equals(ci.get("awaiting_choice"))) continue;
            String key = (String) ci.get("key");
            List<Map<String, Object>> steps = chains.get(key);
            if (steps == null) continue;
            int step = ((Number) ci.get("step")).intValue();
            if (step <= steps.size()) {
                Map<String, Object> stepData = steps.get(step - 1);
                Map<String, Object> choice = new LinkedHashMap<>();
                choice.put("chain_key", key);
                choice.put("step", step);
                choice.put("name", stepData.getOrDefault("name", "未知抉择"));
                choice.put("desc", stepData.getOrDefault("desc", ""));
                choice.put("id", stepData.getOrDefault("id", ""));
                // 注入预设选项
                List<Map<String, Object>> outcomes = CHAIN_OUTCOMES.get(key);
                if (outcomes != null) {
                    choice.put("choices", outcomes.stream().map(o -> Map.of(
                            "name", o.get("name"), "msg", o.get("msg"))).toList());
                }
                choices.add(choice);
            }
        }
        return choices;
    }

    /** 处理事件链抉择，应用后果 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveChainChoice(GameState state, String chainKey, int optionIndex) {
        List<Map<String, Object>> outcomes = CHAIN_OUTCOMES.getOrDefault(chainKey, List.of(
                Map.of("name","选项A","effect",Map.of(),"msg","做出了选择。"),
                Map.of("name","选项B","effect",Map.of(),"msg","做出了选择。")));
        if (optionIndex < 0 || optionIndex >= outcomes.size())
            return Map.of("ok", false, "message", "无效选项");

        Map<String, Object> result = outcomes.get(optionIndex);
        FactionState fs = state.getFactionState();
        applyEffects(fs.getStats(), (Map<String, Object>) result.get("effect"));

        // 标记链完成
        for (Map<String, Object> ci : state.getActiveChains()) {
            if (chainKey.equals(ci.get("key"))) {
                ci.put("awaiting_choice", false);
                ci.put("step", ci.get("total_steps"));
            }
        }
        return Map.of("ok", true, "message", result.get("name") + "：" + result.get("msg"));
    }

    // ═══════════════════════════════════════════ 史诗事件 ═══════════════════════════════════════════

    /** 检查并触发史诗事件（对标 Python check_epic_events） */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> checkEpic(GameState state) {
        Map<String, Object> raw = gameData.getRaw();
        Map<String, Object> evData = (Map<String, Object>) raw.get("events");
        if (evData == null) {
            // Try loading from events.json if not in game_data
            // events are in game_data.json's small_events, generic_epic are separate
        }

        // Load epic events from events.json file
        List<Map<String, Object>> epics = loadEpicEvents();
        if (epics.isEmpty()) return List.of();

        FactionState fs = state.getFactionState();
        Stats s = fs.getStats();
        int turn = state.getTurn();
        String fid = state.getPlayerFactionId();
        FactionDefinition pf = engine.getPlayerFaction(state);
        String playerRegion = pf != null ? pf.getRegion() : "";
        List<Map<String, Object>> triggered = new ArrayList<>();

        for (Map<String, Object> evt : epics) {
            String eid = (String) evt.get("id");
            // 冷却检查
            int cooldown = ((Number) evt.getOrDefault("cooldown_turns", 0)).intValue();
            boolean alreadyTriggered = state.getTriggeredEpicEvents().stream().anyMatch(t -> t.contains(eid));
            if (alreadyTriggered && cooldown == 0) continue;

            String triggerType = (String) evt.get("trigger_type");
            Map<String, Object> conditions = (Map<String, Object>) evt.getOrDefault("trigger_conditions", Map.of());
            Map<String, String> context = new LinkedHashMap<>();
            boolean shouldTrigger = false;

            switch (triggerType) {
                case "faction_eliminated" -> {
                    List<Map<String, Object>> defeats = state.getDefeatEvents();
                    if (defeats != null && !defeats.isEmpty()) {
                        Map<String, Object> d = defeats.get(defeats.size() - 1);
                        context.put("eliminated_faction", (String) d.getOrDefault("eliminated_faction", "某势力"));
                        context.put("eliminator_faction", (String) d.getOrDefault("eliminator_faction", "某势力"));
                        shouldTrigger = true;
                    }
                }
                case "major_faction_eliminated" -> {
                    int minMil = ((Number) conditions.getOrDefault("min_enemy_military", 50)).intValue();
                    List<Map<String, Object>> defeats = state.getDefeatEvents();
                    if (defeats != null) {
                        for (Map<String, Object> d : defeats) {
                            String efid = (String) d.get("eliminated_fid");
                            if (efid != null) {
                                FactionDefinition ef = engine.getFaction(efid).orElse(null);
                                int enemyMil = ef != null ? ef.getStats().getMilitary() : 30;
                                if (enemyMil >= minMil) {
                                    context.put("eliminated_faction", (String) d.getOrDefault("eliminated_faction", "某势力"));
                                    context.put("eliminator_faction", (String) d.getOrDefault("eliminator_faction", "某势力"));
                                    shouldTrigger = true;
                                    break;
                                }
                            }
                        }
                    }
                }
                case "region_unified" -> {
                    if (playerRegion != null && !playerRegion.isEmpty() && engine.isRegionUnified(state, fid)) {
                        shouldTrigger = true;
                    }
                }
                case "foreign_intervention" -> {
                    if (rng.nextDouble() < 0.12) {
                        context.put("foreign_power", GameEngine.FOREIGN_POWERS.getOrDefault(playerRegion, "列强"));
                        shouldTrigger = true;
                    }
                }
                case "decisive_victory" -> {
                    List<CampaignResult> crs = state.getCampaignResultsThisTurn();
                    if (crs != null) {
                        for (CampaignResult cr : crs) {
                            if ("annihilate".equals(cr.getOutcome()) || "decisive_win".equals(cr.getOutcome())) {
                                context.put("battle_location", cr.getProvinceName());
                                context.put("defeated_enemy", cr.getDefenderName());
                                shouldTrigger = true;
                                break;
                            }
                        }
                    }
                }
                case "revolutionary_wave" -> {
                    int minIdeology = ((Number) conditions.getOrDefault("min_ideology", 80)).intValue();
                    if (s.getIdeology() >= minIdeology) {
                        List<String> neighbors = GameEngine.REGION_ADJACENCY.getOrDefault(playerRegion, List.of("邻区"));
                        context.put("spread_region", GameEngine.REGION_NAMES.getOrDefault(neighbors.get(rng.nextInt(neighbors.size())), "邻区"));
                        shouldTrigger = true;
                    }
                }
                case "tech_breakthrough" -> {
                    int currentTech = fs.getMilitaryTech();
                    if (currentTech >= 3 && state.getTriggeredEpicEvents().stream().noneMatch(t -> t.contains(eid + "_lv" + currentTech))) {
                        Map<Integer, String> names = Map.of(3,"仿制步枪",5,"轻机枪",7,"重型火炮",9,"初级坦克");
                        Map<Integer, String> tiers = Map.of(3,"亚洲二流",5,"亚洲准一流",7,"亚洲一流",9,"世界准一流");
                        context.put("tech_name", names.getOrDefault(currentTech, "新式武器"));
                        context.put("tech_tier", tiers.getOrDefault(currentTech, "先进水平"));
                        shouldTrigger = true;
                    }
                }
                case "diplomatic_coup" -> {
                    int minAlliances = ((Number) conditions.getOrDefault("min_alliances", 3)).intValue();
                    long allies = state.getDiplomaticRelations().values().stream()
                            .filter(r -> "alliance".equals(r.getPact())).count();
                    if (allies >= minAlliances) {
                        context.put("alliance_count", String.valueOf(allies));
                        context.put("alliance_partners", String.valueOf(allies) + "个势力");
                        shouldTrigger = true;
                    }
                }
                case "great_power_shift" -> {
                    int threshold = ((Number) conditions.getOrDefault("total_stat_threshold", 300)).intValue();
                    int total = s.getMilitary() + s.getEconomy() + s.getIndustry() + s.getAgriculture()
                            + s.getIdeology() + s.getDiplomacy() + s.getNavalPower();
                    if (total >= threshold) {
                        context.put("total_power", String.valueOf(total));
                        shouldTrigger = true;
                    }
                }
            }

            if (shouldTrigger) {
                context.putIfAbsent("player_faction", fs.getName());
                context.putIfAbsent("date", state.getGameDate());
                context.putIfAbsent("region_name", GameEngine.REGION_NAMES.getOrDefault(playerRegion, ""));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", eid);
                result.put("name", evt.get("name"));
                result.put("quote", fillTemplate((String) evt.getOrDefault("quote", ""), context));
                result.put("scene", fillTemplate((String) evt.getOrDefault("scene", ""), context));
                result.put("climax", fillTemplate((String) evt.getOrDefault("climax", ""), context));
                result.put("finale", fillTemplate((String) evt.getOrDefault("finale", ""), context));
                result.put("effects", evt.getOrDefault("effects", Map.of()));
                applyEffects(s, (Map<String, Object>) evt.getOrDefault("effects", Map.of()));
                state.getTriggeredEpicEvents().add(eid + "_" + turn);
                triggered.add(result);
            }
        }
        return triggered;
    }

    // ═══════════════════════════════════════════ 工具方法 ═══════════════════════════════════════════

    /** 加载 events.json 中的 generic_epic_events */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadEpicEvents() {
        try {
            var resource = new org.springframework.core.io.ClassPathResource("data/events.json");
            Map<String, Object> data = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(resource.getInputStream(), Map.class);
            return (List<Map<String, Object>>) data.getOrDefault("generic_epic_events", List.of());
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private void applyEffects(Stats stats, Map<String, Object> effects) {
        if (effects == null) return;
        for (var entry : effects.entrySet())
            stats.add(entry.getKey(), ((Number) entry.getValue()).intValue());
    }

    private boolean isPositive(Map<String, Object> evt) {
        Map<String, Object> effect = (Map<String, Object>) evt.get("effect");
        if (effect == null) return true;
        return effect.values().stream().anyMatch(v -> ((Number) v).intValue() > 0);
    }

    private String fillTemplate(String template, Map<String, String> ctx) {
        for (var e : ctx.entrySet())
            template = template.replace("{" + e.getKey() + "}", e.getValue() != null ? e.getValue() : "");
        return template;
    }
}
