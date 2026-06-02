package com.qiyuzhulu.service;

import com.qiyuzhulu.model.*;
import com.qiyuzhulu.repo.GameDataRepo;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI势力回合处理 — 收入/招募/清理/移动/进攻/外交。
 * 对应 Python qiyu_actions_ai.py（564行）。
 */
@Service
public class AiFactionService {

    private final GameEngine engine;
    private final GameDataRepo gameData;
    private final Random rng = new Random();

    public AiFactionService(GameEngine engine, GameDataRepo gameData) {
        this.engine = engine;
        this.gameData = gameData;
    }

    // ═══════════════════════════════════════════ 初始化 ═══════════════════════════════════════════

    /** 初始化所有AI势力的faction_state */
    public void initialize(GameState state) {
        for (var entry : state.getAiFactions().entrySet()) {
            String fid = entry.getKey();
            if (fid.equals(state.getPlayerFactionId())) continue;
            AiFactionData ad = entry.getValue();
            FactionState fs = ad.getFactionState();
            if (fs != null && fs.getUnits() != null && !fs.getUnits().isEmpty()) continue;

            FactionDefinition faction = engine.getFaction(fid).orElse(null);
            if (faction == null) continue;

            if (fs == null) { fs = new FactionState(); ad.setFactionState(fs); }
            fs.setName(faction.getName());
            fs.setStats(faction.getStats().copy());
            fs.setTerritories(new ArrayList<>(ad.getTerritories() != null ? ad.getTerritories() : faction.getInitialTerritory()));
            fs.setCapital(fs.getTerritories().isEmpty() ? "" : fs.getTerritories().get(0));
            fs.setForces(new ArrayList<>(faction.getInitialForces()));
            fs.setTreasury(faction.getStats().getEconomy() * 2);
            fs.setPopulationSupport(50);
            fs.setMilitaryTech(1);
            fs.setArmy(new HashMap<>(Map.of("infantry",0,"cavalry",0,"artillery",0,"engineer",0,"naval",0)));
            fs.setUnitSerial(new HashMap<>(Map.of("total",0,"infantry",0,"cavalry",0,"artillery",0,"engineer",0,"naval",0)));
            fs.setUnitPrefix(engine.deriveUnitPrefix(faction.getName()));

            // 国家精神（优先Phase1奏折分配的spirit）
            Map<String, NationalSpirit> pending = state.getPendingSpirits();
            NationalSpirit spirit = (pending != null && pending.containsKey(fid))
                    ? pending.get(fid)
                    : engine.getNationalSpirit(faction);
            if (spirit != null) {
                fs.setNationalSpirit(spirit);
                if (spirit.getEffects() != null) {
                    for (var eff : spirit.getEffects().entrySet()) {
                        fs.getStats().add(eff.getKey(), eff.getValue());
                    }
                }
            }

            List<String> forces = faction.getInitialForces();
            if (forces != null && !forces.isEmpty()) {
                List<Unit> units = new ArrayList<>();
                Map<String, Integer> serial = new HashMap<>(fs.getUnitSerial());
                for (String fname : forces) {
                    String type = engine.inferUnitType(fname);
                    serial.merge("total", 1, Integer::sum);
                    serial.merge(type, 1, Integer::sum);
                    Unit u = new Unit();
                    u.setName(fname);
                    u.setType(type);
                    u.setAttack(GameEngine.clamp(rng.nextInt(8)+5, 3, 25));
                    u.setDefense(GameEngine.clamp(rng.nextInt(8)+3, 3, 25));
                    u.setMorale(rng.nextInt(31)+40);
                    u.setExperience(rng.nextInt(21)+10);
                    u.setSpeed("cavalry".equals(type) ? 2 : 1);
                    u.setStrength(rng.nextInt(41)+60);
                    u.setMaxStrength(100);
                    u.setStatus("ready");
                    if (!fs.getTerritories().isEmpty())
                        u.setPosition(fs.getTerritories().get(units.size() % fs.getTerritories().size()));
                    units.add(u);
                }
                fs.setUnits(units);
                fs.setUnitSerial(serial);
                fs.setArmy(engine.recountArmyFromUnits(units));
            }
        }
    }

