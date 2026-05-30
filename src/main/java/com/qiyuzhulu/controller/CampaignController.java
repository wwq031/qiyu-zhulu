package com.qiyuzhulu.controller;

import com.qiyuzhulu.model.*;
import com.qiyuzhulu.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 战役系统API — /api/campaign/*, /api/map/reachable, move, attack。
 */
@RestController
@RequestMapping("/api")
public class CampaignController {

    private final GameEngine engine;
    private final CampaignService campaign;
    private final MilitaryService military;
    private final StateController stateCtrl;

    public CampaignController(GameEngine engine, CampaignService campaign,
                               MilitaryService military, StateController stateCtrl) {
        this.engine = engine;
        this.campaign = campaign;
        this.military = military;
        this.stateCtrl = stateCtrl;
    }

    /** POST /api/campaign/honor — 战役授勋 */
    @PostMapping("/campaign/honor")
    public Map<String, Object> honor(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        String campaignId = (String) body.get("campaign_id");
        if (campaignId == null) return Map.of("error", "缺少 campaign_id");
        Map<String, Object> result = campaign.honorCampaignUnits(game, campaignId);
        Map<String, Object> resp = stateCtrl.buildPanelResponse(game);
        resp.putAll(result);
        return resp;
    }

    /** POST /api/campaign/tactics — 更换战术 */
    @PostMapping("/campaign/tactics")
    public Map<String, Object> tactics(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        String campaignId = (String) body.get("campaign_id");
        @SuppressWarnings("unchecked")
        Map<String, String> changes = (Map<String, String>) body.get("unit_tactic_changes");
        if (campaignId == null || changes == null) return Map.of("error", "缺少参数");
        Map<String, Object> result = campaign.changeCampaignTactics(game, campaignId, changes, null);
        Map<String, Object> resp = stateCtrl.buildPanelResponse(game);
        resp.putAll(result);
        return resp;
    }

    /** POST /api/campaign/retreat — 撤退 */
    @PostMapping("/campaign/retreat")
    public Map<String, Object> retreat(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        String campaignId = (String) body.get("campaign_id");
        if (campaignId == null) return Map.of("error", "缺少 campaign_id");
        // 找到战役索引
        int idx = -1;
        for (int i = 0; i < game.getActiveCampaigns().size(); i++) {
            if (game.getActiveCampaigns().get(i).getId().equals(campaignId)) { idx = i; break; }
        }
        Map<String, Object> result = campaign.retreatFromCampaign(game, idx);
        Map<String, Object> resp = stateCtrl.buildPanelResponse(game);
        resp.putAll(result);
        return resp;
    }

    /** POST /api/campaign/reinforce — 增援 */
    @PostMapping("/campaign/reinforce")
    public Map<String, Object> reinforce(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        String campaignId = (String) body.get("campaign_id");
        @SuppressWarnings("unchecked")
        List<Integer> indices = (List<Integer>) body.get("unit_indices");
        if (campaignId == null || indices == null) return Map.of("error", "缺少参数");
        int idx = -1;
        for (int i = 0; i < game.getActiveCampaigns().size(); i++) {
            if (game.getActiveCampaigns().get(i).getId().equals(campaignId)) { idx = i; break; }
        }
        Map<String, Object> result = campaign.reinforceCampaign(game, idx, indices, null);
        Map<String, Object> resp = stateCtrl.buildPanelResponse(game);
        resp.putAll(result);
        return resp;
    }

    /** POST /api/map/reachable — 可到达省份 */
    @PostMapping("/map/reachable")
    public Map<String, Object> reachable(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        String pos = (String) body.get("position");
        FactionState fs = game.getFactionState();

        List<Map<String, Object>> reachable = new ArrayList<>();
        Province fromP = engine.getProvince(pos);
        if (fromP != null && fromP.getConnections() != null) {
            for (String nbPid : fromP.getConnections().keySet()) {
                Province nb = engine.getProvince(nbPid);
                if (nb != null) {
                    reachable.add(Map.of(
                            "pid", nbPid, "name", nb.getName(),
                            "lat", nb.getLat(), "lng", nb.getLng(),
                            "distance", 1, "path", List.of(pos, nbPid),
                            "is_enemy", !fs.getTerritories().contains(nb.getName()),
                            "enemy_fid", ""));
                }
            }
        }
        return Map.of("reachable", reachable, "unit_position", pos);
    }

