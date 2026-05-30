package com.qiyuzhulu.model;

/**
 * AI势力性格配置。替代 Map<String,Map<String,Object>> AI_PERSONALITIES 表格。
 */
public record AiPersonality(
    String name,        // 显示名，如"领地守护型"
    double aggression,   // 侵略性 0.0-1.0
    boolean expand,      // 是否扩张型
    double expandChance, // 每回合进攻概率
    double allianceSeek  // 结盟倾向 0.0-1.0
) {
    /** 攻防比阈值：根据侵略性计算最小进攻战力比 */
    public double minAttackRatio() {
        return aggression > 0.7 ? 0.45 : (aggression > 0.4 ? 0.6 : 0.75);
    }

    /** 默认进攻战术 */
    public String defaultTactic(double ourPower, double defPower) {
        return aggression > 0.6 && ourPower >= defPower * 1.3 ? "assault" : "probe";
    }

    // ── 6种预定义性格 ──

    public static final AiPersonality TERRITORIAL   = new AiPersonality("领地守护型", 0.3,  false, 0.04, 0.6);
    public static final AiPersonality EXPANSIONIST  = new AiPersonality("扩张型",       0.55, true,  0.12, 0.25);
    public static final AiPersonality ADVENTUROUS   = new AiPersonality("冒险型",       0.75, true,  0.18, 0.15);
    public static final AiPersonality DEFENSIVE     = new AiPersonality("防御型",       0.2,  false, 0.02, 0.7);
    public static final AiPersonality DIPLOMATIC    = new AiPersonality("外交型",       0.25, false, 0.03, 0.9);
    public static final AiPersonality OPPORTUNISTIC = new AiPersonality("投机型",       0.5,  true,  0.10, 0.35);

    /** 从AI描述文本推断性格 */
    public static AiPersonality infer(String aiDesc) {
        if (aiDesc == null || aiDesc.isEmpty()) return TERRITORIAL;
        String d = aiDesc.toLowerCase();
        if (containsAny(d, "激进", "冒险", "aggressive", "扩张")) return ADVENTUROUS;
        if (containsAny(d, "保守", "防御", "defensive")) return DEFENSIVE;
        if (containsAny(d, "外交", "diplomatic", "斡旋")) return DIPLOMATIC;
        if (containsAny(d, "投机", "机会", "opportunistic")) return OPPORTUNISTIC;
        if (containsAny(d, "领土", "领地", "territorial")) return TERRITORIAL;
        return EXPANSIONIST;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) if (text.contains(kw)) return true;
        return false;
    }
}
