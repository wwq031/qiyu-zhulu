package com.qiyuzhulu.controller;

import com.qiyuzhulu.model.*;
import com.qiyuzhulu.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 自然语言指令 — 委托 AI Provider + Sandbox 系统处理。
 */
@RestController
@RequestMapping("/api")
public class CommandController {

    private final AiProviderService aiProvider;
    private final SandboxService sandbox;
    private final StateController stateCtrl;

    public CommandController(AiProviderService aiProvider, SandboxService sandbox,
                              StateController stateCtrl) {
        this.aiProvider = aiProvider;
        this.sandbox = sandbox;
        this.stateCtrl = stateCtrl;
    }

    /** POST /api/command/parse — AI解析自然语言为结构化指令 */
    @PostMapping("/command/parse")
    public Map<String, Object> parse(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        String text = (String) body.getOrDefault("command", "");
        if (text == null || text.isEmpty()) return Map.of("error", "缺少 command");

        // 委托 AI 裁决（含 [变更] 标注解析）
        return sandbox.aiAdjudicate(game, text);
    }

    /** POST /api/command/execute — 应用AI裁决结果 */
    @PostMapping("/command/execute")
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        String order = (String) body.getOrDefault("command", "");
        Map<String, Object> adjudication = (Map<String, Object>) body.get("adjudication");
        if (adjudication == null) return Map.of("error", "缺少 adjudication");

        Map<String, Object> result = sandbox.apply(game, order, adjudication);
        Map<String, Object> resp = stateCtrl.buildPanelResponse(game);
        resp.putAll(result);
        return resp;
    }
}