    // ═══════════════════════════════════════════ 主处理 ═══════════════════════════════════════════

    /** AI势力回合处理 */
    public List<String> process(GameState state) {
        List<String> results = new ArrayList<>();
        for (var entry : state.getAiFactions().entrySet()) {
            String fid = entry.getKey();
            if (fid.equals(state.getPlayerFactionId())) continue;
            if (state.getDefeatedFactions().contains(fid)) continue;
            FactionState fs = entry.getValue().getFactionState();
            if (fs == null || fs.getTerritories() == null || fs.getTerritories().isEmpty()) continue;

            AiPersonality pers = getAiPersonality(fid);

            // 1. 收入
            int income = calcAiIncome(fs);
            fs.setTreasury(fs.getTreasury() + income);

            // 1b. 补给
            for (Unit u : fs.getUnits()) {
                if (!u.isActive() || "fighting".equals(u.getStatus())) continue;
                Object[] s = engine.calcSupply(engine.resolvePositionToPid(u.getPosition()), fs.getTerritories());
                String lvl = (String) s[0];
                u.setSupply(lvl);
                if ("isolated".equals(lvl)) {
                    u.setMorale(Math.max(5, u.getMorale() - 15));
                    u.setStrength(Math.max(1, u.getStrength() - 5));
                } else if ("cut_off".equals(lvl)) {
                    u.setMorale(Math.max(10, u.getMorale() - 5));
                } else if ("strained".equals(lvl)) {
                    u.setMorale(Math.max(15, u.getMorale() - 2));
                }
            }

            // 2. 维持费
            int maint = engine.calcTotalMaintenance(fs);
            if (fs.getTreasury() >= maint) {
                fs.setTreasury(fs.getTreasury() - maint);
            } else {
                fs.setTreasury(0);
                for (Unit u : fs.getUnits()) {
                    if (u.isActive()) {
                        u.setMorale(Math.max(5, u.getMorale() - 10));
                        u.setStrength(Math.max(1, u.getStrength() - 3));
                    }
                }
            }

            // 3. 招募
            results.addAll(aiRecruit(state, fs, fid, pers));

            // 4. 清理
            aiCleanup(fs);

            // 5. 移动
            aiMoveUnits(state, fs, fid, pers);

            // 6. 发动战役
            results.addAll(aiLaunchCampaigns(state, fs, fid, pers));
        }

        // 7. AI外交
        results.addAll(checkAiDiplomacy(state));

        return results;
    }

    // ═══════════════════════════════════════════ 收入 ═══════════════════════════════════════════

    private int calcAiIncome(FactionState fs) {
        Map<String, Object> econ = engine.aggregateTerritoryEconomy(fs);
        int commerce = ((Number) econ.getOrDefault("commerce", 0)).intValue();
        int agriculture = ((Number) econ.getOrDefault("agriculture", 0)).intValue();
        return (int)(commerce * 1.5 + agriculture * 0.8)
                + fs.getStats().getEconomy() / 8 + fs.getStats().getIndustry() / 10;
    }

    // ═══════════════════════════════════════════ 招募 ═══════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private List<String> aiRecruit(GameState state, FactionState fs, String fid, AiPersonality pers) {
        List<String> results = new ArrayList<>();
        int treasury = fs.getTreasury();
        List<Unit> active = fs.getActiveUnits();
        int maxUnits = 8 + fs.getStats().getMilitary() / 10;
        if (active.size() >= maxUnits) return results;

        double chance = pers.aggression() * 0.55;
        if (treasury < 6) chance *= 0.4;
        if (rng.nextDouble() >= chance) return results;

