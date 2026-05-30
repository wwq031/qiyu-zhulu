package com.qiyuzhulu.service;

import com.qiyuzhulu.model.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 回合面板渲染。对应 Python 的 qiyu_ui.py render_round_panel。
 * 输出 ANSI 转义码文本（前端 colorizePanel 处理着色）。
 */
@Service
public class PanelRenderer {

    private final GameEngine engine;

    // ANSI 颜色常量
    private static final String R = "\033[0m";
    private static final String GR = "\033[90m";
    private static final String LR = "\033[91m"; private static final String LG = "\033[92m";
    private static final String LY = "\033[93m"; private static final String LB = "\033[94m";
    private static final String LP = "\033[95m"; private static final String LC = "\033[96m";
    private static final String LW = "\033[97m";
    private static final String Y = "\033[33m"; private static final String G = "\033[32m";
    private static final String R_ = "\033[31m"; private static final String B = "\033[34m";
    private static final String C = "\033[36m"; private static final String P = "\033[35m";
    private static final String W = "\033[37m";

    private static final Map<String, String> STAT_COLORS = Map.of(
            "industry", W, "agriculture", G, "military", R_,
            "economy", Y, "ideology", P, "diplomacy", B, "naval_power", C);

    public PanelRenderer(GameEngine engine) { this.engine = engine; }

    /** 渲染回合面板，返回 ANSI 字符串 */
    public String render(GameState state) {
        StringBuilder sb = new StringBuilder();

        FactionState fs = state.getFactionState();
        Stats s = fs.getStats();
        String fid = state.getPlayerFactionId();
        FactionDefinition faction = engine.getFaction(fid).orElse(null);
        if (faction == null) return "ERROR: Faction not found";

        List<String> evo = faction.getEvolution();
        String phaseName = GameEngine.PHASE_NAMES.getOrDefault(state.getPhase(), "未知");

        // 标题
        sb.append(GR).append("─".repeat(55)).append(R).append("\n");
        sb.append(" ").append(LC).append("七域逐鹿 · ").append(phaseName).append(R)
          .append("    ").append(GR).append(state.getGameDate()).append(" · 第 ")
          .append(state.getTurn()).append(" 回合").append(R).append("\n");
        sb.append(GR).append("─".repeat(55)).append(R).append("\n\n");

        // 势力名 + 进化路径
        Leader leader = faction.getLeader();
        String ldLine = "";
        if (leader != null) {
            ldLine = " " + LW + "@" + leader.getName() + R + " " + GR + leader.getTitle() + R;
        }
        sb.append(" ").append(Y).append(fs.getName()).append(R)
          .append("  ").append(GR).append("→").append(R).append("  ")
          .append(Y).append(evo.size() > 1 ? evo.get(1) : "?").append(R)
          .append("  ").append(GR).append("→").append(R).append("  ")
          .append(LY).append("★").append(evo.size() > 2 ? evo.get(2) : "?").append("★").append(R)
          .append(ldLine).append("\n");

        // 领土
        List<String> territories = fs.getTerritories();
        String terrStr = territories != null ? territories.stream().limit(4).collect(Collectors.joining(", ")) : "";
        sb.append(" ").append(G).append("📍 ").append(terrStr).append(R).append("\n");

        // 部队
        List<String> forces = fs.getForces();
        String forceStr = forces != null ? forces.stream().limit(5).collect(Collectors.joining(", ")) : "";
        sb.append(" ").append(R_).append("🗡 ").append(forceStr).append(R).append("\n\n");

        // 六围
        sb.append(renderStats(s));

        // 军事科技 + 兵力
        int tech = fs.getMilitaryTech();
        Map<String, Integer> army = fs.getArmy();
        StringBuilder armyParts = new StringBuilder();
        if (army != null) {
            for (String uk : List.of("infantry", "cavalry", "artillery", "engineer", "naval")) {
                int cnt = army.getOrDefault(uk, 0);
                if (cnt > 0) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ut = (Map<String, Object>) GameEngine.UNIT_TYPES.get(uk);
                    if (ut != null) armyParts.append(ut.get("icon")).append(ut.get("name")).append("×").append(cnt).append("  ");
                }
            }
        }
        String armyStr = armyParts.length() > 0 ? armyParts.toString().trim() : "暂无编制";

        int ap = state.getActionPoints();
        int apMax = state.getApMax();
        String apBar = LC + "◆".repeat(Math.max(0, ap)) + GR + "◇".repeat(Math.max(0, apMax - ap)) + R;

        sb.append(" ").append(GR).append("军事科技 Lv.").append(tech).append(R)
          .append("  |  ").append(GR).append("兵力").append(R).append(" ").append(armyStr).append("\n");

