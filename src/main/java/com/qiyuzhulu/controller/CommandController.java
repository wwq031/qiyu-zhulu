package com.qiyuzhulu.controller;

import com.qiyuzhulu.model.*;
import com.qiyuzhulu.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.*;

/**
 * 自然语言指令解析/执行 — /api/command/parse, /api/command/execute
 */
@RestController
@RequestMapping("/api")
public class CommandController {

    private final GameEngine engine;
    private final CampaignService campaign;
    private final MilitaryService military;
    private final DiplomacyService diplomacy;
    private final StateController stateCtrl;

    public CommandController(GameEngine engine, CampaignService campaign,
                              MilitaryService military, DiplomacyService diplomacy,
                              StateController stateCtrl) {
        this.engine = engine;
        this.campaign = campaign;
        this.military = military;
        this.diplomacy = diplomacy;
        this.stateCtrl = stateCtrl;
    }

    /** POST /api/command/parse — 解析自然语言指令 */
    @PostMapping("/command/parse")
    public Map<String, Object> parse(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        String text = (String) body.getOrDefault("command", "");
        if (text == null || text.isEmpty()) return Map.of("error", "缺少 command");

        return parseLocal(game, text);
    }

    /** POST /api/command/execute — 执行解析后的指令 */
    @PostMapping("/command/execute")
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");

        String action = (String) body.get("action");
        Map<String, Object> params = (Map<String, Object>) body.getOrDefault("params", Map.of());
        if (action == null) return Map.of("error", "缺少 action");

        Map<String, Object> resp = stateCtrl.buildPanelResponse(game);
        try {
            switch (action) {
                case "move" -> {
                    Integer idx = params.containsKey("unit_index") ? ((Number) params.get("unit_index")).intValue() : 0;
                    String dest = (String) params.getOrDefault("dest", "");
                    String destPid = engine.getPidByName(dest);
                    if (destPid == null) destPid = dest;
                    if (idx < game.getFactionState().getUnits().size()) {
                        game.getFactionState().getUnits().get(idx).setPosition(destPid);
                        resp.put("output", "✅ 部队已移动至 " + dest);
                    }
                }
                case "attack" -> {
                    String target = (String) params.getOrDefault("target", "");
                    String targetPid = engine.getPidByName(target);
                    List<Integer> indices = params.containsKey("unit_indices")
                            ? (List<Integer>) params.get("unit_indices") : List.of(0);
                    if (targetPid != null) {
                        Map<String, Object> r = campaign.startCampaign(game, targetPid,
                                new ArrayList<>(indices), null);
                        resp.put("output", r.getOrDefault("message", "战役已发动"));
                    }
                }
                case "build" -> {
                    String build = (String) params.getOrDefault("build", "");
                    resp.put("output", "⏳ 建设中: " + build);
                }
                case "diplo" -> {
                    String sub = (String) params.getOrDefault("sub", "3.1");
                    Integer tIdx = params.containsKey("target_index")
                            ? ((Number) params.get("target_index")).intValue() : 0;
                    Map<String, Object> r = diplomacy.doDiploAction(game, sub, tIdx);
                    resp.put("output", r.get("message"));
                }
                case "recruit" -> {
                    String type = (String) params.getOrDefault("unit_type", "infantry");
                    String loc = (String) params.getOrDefault("location", "奉天");
                    String locPid = engine.getPidByName(loc);
                    if (locPid != null) {
                        Map<String, Object> r = military.startTraining(game, type, locPid, false);
                        resp.put("output", r.get("message"));
                    }
                }
                default -> resp.put("output", "未知指令类型: " + action);
            }
        } catch (Exception e) {
            resp.put("output", "执行失败: " + e.getMessage());
        }
        return resp;
    }

    // ── 本地解析 ──

    private static final Pattern PT_ATTACK = Pattern.compile("(?:攻击|进攻|攻打|夺取|占领)(.+?)(?:的)?$");
    private static final Pattern PT_MOVE   = Pattern.compile("(?:移动|调动|派遣)(.+?)(?:前往|到|至)(.+)$");
    private static final Pattern PT_BUILD  = Pattern.compile("(?:建设|建造|修建|开发)(.+)$");
    private static final Pattern PT_RECRUIT= Pattern.compile("(?:招募|训练|征召|组建)(.+)$");
    private static final Pattern PT_DIPLO  = Pattern.compile("(?:外交|结盟|条约|宣战|和谈)(.+)$");

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseLocal(GameState game, String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "local");

        // 攻击
        Matcher m = PT_ATTACK.matcher(text);
        if (m.find()) {
            String target = m.group(1).trim();
            result.put("action", "attack");
            result.put("params", Map.of("target", target, "unit_indices", List.of(0)));
            result.put("confidence", 0.85);
            result.put("explanation", "解析为攻击指令 → 目标: " + target);
            return result;
        }
        // 移动
        m = PT_MOVE.matcher(text);
        if (m.find()) {
            result.put("action", "move");
            result.put("params", Map.of("unit_name", m.group(1).trim(), "dest", m.group(2).trim(), "unit_index", 0));
            result.put("confidence", 0.9);
            result.put("explanation", "解析为移动指令 → " + m.group(1).trim() + " → " + m.group(2).trim());
            return result;
        }
        // 建设
        m = PT_BUILD.matcher(text);
        if (m.find()) {
            result.put("action", "build");
            result.put("params", Map.of("build", m.group(1).trim()));
            result.put("confidence", 0.8);
            result.put("explanation", "解析为建设指令 → " + m.group(1).trim());
            return result;
        }
        // 招募
        m = PT_RECRUIT.matcher(text);
        if (m.find()) {
            result.put("action", "recruit");
            result.put("params", Map.of("unit_type", "infantry", "location", "奉天"));
            result.put("confidence", 0.8);
            result.put("explanation", "解析为招募指令 → " + m.group(1).trim());
            return result;
        }
        // 外交
        m = PT_DIPLO.matcher(text);
        if (m.find()) {
            result.put("action", "diplo");
            result.put("params", Map.of("sub", "3.1", "target_index", 0));
            result.put("confidence", 0.7);
            result.put("explanation", "解析为外交指令 → " + m.group(1).trim());
            return result;
        }
        // 模糊匹配
        String lower = text.toLowerCase();
        if (lower.contains("回合") || lower.contains("结束")) {
            result.put("action", "end_turn");
            result.put("params", Map.of());
            result.put("confidence", 0.95);
            result.put("explanation", "结束回合");
        } else {
            result.put("action", "unknown");
            result.put("confidence", 0.1);
            result.put("explanation", "未能识别指令意图，请尝试: 攻击[目标] / 移动[部队]到[地点] / 建设[项目] / 招募[兵种]");
        }
        return result;
    }
}