        int cost = rng.nextInt(7) + 6; // 6-12
        if (treasury < cost) return results;
        fs.setTreasury(treasury - cost);

        String utype = rng.nextDouble() < 0.15 ? "cavalry" : "infantry";
        Map<String, Object> ut = (Map<String, Object>) GameEngine.UNIT_TYPES.getOrDefault(utype, GameEngine.UNIT_TYPES.get("infantry"));
        List<String> territories = fs.getTerritories();
        String pos = nameToPid(territories.get(rng.nextInt(territories.size())));

        Map<String, Integer> serial = fs.getUnitSerial();
        if (serial == null) serial = new HashMap<>();
        serial.merge("total", 1, Integer::sum);
        serial.merge(utype, 1, Integer::sum);
        String prefix = fs.getUnitPrefix() != null ? fs.getUnitPrefix() : "新编";
        String unitName = engine.generateUnitName(prefix, utype, serial.getOrDefault(utype, 1));

        Unit u = new Unit();
        u.setName(unitName); u.setType(utype);
        int atkBonus = ut.get("atk_bonus") != null ? ((Number) ut.get("atk_bonus")).intValue() : 5;
        int defBonus = ut.get("def_bonus") != null ? ((Number) ut.get("def_bonus")).intValue() : 3;
        u.setAttack(GameEngine.clamp(5 + atkBonus + rng.nextInt(5) - 2, 3, 25));
        u.setDefense(GameEngine.clamp(5 + defBonus + rng.nextInt(5) - 2, 3, 25));
        u.setMorale(rng.nextInt(31) + 35);
        u.setExperience(rng.nextInt(16) + 5);
        u.setPosition(pos);
        u.setSpeed("cavalry".equals(utype) ? 2 : 1);
        u.setStrength(rng.nextInt(31) + 60);
        u.setMaxStrength(100);
        u.setStatus("ready");