        int maintenance = engine.calcTotalMaintenance(fs);
        String maintWarn = maintenance > 0 && maintenance > fs.getTreasury() / 2 ? LR : GR;
        sb.append(" ").append(GR).append("国库：").append(R).append(LW).append(fs.getTreasury()).append(R)
          .append("  |  ").append(GR).append("民心：").append(R).append(LW).append(fs.getPopulationSupport()).append("%").append(R)
          .append("  |  ").append(GR).append("建设：").append(R).append(state.getConstructionQueue().size()).append("项")
          .append("  |  ").append(maintWarn).append("维持费：").append(maintenance).append(R).append("\n");

        sb.append(" ").append(GR).append("AP：").append(R).append(apBar).append("\n");

        // 进行中的战役
        List<Campaign> activeCamps = state.getActiveCampaigns().stream()
                .filter(c -> "ongoing".equals(c.getStatus())).toList();
        if (!activeCamps.isEmpty()) {
            sb.append(" ").append(LR).append("⚔ 进行中战役：").append(R).append("\n");
            for (Campaign camp : activeCamps.stream().limit(3).toList()) {
                sb.append("   ").append(Y).append(camp.getProvinceName()).append(R)
                  .append(" 第").append(camp.getRound()).append("轮\n");
            }
        }

        sb.append("\n").append(GR).append("─".repeat(55)).append(R).append("\n");

        // 行动菜单
        sb.append(" ").append(GR).append("─".repeat(45)).append(R).append("\n");
        sb.append(" ").append(LW).append("[1]").append(R).append(" ⚔军事  ")
          .append(LW).append("[2]").append(R).append(" 🏗内政  ")
          .append(LW).append("[3]").append(R).append(" 🌐外交\n");
        sb.append(" ").append(LW).append("[4]").append(R).append(" 🔍情报  ")
          .append(LW).append("[5]").append(R).append(" 🗺区域攻略  ")
          .append(LW).append("[6]").append(R).append(" 💾存档\n");
        sb.append(" ").append(LW).append("[7]").append(R).append(" 📜国策  ")
          .append(LW).append("[8]").append(R).append(" 🔬科技  ")
          .append(LW).append("[*]").append(R).append(" ✧自由行动\n");
        if (state.getPhase() >= 4) sb.append(" ").append(LW).append("[9]").append(R).append(" 🌍大国博弈\n");
        if (state.getPhase() >= 5) sb.append(" ").append(LW).append("[0]").append(R).append(" ⚡终局决战\n");

        return sb.toString();
    }

    /** 渲染六围 */
    public String renderStats(Stats stats) {
        StringBuilder sb = new StringBuilder();
        List<String> keys = List.of("industry", "agriculture", "military", "economy", "ideology", "diplomacy");
        for (String key : keys) {
            int v = stats.get(key);
            String emoji = GameEngine.STAT_EMOJI.getOrDefault(key, "?");
            String name = GameEngine.STAT_NAMES.getOrDefault(key, key);
            String bar = renderBar(v, key);
            sb.append("  ").append(emoji).append(name).append(" ").append(LW).append(String.format("%3d", v)).append(R)
              .append("  ").append(bar).append("\n");
        }
        if (stats.getNavalPower() > 0) {
            int v = stats.getNavalPower();
            String bar = renderBar(v, "naval_power");
            sb.append("  ⚓海军 ").append(LW).append(String.format("%3d", v)).append(R).append("  ").append(bar).append("\n");
        }
        return sb.toString();
    }

    /** 渲染单个属性进度条 */
    private String renderBar(int value, String statKey) {
        int blocks = Math.min(20, value / 5);
        String color = STAT_COLORS.getOrDefault(statKey, W);
        return color + "█".repeat(blocks) + GR + "░".repeat(20 - blocks) + R;
    }

    /** 格式化效果map为文本 */
    public String formatEffects(Map<String, Integer> effects) {
        if (effects == null || effects.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        Map<String, String> unitNames = Map.of(
                "infantry","步兵","cavalry","骑兵","artillery","炮兵","engineer","工兵","naval","海军");
        for (var entry : effects.entrySet()) {
            String k = entry.getKey();
            int v = entry.getValue();
            if ("naval_power".equals(k)) parts.add("⚓海军" + formatSigned(v));
            else if ("military_tech".equals(k)) parts.add("🔬军事科技" + formatSigned(v));
            else if (unitNames.containsKey(k)) parts.add("+" + v + unitNames.get(k));
            else if (GameEngine.STAT_EMOJI.containsKey(k))
                parts.add(GameEngine.STAT_EMOJI.get(k) + GameEngine.STAT_NAMES.getOrDefault(k, k) + formatSigned(v));
            else parts.add(k + formatSigned(v));
        }
        return String.join(" ", parts);
    }

    private String formatSigned(int v) { return v >= 0 ? "+" + v : String.valueOf(v); }
}
