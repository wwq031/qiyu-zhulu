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
    private final EventService eventService;

    public CampaignController(GameEngine engine, CampaignService campaign,
                               MilitaryService military, StateController stateCtrl,
                               EventService eventService) {
        this.engine = engine;
        this.campaign = campaign;
        this.military = military;
        this.stateCtrl = stateCtrl;
        this.eventService = eventService;
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

    /** POST /api/map/reachable — BFS多步搜索可达省份（铁路加速） */
    @PostMapping("/map/reachable")
    public Map<String, Object> reachable(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        FactionState fs = game.getFactionState();

        // 确定起始位置、速度、部队信息
        String pos = null;
        int baseSpeed = 1;
        String unitName = "";
        List<Integer> unitIndices = new ArrayList<>();
        List<Map<String, Object>> localUnits = new ArrayList<>();
        // 1) unit_indices（复数，前端传的）
        Object indicesObj = body.get("unit_indices");
        if (indicesObj instanceof List && !((List<?>) indicesObj).isEmpty()) {
            for (Object o : (List<?>) indicesObj) {
                int idx = ((Number) o).intValue();
                if (idx >= 0 && idx < fs.getUnits().size()) {
                    unitIndices.add(idx);
                    Unit u = fs.getUnits().get(idx);
                    if (pos == null) {
                        pos = engine.resolvePositionToPid(u.getPosition());
                        baseSpeed = u.getSpeed();
                        unitName = u.getName();
                    }
                }
            }
        }
        // 2) unit_index（单数）
        if (pos == null) {
            Object idxObj = body.get("unit_index");
            if (idxObj instanceof Number) {
                int idx = ((Number) idxObj).intValue();
                if (idx >= 0 && idx < fs.getUnits().size()) {
                    unitIndices.add(idx);
                    Unit u = fs.getUnits().get(idx);
                    pos = engine.resolvePositionToPid(u.getPosition());
                    baseSpeed = u.getSpeed();
                    unitName = u.getName();
                }
            }
        }
        // 3) position（直接指定）
        if (pos == null) {
            pos = engine.resolvePositionToPid((String) body.get("position"));
            if (pos != null) {
                // 收集该位置所有玩家部队
                for (int i = 0; i < fs.getUnits().size(); i++) {
                    Unit u = fs.getUnits().get(i);
                    if (pos.equals(engine.resolvePositionToPid(u.getPosition()))) {
                        unitIndices.add(i);
                        if (unitName.isEmpty()) { unitName = u.getName(); baseSpeed = u.getSpeed(); }
                    }
                }
            }
        }
        // 4) 回退
        if (pos == null) pos = "beijing";
        // 构建 local_units 列表
        for (int i = 0; i < fs.getUnits().size(); i++) {
            Unit u = fs.getUnits().get(i);
            if (pos.equals(engine.resolvePositionToPid(u.getPosition())) && u.isActive()) {
                localUnits.add(Map.of("name", u.getName(), "type", u.getType(),
                        "attack", u.getAttack(), "defense", u.getDefense(),
                        "strength", u.getStrength(), "morale", u.getMorale(),
                        "index", i, "icon", "🗡"));
            }
        }

        int effectiveSpeed = engine.hasRailway(pos) ? baseSpeed * 2 : Math.max(2, baseSpeed);

        // BFS
        List<Map<String, Object>> reachable = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(pos);
        Queue<Object[]> queue = new LinkedList<>();
        queue.add(new Object[]{pos, new ArrayList<>(List.of(pos)), 0, engine.hasRailway(pos)});

        while (!queue.isEmpty()) {
            Object[] item = queue.poll();
            String current = (String) item[0];
            @SuppressWarnings("unchecked")
            List<String> path = (List<String>) item[1];
            int dist = (int) item[2];
            boolean onRail = (boolean) item[3];
            Province p = engine.getProvince(current);
            if (p == null || p.getConnections() == null) continue;

            for (var nbEntry : p.getConnections().entrySet()) {
                String nbPid = nbEntry.getKey();
                boolean nbRail = engine.hasRailway(nbPid);
                int totalDist = dist + 1; // 每跳固定+1步
                if (!visited.contains(nbPid)) {
                    visited.add(nbPid);
                    Province nb = engine.getProvince(nbPid);
                    if (nb != null && totalDist <= effectiveSpeed) {
                        List<String> newPath = new ArrayList<>(path);
                        newPath.add(nbPid);
                        reachable.add(Map.of(
                                "pid", nbPid, "name", nb.getName(),
                                "lat", nb.getLat(), "lng", nb.getLng(),
                                "distance", totalDist, "path", newPath,
                                "is_enemy", !fs.getTerritories().contains(nb.getName()),
                                "enemy_fid", ""));
                        if (totalDist < effectiveSpeed)
                            queue.add(new Object[]{nbPid, newPath, totalDist, nbRail});
                    }
                }
            }
        }
        return Map.of("reachable", reachable, "unit_position", pos,
                "unit_name", unitName, "unit_indices", unitIndices, "local_units", localUnits);
    }

    /** POST /api/map/move — 移动部队 */
    @PostMapping("/map/move")
    @SuppressWarnings("unchecked")
    public Map<String, Object> move(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        FactionState fs = game.getFactionState();

        String destPid = engine.resolvePositionToPid((String) body.get("dest_pid"));
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

        String destPid = engine.resolvePositionToPid((String) body.get("dest_pid"));
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

    /** POST /api/event-chain/resolve — 事件链抉择 */
    @PostMapping("/event-chain/resolve")
    public Map<String, Object> resolveChain(@RequestBody Map<String, Object> body) {
        GameState game = stateCtrl.getGame();
        if (game == null) return Map.of("error", "无存档");
        String chainKey = (String) body.get("chain_key");
        Integer optionIndex = body.get("option_index") != null
                ? ((Number) body.get("option_index")).intValue() : 0;
        Map<String, Object> result = eventService.resolveChainChoice(game, chainKey, optionIndex);
        Map<String, Object> resp = stateCtrl.buildPanelResponse(game);
        resp.putAll(result);
        return resp;
    }
}
