package com.qiyuzhulu.service;

import com.qiyuzhulu.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI 供应商抽象层 — 支持 local/DeepSeek/OpenAI/Claude。
 * 会话级配置，不写文件。
 * 注：local 模式的丰富模板在 SandboxService.localAdjudicate()，
 *      此处仅做最后兜底，正常流程不经过这里。
 */
@Service
public class AiProviderService {

    private String provider = "local";
    private String apiKey = null;
    private String model = null;
    private String baseUrl = null;

    private static final Map<String, String> DEFAULT_MODELS = Map.of(
            "deepseek", "deepseek-chat",
            "openai", "gpt-4o",
            "anthropic", "claude-sonnet-4-6"
    );

    private static final Map<String, String> API_BASES = Map.of(
            "deepseek", "https://api.deepseek.com",
            "openai", "https://api.openai.com/v1",
            "anthropic", "https://api.anthropic.com"
    );

    public AiProviderService() {}

    /** 获取当前provider */
    public String getProvider() { return provider; }

    /** 设置会话级配置 */
    public synchronized Map<String, Object> setConfig(Map<String, Object> cfg) {
        if (cfg.containsKey("provider")) provider = (String) cfg.get("provider");
        if (cfg.containsKey("api_key")) {
            String key = (String) cfg.get("api_key");
            apiKey = (key != null && !key.isEmpty()) ? key : null;
        }
        if (cfg.containsKey("model")) model = (String) cfg.get("model");
        if (cfg.containsKey("base_url")) baseUrl = (String) cfg.get("base_url");
        return getConfig();
    }