    /** POST /api/map/move — 移动部队 */
    @PostMapping("/map/move")
    @SuppressWarnings("unchecked")
    public Map<String, Object> move(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        FactionState fs = game.getFactionState();

        String destPid = (String) body.get("dest_pid");
        Province dest = engine.getProvince(destPid);
        if (dest == null) return Map.of("error", "无效目的地");

        // 支持单部队和批量移动
        List<Integer> indices = (List<Integer>) body.get("unit_indices");
        if (indices == null || indices.isEmpty()) {
            Integer idx = (Integer) body.get("unit_index");
            if (idx != null) indices = List.of(idx);
        }

        List<String> moved = new ArrayList<>();
        if (indices != null && fs.getUnits() != null) {
            for (int idx : indices) {
                if (idx >= 0 && idx < fs.getUnits().size()) {
                    Unit u = fs.getUnits().get(idx);
                    if (!"fighting".equals(u.getStatus())) {
                        u.setPosition(destPid);
                        moved.add(u.getName());
                    }
                }
            }
        }

        Map<String, Object> resp = stateCtrl.buildPanelResponse(game);
        resp.put("ok", true);
        resp.put("dest_name", dest.getName());
        resp.put("message", moved.isEmpty() ? "部队已移动至 " + dest.getName()
                : String.join("、", moved) + " 已移动至 " + dest.getName());
        return resp;
    }

    /** POST /api/map/attack — 发动攻击 */
    @PostMapping("/map/attack")
    @SuppressWarnings("unchecked")
    public Map<String, Object> attack(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");

        String destPid = (String) body.get("dest_pid");
        Province dest = engine.getProvince(destPid);
        if (dest == null) return Map.of("error", "无效目标");

        List<Integer> indices = (List<Integer>) body.get("unit_indices");
        if (indices == null || indices.isEmpty()) {
            Integer idx = (Integer) body.get("unit_index");
            if (idx != null) indices = List.of(idx);
        }
        if (indices == null || indices.isEmpty()) return Map.of("error", "未选择攻击部队");

        Map<String, String> tactics = (Map<String, String>) body.get("unit_tactics");
        Map<String, Object> result = campaign.startCampaign(game, destPid, new ArrayList<>(indices), tactics);

        Map<String, Object> resp = stateCtrl.buildPanelResponse(game);
        resp.putAll(result);
        return resp;
    }

    /** POST /api/campaign/start — Web版发动战役（选省→选部队→选战术） */
    @PostMapping("/campaign/start")
    @SuppressWarnings("unchecked")
    public Map<String, Object> startCampaign(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");

        String provincePid = (String) body.get("province_pid");
        List<Integer> indices = (List<Integer>) body.get("unit_indices");
        Map<String, String> tactics = (Map<String, String>) body.get("unit_tactics");
        if (provincePid == null || indices == null || indices.isEmpty())
            return Map.of("error", "缺少参数");

        Map<String, Object> result = campaign.startCampaign(game, provincePid, new ArrayList<>(indices), tactics);
        Map<String, Object> resp = stateCtrl.buildPanelResponse(game);
        resp.putAll(result);
        return resp;
    }

    /** GET /api/map/enemy-provinces — 可攻击的敌方省份列表 */
    @GetMapping("/map/enemy-provinces")
    public Map<String, Object> enemyProvinces() {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        return Map.of("enemy_provinces", campaign.listEnemyProvinces(game));
    }

    /** POST /api/event-chain/resolve — 事件链抉择（占位，后续事件系统补充） */
    @PostMapping("/event-chain/resolve")
    public Map<String, Object> resolveChain(@RequestBody Map<String, Object> body) {
        return Map.of("ok", true, "message", "抉择已处理");
    }
}
