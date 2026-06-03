package com.qiyuzhulu.controller;

import com.qiyuzhulu.model.*;
import com.qiyuzhulu.service.SandboxService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class CustomOrderController {

    private final SandboxService sandbox;
    private final StateController stateCtrl;

    public CustomOrderController(SandboxService sandbox, StateController stateCtrl) {
        this.sandbox = sandbox;
        this.stateCtrl = stateCtrl;
    }

    /** POST /api/custom-order — 构建裁决上下文 */
    @PostMapping("/custom-order")
    public Map<String, Object> buildOrder(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        String order = (String) body.get("order");
        if (order == null || order.isEmpty()) return Map.of("error", "缺少 order 参数");
        return Map.of(
                "context", sandbox.buildContext(game, order),
                "local_hint", sandbox.aiAdjudicate(game, order),
                "message", "裁决完成（若配置了AI API则使用AI，否则本地模板）"
        );
    }

    /** POST /api/custom-order/apply — 应用手动裁决 */
    @PostMapping("/custom-order/apply")
    @SuppressWarnings("unchecked")
    public Map<String, Object> applyOrder(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        String order = (String) body.get("order");
        Map<String, Object> adjudication = (Map<String, Object>) body.get("adjudication");
        if (adjudication == null) return Map.of("error", "缺少 adjudication");
        Map<String, Object> result = sandbox.apply(game, order, adjudication);
        Map<String, Object> resp = stateCtrl.buildPanelResponse(game);
        resp.putAll(result);
        return resp;
    }

    /** POST /api/custom-order/auto — 一键自动裁决 */
    @PostMapping("/custom-order/auto")
    public Map<String, Object> autoOrder(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        String order = (String) body.get("order");
        if (order == null || order.isEmpty()) return Map.of("error", "缺少 order");
        boolean isSandbox = Boolean.TRUE.equals(body.get("sandbox"));
        // 沙盒模式：注入sandbox标记到裁决结果
        Map<String, Object> adjudication = sandbox.aiAdjudicate(game, order);
        if (isSandbox) adjudication.put("sandbox", true);
        Map<String, Object> result = sandbox.apply(game, order, adjudication);
        Map<String, Object> resp = stateCtrl.buildPanelResponse(game);
        resp.putAll(result);
        resp.put("fallback", "local".equals(result.getOrDefault("provider", "local")));
        return resp;
    }
}