    /** 获取当前配置 */
    public Map<String, Object> getConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("provider", provider);
        cfg.put("api_key_set", apiKey != null && !apiKey.isEmpty());
        cfg.put("model", model != null ? model : DEFAULT_MODELS.getOrDefault(provider, ""));
        cfg.put("base_url", baseUrl != null ? baseUrl : "");
        Map<String, Object> providers = new LinkedHashMap<>();
        for (String p : List.of("local", "deepseek", "openai", "anthropic")) {
            providers.put(p, Map.of(
                    "available", "local".equals(p) || apiKey != null,
                    "name", switch (p) {
                        case "local" -> "本地模板";
                        case "deepseek" -> "DeepSeek";
                        case "openai" -> "OpenAI";
                        case "anthropic" -> "Claude";
                        default -> p;
                    }
            ));
        }
        cfg.put("providers", providers);
        return cfg;
    }

    /** 测试连接 */
    public Map<String, Object> checkConnection() {
        if ("local".equals(provider)) {
            return Map.of("available", true, "message", "本地模板模式，无需连接");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            return Map.of("available", false, "message", "未设置API密钥");
        }
        // 尝试实际连接测试
        try {
            var result = testProviderConnection(provider);
            if (result != null) return result;
        } catch (Exception ignored) {}
        return Map.of("available", true, "message", provider + " 配置有效（密钥已设置）");
    }

    /** 实际测试供应商连接 */
    private Map<String, Object> testProviderConnection(String prov) {
        String url = API_BASES.getOrDefault(prov, "");
        if (url.isEmpty()) return null;
        String key = apiKey;
        if (key == null || key.isEmpty()) return null;
        try {
            var req = java.net.HttpURLConnection.class.cast(
                java.net.URI.create(url + "/models").toURL().openConnection());
            req.setRequestMethod("GET");
            req.setRequestProperty("Authorization", "Bearer " + key);
            req.setConnectTimeout(5000);
            req.setReadTimeout(5000);
            int code = req.getResponseCode();
            if (code == 200) {
                return Map.of("available", true, "message", prov + " 连接成功 ✓");
            } else if (code == 401) {
                return Map.of("available", false, "message", "API密钥无效 (401)");
            } else {
                return Map.of("available", true, "message", prov + " 响应 " + code + "（可尝试使用）");
            }
        } catch (java.net.SocketTimeoutException e) {
            return Map.of("available", false, "message", "连接超时，请检查网络或Base URL");
        } catch (Exception e) {
            return Map.of("available", false, "message", "连接失败: " + e.getMessage());
        }
    }

    /** AI裁决——根据当前provider分发 */
    public Map<String, Object> adjudicate(Map<String, Object> context) {
        return switch (provider) {
            case "local" -> localAdjudicate(context);
            case "deepseek" -> callDeepSeek(context);
            case "openai" -> callOpenAI(context);
            case "anthropic" -> callAnthropic(context);
            default -> localAdjudicate(context);
        };
    }

    // ═══════════════════════════════════════════ 本地模板 ═══════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> localAdjudicate(Map<String, Object> ctx) {
        String order = (String) ctx.getOrDefault("order", "");
        String faction = (String) ctx.getOrDefault("faction", "?");
        String lower = order.toLowerCase();
        Map<String, Object> result = new LinkedHashMap<>();

        if (matches(lower, "间谍", "情报", "侦察", "侦查", "刺探")) {
            result.put("feasibility", "high");
            result.put("cost", Map.of("treasury", -5));
            result.put("effects", Map.of("military", 2));
            result.put("risk", "low");
            result.put("ap_cost", 1);
            result.put("narrative", faction + "的情报人员渗透入敌境，带回了宝贵情报。");
        } else if (matches(lower, "外交", "谈判", "使节", "出使", "斡旋")) {
            result.put("feasibility", "medium");
            result.put("cost", Map.of("treasury", -8));
            result.put("effects", Map.of("diplomacy", 3));
            result.put("risk", "medium");
            result.put("ap_cost", 1);
            result.put("narrative", faction + "派出使节，在外交战场上纵横捭阖。");
        } else if (matches(lower, "建设", "发展", "工业", "工厂", "基础设施")) {
            result.put("feasibility", "high");
            result.put("cost", Map.of("treasury", -12));
            result.put("effects", Map.of("industry", 3, "economy", 2));
            result.put("risk", "low");
            result.put("ap_cost", 2);
            result.put("narrative", faction + "启动了新一轮建设计划，工业发展势头良好。");
        } else if (matches(lower, "偷袭", "游击", "骚扰", "破坏")) {
            result.put("feasibility", "medium");
            result.put("cost", Map.of("treasury", -3));
            result.put("effects", Map.of("military", 2));
            result.put("risk", "high");
            result.put("ap_cost", 1);
            result.put("narrative", "一支小分队对敌境发动了突袭，成果有限但震慑效果显著。");
        } else if (matches(lower, "宣传", "动员", "民心", "演讲")) {
            result.put("feasibility", "high");
            result.put("cost", Map.of("treasury", -5));
            result.put("effects", Map.of("ideology", 4));
            result.put("risk", "low");
            result.put("ap_cost", 1);
            result.put("narrative", "宣传机器全力运转，民众对" + faction + "的支持率稳步上升。");
        } else if (matches(lower, "贸易", "通商", "商路")) {
            result.put("feasibility", "high");
            result.put("cost", Map.of("treasury", -6));
            result.put("effects", Map.of("economy", 3, "diplomacy", 1));
            result.put("risk", "low");
            result.put("ap_cost", 1);
            result.put("narrative", faction + "开辟了新的贸易路线，商队络绎不绝。");
        } else if (matches(lower, "改革", "改制", "革新")) {
            result.put("feasibility", "medium");
            result.put("cost", Map.of("treasury", -15));
            result.put("effects", Map.of("ideology", 3, "economy", 3));
            result.put("risk", "medium");
            result.put("ap_cost", 2);
            result.put("narrative", faction + "推行了一系列改革措施，虽遇阻力但方向正确。");
        } else if (matches(lower, "镇压", "戒严", "清剿", "剿匪")) {
            result.put("feasibility", "high");
            result.put("cost", Map.of("treasury", -4));
            result.put("effects", Map.of("military", 1, "ideology", 2));
            result.put("risk", "low");
            result.put("ap_cost", 1);
            result.put("narrative", faction + "出动军警维持秩序，地方治安明显好转。");
        } else if (matches(lower, "撤退", "退兵", "撤军")) {
            result.put("feasibility", "high");
            result.put("cost", Map.of("treasury", -2));
            result.put("effects", Map.of());
            result.put("risk", "low");
            result.put("ap_cost", 1);
            result.put("narrative", faction + "下令部队有序后撤，保存有生力量。");
        } else {
            result.put("feasibility", "medium");
            result.put("cost", Map.of("treasury", -5));
            result.put("effects", Map.of());
            result.put("risk", "medium");
            result.put("ap_cost", 1);
            result.put("narrative", faction + "执行了「" + order + "」行动。局势尚无大的变化，但已经埋下了种子。");
        }
        result.put("provider", "local");
        return result;
    }

    // ═══════════════════════════════════════════ API供应商 ═══════════════════════════════════════════

    private Map<String, Object> callDeepSeek(Map<String, Object> ctx) {
        return callOpenAICompat("deepseek", ctx);
    }

    private Map<String, Object> callOpenAI(Map<String, Object> ctx) {
        return callOpenAICompat("openai", ctx);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callOpenAICompat(String providerName, Map<String, Object> ctx) {
        if (apiKey == null || apiKey.isEmpty()) {
            return Map.of("provider", providerName, "error", "未设置API密钥，请在设置中配置");
        }
        try {
            String url = (baseUrl != null && !baseUrl.isEmpty() ? baseUrl : API_BASES.get(providerName))
                    + "/chat/completions";
            String modelName = model != null ? model : DEFAULT_MODELS.get(providerName);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            body.put("max_tokens", 600);
            body.put("temperature", 0.7);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", buildUserPrompt(ctx))
            ));

            // 实际HTTP调用 — 需要java.net.http
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                    .build();
            java.net.http.HttpResponse<String> resp = client.send(req,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                Map<String, Object> respData = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(resp.body(), Map.class);
                Map<String, Object> result = parseAiResponse(respData, providerName);
                if (!result.containsKey("error") && !result.containsKey("provider"))
                    result.put("provider", providerName);
                return result;
            }
            return Map.of("provider", providerName, "error",
                    "API返回错误 " + resp.statusCode() + ": " + resp.body().substring(0, Math.min(200, resp.body().length())));
        } catch (Exception e) {
            return Map.of("provider", providerName, "error", "连接失败: " + e.getMessage());
        }
    }

    private Map<String, Object> callAnthropic(Map<String, Object> ctx) {
        if (apiKey == null || apiKey.isEmpty()) {
            return Map.of("provider", "anthropic", "error", "未设置API密钥");
        }
        try {
            String url = (baseUrl != null && !baseUrl.isEmpty() ? baseUrl : API_BASES.get("anthropic"))
                    + "/v1/messages";
            String modelName = model != null ? model : DEFAULT_MODELS.get("anthropic");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            body.put("max_tokens", 600);
            body.put("messages", List.of(
                    Map.of("role", "user", "content", SYSTEM_PROMPT + "\n\n" + buildUserPrompt(ctx))
            ));

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                    .build();
            java.net.http.HttpResponse<String> resp = client.send(req,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            return parseAiProviderResponse(resp.body(), "anthropic");
        } catch (Exception e) {
            return Map.of("provider", "anthropic", "error", "连接失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAiProviderResponse(String body, String providerName) {
        try {
            Map<String, Object> respData = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(body, Map.class);
            // Anthropic format
            if (respData.containsKey("content")) {
                var content = respData.get("content");
                String text = "";
                if (content instanceof List) {
                    for (Object block : (List<?>) content) {
                        if (block instanceof Map && ((Map<String, Object>) block).containsKey("text")) {
                            text = (String) ((Map<String, Object>) block).get("text");
                            break;
                        }
                    }
                } else if (content instanceof String) {
                    text = (String) content;
                }
                return extractJsonFromText(text, providerName);
            }
            return Map.of("provider", providerName, "error", "无法解析响应");
        } catch (Exception e) {
            return Map.of("provider", providerName, "error", "解析失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAiResponse(Map<String, Object> respData, String providerName) {
        try {
            var choices = respData.get("choices");
            if (choices instanceof List && !((List<?>) choices).isEmpty()) {
                Object first = ((List<?>) choices).get(0);
                if (first instanceof Map) {
                    Map<String, Object> msg = (Map<String, Object>) ((Map<String, Object>) first).get("message");
                    if (msg != null) {
                        return extractJsonFromText((String) msg.getOrDefault("content", ""), providerName);
                    }
                }
            }
        } catch (Exception e) { /* fall through */ }
        return Map.of("provider", providerName, "error", "无法解析AI响应");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractJsonFromText(String text, String providerName) {
        try {
            // 1. 尝试提取 ```json ... ``` 块
            int cbStart = text.indexOf("```json");
            if (cbStart >= 0) {
                int cbEnd = text.indexOf("```", cbStart + 7);
                if (cbEnd > cbStart) {
                    String json = text.substring(cbStart + 7, cbEnd).trim();
                    Map<String, Object> r = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
                    if (!r.containsKey("narrative")) r.put("narrative", "");
                    return r;
                }
            }
            // 2. 尝试提取 ``` ... ``` 块（无语言标记）
            cbStart = text.indexOf("```");
            if (cbStart >= 0) {
                int cbEnd = text.indexOf("```", cbStart + 3);
                if (cbEnd > cbStart) {
                    String block = text.substring(cbStart + 3, cbEnd).trim();
                    if (block.startsWith("{")) {
                        Map<String, Object> r = new com.fasterxml.jackson.databind.ObjectMapper().readValue(block, Map.class);
                        if (!r.containsKey("narrative")) r.put("narrative", "");
                        return r;
                    }
                }
            }
            // 3. 尝试提取裸JSON块
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String json = text.substring(start, end + 1);
                Map<String, Object> r = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
                if (!r.containsKey("narrative")) r.put("narrative", "");
                return r;
            }
            // 4. 无JSON → 把全文当叙事
            return Map.of("provider", providerName, "narrative", text,
                    "feasibility", "medium", "cost", Map.of(), "effects", Map.of(),
                    "risk", "medium", "ap_cost", 1);
        } catch (Exception e) {
            return Map.of("provider", providerName, "narrative", text,
                    "feasibility", "medium", "cost", Map.of(), "effects", Map.of(),
                    "risk", "medium", "ap_cost", 1,
                    "parse_error", e.getMessage());
        }
    }

    private static boolean matches(String text, String... keywords) {
        for (String kw : keywords) if (text.contains(kw)) return true;
        return false;
    }

    private String buildUserPrompt(Map<String, Object> ctx) {
        return """
            请根据以下游戏状态裁决该自由行动，以JSON返回：
            {
              "feasibility": "high|medium|low|impossible",
              "feasibility_reason": "（仅impossible时需要）",
              "cost": {"treasury": -N},
              "effects": {"industry": N, "military": N, ...},
              "risk": "low|medium|high",
              "ap_cost": N,
              "narrative": "沉浸式叙事文本，80-150字",
              "special_notes": "可选持久标记"
            }

            游戏状态：
            势力：""" + ctx.getOrDefault("faction", "?") + """
            国库：""" + ctx.getOrDefault("treasury", 0) + """
            领土：""" + ctx.getOrDefault("territories", "[]") + """
            军队：""" + ctx.getOrDefault("units", "[]") + """
            回合：""" + ctx.getOrDefault("turn", 0) + """

            自由行动：""" + ctx.getOrDefault("order", "");
    }

    private static final String SYSTEM_PROMPT = """
        你是"七域逐鹿"游戏的AI GM。这是一款架空1910年代中华大地的文字策略战棋游戏。
        玩家输入自然语言指令，你根据游戏上下文裁决行动的可行性、成本、效果、风险和AP消耗。
        裁决必须合理——消耗与收益对等，叙事需贴合时代背景（80-200字）。

        返回JSON格式，包含:
        - feasibility: high|medium|low|impossible
        - cost: {"treasury": -N} 或其他资源消耗
        - effects: {"industry": N, "military": N, ...} 有效键: industry, agriculture, military, economy, ideology, diplomacy, naval_power, population_support
        - ap_cost: 简单1, 复杂2, 重大3
        - risk: low|medium|high (触发概率10%/20%/35%, 惩罚民心-1~5)
        - narrative: 沉浸式叙事

        **重要**: 在narrative末尾可以添加 [变更]...[/变更] 标注块来触发具体游戏操作:
        [变更]
        "势力名" 被吞并
        "部队名" 移动到 "城市名"
        在 "城市名" 直接创建 两支 步兵
        占领 "省份名"
        效果：工业+5 军事+3
        [/变更]

        支持的变更类型: 吞并/移动到/直接创建/占领/解散/属性修改/效果加减
        城市名使用实际地图上的中文名，部队名使用游戏中已存在的番号。""";
}
