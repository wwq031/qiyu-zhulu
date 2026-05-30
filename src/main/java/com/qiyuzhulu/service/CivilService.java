package com.qiyuzhulu.service;

import com.qiyuzhulu.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 内政系统 — 建设、内政菜单。
 * 对应 Python qiyu_actions_civil.py。
 */
@Service
public class CivilService {

    private final GameEngine engine;

    /** 建设项目定义 */
    public static final Map<String, Map<String, Object>> BUILD_DEFS = new LinkedHashMap<>();
    static {
        BUILD_DEFS.put("2.1",  Map.of("name","兴建工厂","icon","🏭","cost",Map.of("economy",-10),"effect",Map.of("industry",3),"turns",6,"building_key","factory"));
        BUILD_DEFS.put("2.2",  Map.of("name","农田水利","icon","🌾","cost",Map.of("economy",-5),"effect",Map.of("agriculture",3),"turns",3,"building_key","irrigation"));
        BUILD_DEFS.put("2.3",  Map.of("name","开办军校","icon","🎖","cost",Map.of("economy",-8),"effect",Map.of("military",3),"turns",4,"building_key","academy"));
        BUILD_DEFS.put("2.4",  Map.of("name","统一货币","icon","💰","cost",Map.of("economy",-15),"effect",Map.of("economy",5),"turns",8));
        BUILD_DEFS.put("2.5",  Map.of("name","宣传教育","icon","📖","cost",Map.of("economy",-5),"effect",Map.of("ideology",4),"turns",3));
        BUILD_DEFS.put("2.6",  Map.of("name","遣使修好","icon","🌐","cost",Map.of("economy",-8),"effect",Map.of("diplomacy",3),"turns",2));
        BUILD_DEFS.put("2.8",  Map.of("name","招募步兵团","icon","🛡","cost",Map.of("economy",-8),"effect",Map.of("military",4,"infantry",1),"turns",2));
        BUILD_DEFS.put("2.9",  Map.of("name","组建骑兵队","icon","🐴","cost",Map.of("economy",-12),"effect",Map.of("military",6,"cavalry",1),"turns",3));
        BUILD_DEFS.put("2.10", Map.of("name","炮兵工厂","icon","💣","cost",Map.of("economy",-18),"effect",Map.of("military",10,"industry",2,"artillery",1),"turns",5));
        BUILD_DEFS.put("2.11", Map.of("name","军事技术研究","icon","⚙","cost",Map.of("economy",-15),"effect",Map.of("military",3,"military_tech",1),"turns",4));
    }

    public CivilService(GameEngine engine) { this.engine = engine; }

    /** 列出所有可用的建设项目 */
    public List<Map<String, Object>> listConstruction() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (var entry : BUILD_DEFS.entrySet()) {
            Map<String, Object> b = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", entry.getKey());
            item.put("name", b.get("name"));
            item.put("icon", b.getOrDefault("icon", ""));
            @SuppressWarnings("unchecked")
            Map<String, Integer> cost = (Map<String, Integer>) b.get("cost");
            item.put("cost", cost.getOrDefault("economy", 0));
            item.put("turns", b.get("turns"));
            item.put("needs_province", b.containsKey("building_key"));
            items.add(item);
        }
        return items;
    }

    /** 执行建设项目 */
    public Map<String, Object> build(GameState state, String buildId, String locationPid) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> b = BUILD_DEFS.get(buildId);
        if (b == null) {
            result.put("ok", false);
            result.put("message", "未知建设类型: " + buildId);
            return result;
        }

        FactionState fs = state.getFactionState();
        @SuppressWarnings("unchecked")
        Map<String, Integer> cost = (Map<String, Integer>) b.get("cost");
        int costEco = Math.abs(cost.getOrDefault("economy", 0));

        // 地块级建设验证
        String buildingKey = (String) b.get("building_key");
        int effectiveInd = 0;
        if (buildingKey != null) {
            if (locationPid == null || locationPid.isEmpty()) {
                result.put("ok", false);
                result.put("message", "「" + b.get("name") + "」需要选择目标省份！");
                return result;
            }
            Province p = engine.getProvince(locationPid);
            if (p == null || !fs.getTerritories().contains(p.getName())) {
                result.put("ok", false);
                result.put("message", (p != null ? p.getName() : locationPid) + " 不在您的控制范围内！");
                return result;
            }
            int bonusInd = fs.getBuildingLevel(locationPid, "factory") * 2;
            effectiveInd = Math.min(10, p.getIndustry() + bonusInd);

            if (effectiveInd >= 7) costEco = (int)(costEco * 0.7);
            else if (effectiveInd >= 5) costEco = (int)(costEco * 0.85);
        }

        if (fs.getTreasury() < costEco) {
            result.put("ok", false);
            result.put("message", "国库不足！需要 " + costEco + "💰，当前 " + fs.getTreasury() + "💰");
            return result;
        }

        fs.setTreasury(fs.getTreasury() - costEco);

        // 建设队列
        ConstructionItem item = new ConstructionItem();
        item.setName((String) b.get("name"));
        item.setTurnsLeft((Integer) b.get("turns"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> effect = (Map<String, Integer>) b.get("effect");
        item.setEffect(new LinkedHashMap<>(effect));
        if (buildingKey != null) {
            item.setBuildingKey(buildingKey);
            item.setLocationPid(locationPid);
        }
        state.getConstructionQueue().add(item);

        String discountStr = effectiveInd >= 5 ? "（🏭折扣 " + costEco + "💰）" : "";
        String locStr = locationPid != null ? " @" + engine.getProvince(locationPid).getName() : "";

        result.put("ok", true);
        result.put("message", "启动：" + b.get("name") + locStr + "  消耗 " + costEco + "💰" + discountStr + "  预计 " + b.get("turns") + " 回合");
        return result;
    }

    /** 运行内政菜单（返回前端所需数据） */
    public Map<String, Object> getDomesticMenu(GameState state) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("construction", true);
        data.put("items", listConstruction());
        data.put("queue_len", state.getConstructionQueue().size());
        return data;
    }
}
