package com.qiyuzhulu.controller;

import com.qiyuzhulu.model.*;
import com.qiyuzhulu.service.GameEngine;
import com.qiyuzhulu.service.PanelRenderer;
import com.qiyuzhulu.service.SandboxService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 自由指令API — /api/custom-order/*。
 */
@RestController
@RequestMapping("/api")
public class CustomOrderController {

    private final GameEngine engine;
    private final PanelRenderer renderer;
    private final SandboxService sandbox;
    private final StateController stateCtrl;

    public CustomOrderController(GameEngine engine, PanelRenderer renderer,
                                  SandboxService sandbox, StateController stateCtrl) {
        this.engine = engine;
        this.renderer = renderer;
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

        Map<String, Object> ctx = sandbox.buildContext(game, order);
        Map<String, Object> localHint = sandbox.localAdjudicate(game, order);

        return Map.of(
                "context", ctx,
                "local_hint", localHint,
                "message", "请裁决此自由行动，然后 POST /api/custom-order/apply"
        );
    }

    /** POST /api/custom-order/apply — 应用手动裁决 */
    @PostMapping("/custom-order/apply")
    public Map<String, Object> applyOrder(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");

        String order = (String) body.get("order");
        @SuppressWarnings("unchecked")
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

        Map<String, Object> adjudication = sandbox.localAdjudicate(game, order);
        Map<String, Object> result = sandbox.apply(game, order, adjudication);

        Map<String, Object> resp = stateCtrl.buildPanelResponse(game);
        resp.putAll(result);
        resp.put("fallback", true);
        resp.put("provider", "local");
        return resp;
    }
}