        if (fs.getUnits() == null) fs.setUnits(new ArrayList<>());
        fs.getUnits().add(u);
        fs.setUnitSerial(serial);
        fs.setArmy(engine.recountArmyFromUnits(fs.getUnits()));
        results.add("📋 " + fs.getName() + " 招募了 " + unitName);
        return results;
    }

    // ═══════════════════════════════════════════ 清理 ═══════════════════════════════════════════

    private void aiCleanup(FactionState fs) {
        if (fs.getUnits() == null) return;
        fs.setUnits(fs.getUnits().stream()
                .filter(u -> !"annihilated".equals(u.getStatus()) && !"surrendered".equals(u.getStatus()))
                .collect(Collectors.toList()));
    }

    // ═══════════════════════════════════════════ 移动 ═══════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private void aiMoveUnits(GameState state, FactionState fs, String fid, AiPersonality pers) {
        List<Unit> units = fs.getUnits();
        List<String> territories = fs.getTerritories();
        if (units == null || territories == null || territories.isEmpty()) return;

        Set<String> enemyBorders = findBorderNames(state, fid, territories);
        if (enemyBorders.isEmpty()) return;

        for (Unit u : units) {
            if ("routed".equals(u.getStatus()) || "annihilated".equals(u.getStatus()) || "fighting".equals(u.getStatus()))
                continue;
            if (u.getMovePath() != null && !u.getMovePath().isEmpty()) continue;

            String pos = u.getPosition();
            Province pdata = engine.getProvince(pos);
            if (pdata == null) continue;
            if (enemyBorders.contains(pdata.getName())) continue; // 已在边境

            // 防御型：不主动前出
            if (territories.contains(pdata.getName()) && pers.aggression() < 0.25 && rng.nextDouble() < 0.5) continue;

            String target = findNearestBorder(pos, enemyBorders);
            if (target != null && !target.equals(pos)) {
                Object[] distResult = engine.getDistance(pos, target);
                if (distResult[0] != null) {
                    List<String> path = (List<String>) distResult[1];
                    if (path != null && path.size() > 1) {
                        u.setPosition(path.get(1));
                        if (path.size() > 2) {
                            u.setMovePath(new ArrayList<>(path.subList(2, path.size())));
                            u.setMoveTarget(target);
                        }
                    }
                }
            }
        }
    }

    Set<String> findBorderNames(GameState state, String fid, List<String> territories) {
        Set<String> myPids = new HashSet<>();
        for (String t : territories) { String pid = engine.getPidByName(t); if (pid != null) myPids.add(pid); }

        Set<String> enemyPids = new HashSet<>();
        for (var ae : state.getAiFactions().entrySet()) {
            if (ae.getKey().equals(fid) || state.getDefeatedFactions().contains(ae.getKey())) continue;
            FactionState ofs = ae.getValue().getFactionState();
            if (ofs == null || ofs.getTerritories() == null) continue;
            for (String t : ofs.getTerritories()) { String pid = engine.getPidByName(t); if (pid != null) enemyPids.add(pid); }
        }
        // 玩家领土
        for (String t : state.getFactionState().getTerritories()) { String pid = engine.getPidByName(t); if (pid != null) enemyPids.add(pid); }
        // NPC领土
        Map<String, NpcDefinition> npcs = gameData.getHostileNpcs();
        if (npcs != null) {
            for (var ne : npcs.entrySet()) {
                List<String> terrList = ne.getValue().getTerritories();
                if (terrList != null) {
                    for (String t : terrList) { String pid = engine.getPidByName(t); if (pid != null) enemyPids.add(pid); }
                }
            }
        }

        Set<String> border = new HashSet<>();
        for (String myPid : myPids) {
            Province p = engine.getProvince(myPid);
            if (p == null || p.getConnections() == null) continue;
            for (String nb : p.getConnections().keySet()) {
                if (enemyPids.contains(nb)) {
                    Province bd = engine.getProvince(nb);
                    if (bd != null) border.add(bd.getName());
                }
            }
        }
        return border;
    }

    private String findNearestBorder(String currentPos, Set<String> borderNames) {
        Set<String> visited = new HashSet<>();
        visited.add(currentPos);
        Queue<Object[]> queue = new LinkedList<>();
        queue.add(new Object[]{currentPos});

        while (!queue.isEmpty()) {
            Object[] item = queue.poll();
            String pid = (String) item[0];
            Province pdata = engine.getProvince(pid);
            if (pdata == null) continue;
            if (borderNames.contains(pdata.getName()) && !pid.equals(currentPos)) return pid;
            for (String nb : pdata.getConnections().keySet()) {
                if (!visited.contains(nb)) { visited.add(nb); queue.add(new Object[]{nb}); }
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════ 发动战役 ═══════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private List<String> aiLaunchCampaigns(GameState state, FactionState fs, String fid, AiPersonality pers) {
        List<String> results = new ArrayList<>();
        boolean expand = pers.expand();
        if (!expand) return results;

        double expandChance = pers.expandChance();
        if (rng.nextDouble() > expandChance) return results;

        List<Unit> readyUnits = fs.getUnits().stream()
                .filter(u -> "ready".equals(u.getStatus())).collect(Collectors.toList());
        if (readyUnits.isEmpty()) return results;

        List<Map<String, Object>> targets = findAiTargets(state, fid, readyUnits, fs.getTerritories());
        if (targets.isEmpty()) return results;

        targets.sort(Comparator.comparingDouble(t -> ((Number) t.get("defense_power")).doubleValue()));

        for (Map<String, Object> target : targets.subList(0, Math.min(1, targets.size()))) {
            List<Unit> availUnits = (List<Unit>) target.get("available_units");
            double ourPower = availUnits.stream().mapToDouble(u -> u.getAttack() + u.getDefense()).sum();
            double defPower = Math.max(1, ((Number) target.get("defense_power")).doubleValue());
            double minRatio = pers.minAttackRatio();
            if (ourPower < defPower * minRatio) continue;

            String enemyFid = (String) target.get("enemy_fid");
            // 连败时主动求和
            if (pers.aggression() < 0.6 && ourPower < defPower && rng.nextDouble() < 0.15) {
                if (!engine.hasNonAggression(state, fid, enemyFid)) {
                    state.getNonAggressionPacts().put(enemyFid, 3);
                    results.add("🕊 " + fs.getName() + " 向 " + target.get("enemy_name") + " 请求休战3回合");
                }
            }

            String tactic = pers.defaultTactic(ourPower, defPower);
            String targetPid = (String) target.get("pid");

            // 检查是否已有进行中战役
            boolean alreadyCampaign = state.getActiveCampaigns().stream()
                    .anyMatch(c -> "ongoing".equals(c.getStatus()) && targetPid.equals(c.getProvince()));
            if (alreadyCampaign) continue;

            List<Unit> atkUnits = availUnits.subList(0, Math.min(3, availUnits.size()));
            Campaign camp = createAiCampaign(state, fs, fid, target, atkUnits, tactic);
            if (camp != null) {
                results.add("⚔ " + fs.getName() + " 向 " + target.get("name") + " 发动战役！（" + atkUnits.size() + "支部队 · " + tactic + "）");
            }
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> findAiTargets(GameState state, String fid, List<Unit> units, List<String> territories) {
        List<Map<String, Object>> targets = new ArrayList<>();
        Set<String> playerTerrs = new HashSet<>(state.getFactionState().getTerritories());
        Map<String, List<Unit>> unitByPos = new HashMap<>();
        for (Unit u : units) {
            if (u.getPosition() != null) unitByPos.computeIfAbsent(u.getPosition(), k -> new ArrayList<>()).add(u);
        }

        Set<String> seen = new HashSet<>();
        for (var posEntry : unitByPos.entrySet()) {
            Province pdata = engine.getProvince(posEntry.getKey());
            if (pdata == null || pdata.getConnections() == null) continue;
            for (String nb : pdata.getConnections().keySet()) {
                Province nbData = engine.getProvince(nb);
                if (nbData == null) continue;
                String nbName = nbData.getName();
                if (territories.contains(nbName)) continue;
                if (seen.contains(nbName)) continue;
                seen.add(nbName);

                String enemyFid = null, enemyName = "";
                List<String> enemyTerrs = List.of();
                if (playerTerrs.contains(nbName)) {
                    enemyFid = state.getPlayerFactionId();
                    enemyName = state.getFactionState().getName();
                    enemyTerrs = state.getFactionState().getTerritories();
                } else {
                    for (var ae : state.getAiFactions().entrySet()) {
                        if (ae.getKey().equals(fid) || state.getDefeatedFactions().contains(ae.getKey())) continue;
                        FactionState ofs = ae.getValue().getFactionState();
                        if (ofs != null && ofs.getTerritories() != null && ofs.getTerritories().contains(nbName)) {
                            enemyFid = ae.getKey(); enemyName = ofs.getName();
                            enemyTerrs = ofs.getTerritories(); break;
                        }
                    }
                }
                if (enemyFid == null) continue;

                // 跨区限制：直接用省份区域比较
                var aiFaction = engine.getFaction(fid).orElse(null);
                if (aiFaction != null && nbData.getRegion() != null
                        && !aiFaction.getRegion().equals(nbData.getRegion())) {
                    if (!engine.isRegionUnified(state, fid)) continue;
                }
                // NAP检查
                if (engine.hasNonAggression(state, fid, enemyFid)) continue;

                double defPower = estimateDefensePower(state, enemyFid, enemyTerrs, nb);
                double ourPower = posEntry.getValue().stream().mapToDouble(u -> u.getAttack() + u.getDefense()).sum();
                if (ourPower >= defPower * 0.3) {
                    targets.add(GameUtils.mapOf("pid", nb, "name", nbName, "terrain", nbData.getTerrain(),
                            "enemy_fid", enemyFid, "enemy_name", enemyName, "enemy_territories", enemyTerrs,
                            "available_units", new ArrayList<>(posEntry.getValue()), "defense_power", defPower));
                }
            }
        }
        return targets;
    }

    private double estimateDefensePower(GameState state, String enemyFid, List<String> enemyTerrs, String provincePid) {
        double power = 8;
        FactionState efs;
        if (enemyFid.equals(state.getPlayerFactionId())) {
            efs = state.getFactionState();
        } else {
            var ad = state.getAiFactions().get(enemyFid);
            efs = ad != null ? ad.getFactionState() : null;
        }
        if (efs == null) return power;
        for (Unit u : efs.getActiveUnits()) {
            if ("routed".equals(u.getStatus())) continue;
            Object[] distResult = engine.getDistance(u.getPosition(), provincePid);
            if (distResult[0] != null && ((Number) distResult[0]).intValue() <= 1) {
                power += u.getDefense() + u.getAttack();
            }
        }
        return power;
    }

    @SuppressWarnings("unchecked")
    private Campaign createAiCampaign(GameState state, FactionState fs, String fid,
                                       Map<String, Object> target, List<Unit> atkUnits, String tactic) {
        if (atkUnits.isEmpty()) return null;

        String enemyFid = (String) target.get("enemy_fid");
        FactionState defFs;
        if (enemyFid.equals(state.getPlayerFactionId())) {
            defFs = state.getFactionState();
        } else {
            var ad = state.getAiFactions().get(enemyFid);
            defFs = ad != null ? ad.getFactionState() : null;
        }

        List<Unit> defUnits = new ArrayList<>();
        if (defFs != null && defFs.getUnits() != null) {
            defUnits = defFs.getUnits().stream()
                    .filter(u -> !"routed".equals(u.getStatus()) && !"annihilated".equals(u.getStatus()))
                    .filter(u -> {
                        Object[] dr = engine.getDistance(u.getPosition(), (String) target.get("pid"));
                        return dr[0] != null && ((Number) dr[0]).intValue() <= 1;
                    }).collect(Collectors.toList());
        }
        if (defUnits.isEmpty()) {
            Unit du = new Unit();
            du.setName(target.get("enemy_name") + "守备");
            du.setType("infantry"); du.setAttack(rng.nextInt(5)+4); du.setDefense(rng.nextInt(7)+4);
            du.setMorale(rng.nextInt(26)+30); du.setExperience(rng.nextInt(11)+5);
            du.setPosition((String) target.get("pid")); du.setStrength(rng.nextInt(31)+40);
            du.setMaxStrength(100); du.setStatus("ready");
            defUnits = List.of(du);
        }

        String terrain = (String) target.get("terrain");
        // 攻击方战术
        Map<String, String> attackerTactics = new LinkedHashMap<>();
        for (Unit u : atkUnits) attackerTactics.put(u.getName(), tactic != null ? tactic : "assault");
        // 防守方战术
        Map<String, String> defenderTactics = new LinkedHashMap<>();
        for (Unit u : defUnits) {
            String defTac;
            if ("artillery".equals(u.getType())) defTac = "bombard";
            else if (("森林".equals(terrain) || "丘陵".equals(terrain) || "山地".equals(terrain))
                    && ("infantry".equals(u.getType()) || "engineer".equals(u.getType()))) defTac = "ambush";
            else if ("城市".equals(terrain)) defTac = "fortify";
            else defTac = "fortify";
            defenderTactics.put(u.getName(), defTac);
        }

        Campaign camp = new Campaign();
        camp.setId("camp_" + state.getTurn() + "_" + state.getActiveCampaigns().size());
        camp.setProvince((String) target.get("pid"));
        camp.setProvinceName((String) target.get("name"));
        camp.setTerrain(terrain);
        camp.setAttackerFaction(fid);
        camp.setAttackerName(fs.getName());
        camp.setDefenderFaction(enemyFid);
        camp.setDefenderName((String) target.get("enemy_name"));
        camp.setDefenderType("faction");
        camp.setAttackerUnits(atkUnits.stream().map(Unit::getName).collect(Collectors.toList()));
        camp.setDefenderUnits(defUnits.stream().map(Unit::getName).collect(Collectors.toList()));
        camp.setAttackerTactics(attackerTactics);
        camp.setDefenderTactics(defenderTactics);
        camp.setRound(0); camp.setMaxRounds(4); camp.setStatus("ongoing");
        camp.setProvinceValue(rng.nextInt(6) + 3);
        camp.setAttackerCache(new ArrayList<>(atkUnits));
        camp.setDefenderCache(new ArrayList<>(defUnits));
        camp.setReinforcementQueue(new ArrayList<>());

        if (state.getActiveCampaigns() == null) state.setActiveCampaigns(new ArrayList<>());
        state.getActiveCampaigns().add(camp);

        for (Unit u : atkUnits) { u.setStatus("fighting"); u.setCampaignId(camp.getId()); u.setPosition(camp.getProvince()); }
        return camp;
    }

    // ═══════════════════════════════════════════ AI外交 ═══════════════════════════════════════════

    @SuppressWarnings("unchecked")
    List<String> checkAiDiplomacy(GameState state) {
        String fid = state.getPlayerFactionId();
        String playerRegion = engine.getFaction(fid).map(FactionDefinition::getRegion).orElse("");
        List<String> results = new ArrayList<>();

        Map<String, FactionDefinition> factions = gameData.getFactions();
        for (var entry : factions.entrySet()) {
            String tfid = entry.getKey();
            if (tfid.equals(fid)) continue;
            if (state.getDefeatedFactions().contains(tfid)) continue;
            FactionDefinition tf = entry.getValue();
            if (!playerRegion.equals(tf.getRegion())) continue;

            // 已在战争 → 可能求和
            if (state.getActiveWars().contains(tfid)) {
                if (rng.nextDouble() < 0.15) {
                    results.add("🕊 " + tf.getName() + "遣使求和——可用外交和谈回应");
                }
                continue;
            }

            AiPersonality pers = getAiPersonality(tfid);
            double allianceSeek = pers.allianceSeek();
            if (rng.nextDouble() >= allianceSeek * 0.08) continue;

            int rel = 0;
            var dr = state.getDiplomaticRelations().get(tfid);
            if (dr != null) {
                if ("non_aggression".equals(dr.getPact()) || "alliance".equals(dr.getPact())) continue;
                rel = dr.getScore();
            }
            if (rel < 0) continue;

            if (rng.nextDouble() < 0.5) {
                results.add("🤝 " + tf.getName() + "提议互不侵犯条约——可用外交 [3.1] 回应");
            } else {
                results.add("🤝 " + tf.getName() + "提议贸易协定——可用外交 [3.6] 回应");
            }
        }
        return results;
    }

    // ═══════════════════════════════════════════ 工具 ═══════════════════════════════════════════

    private AiPersonality getAiPersonality(String fid) {
        FactionDefinition faction = gameData.getFactions().get(fid);
        String aiDesc = "";
        if (faction != null) {
            aiDesc = faction.getAiPersonality() != null ? faction.getAiPersonality() : "";
            if (aiDesc.isEmpty()) aiDesc = faction.getAi() != null ? faction.getAi() : "";
        }
        return AiPersonality.infer(aiDesc);
    }

    private String nameToPid(String name) {
        String pid = engine.getPidByName(name);
        if (pid != null) return pid;
        for (var entry : engine.getMapData().getAll().entrySet()) {
            if (entry.getValue().getName().contains(name) || name.contains(entry.getValue().getName()))
                return entry.getKey();
        }
        return "beijing";
    }

}
