package com.qiyuzhulu.service;

import com.qiyuzhulu.model.*;
import com.qiyuzhulu.repo.GameDataRepo;
import com.qiyuzhulu.repo.MapDataRepo;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 核心游戏引擎。对应 Python 的 qiyu_core.py。
 */
@Service
public class GameEngine {

    private final GameDataRepo gameData;
    private final MapDataRepo mapData;

    /** 区域ID列表（有序） */
    public static final List<String> REGION_IDS = List.of(
            "northeast", "huabei", "southwest", "southeast", "lingnan", "nanyang", "xibei");

    /** 属性中文名 */
    public static final Map<String, String> STAT_NAMES = Map.of(
            "industry", "工业", "agriculture", "农业", "military", "军事",
            "economy", "经济", "ideology", "思想", "diplomacy", "外交",
            "naval_power", "海军");

    /** 属性emoji */
    public static final Map<String, String> STAT_EMOJI = Map.of(
            "industry", "🏭", "agriculture", "🌾", "military", "⚔",
            "economy", "💰", "ideology", "📖", "diplomacy", "🌐",
            "naval_power", "⚓");

    /** 兵种定义 */
    public static final Map<String, Map<String, Object>> UNIT_TYPES = new LinkedHashMap<>();
    static {
        UNIT_TYPES.put("infantry",  Map.of("name","步兵","icon","🗡","atk_bonus",3,"def_bonus",3,"cost",8,"turns",2,"military_gain",5,"maintenance_cost",2,"suffix","号"));
        UNIT_TYPES.put("cavalry",   Map.of("name","骑兵","icon","🐎","atk_bonus",6,"def_bonus",1,"cost",12,"turns",3,"military_gain",7,"maintenance_cost",3,"suffix","号"));
        UNIT_TYPES.put("artillery", Map.of("name","炮兵","icon","💣","atk_bonus",9,"def_bonus",1,"cost",18,"turns",4,"military_gain",10,"maintenance_cost",4,"suffix","号"));
        UNIT_TYPES.put("engineer",  Map.of("name","工兵","icon","🔧","atk_bonus",2,"def_bonus",6,"cost",10,"turns",3,"military_gain",6,"maintenance_cost",3,"suffix","号"));
        UNIT_TYPES.put("naval",     Map.of("name","海军舰艇","icon","⚓","atk_bonus",8,"def_bonus",4,"cost",20,"turns",5,"military_gain",10,"naval_gain",8,"maintenance_cost",5,"suffix","舰艇"));
    }

    /** 战术定义 */
    public static final Map<String, Map<String, Object>> TACTICS = new LinkedHashMap<>();
    static {
        TACTICS.put("assault",    Map.of("name","强攻","icon","⚔","atk_mult",1.4,"def_mult",0.6,"loss_mult",1.5));
        TACTICS.put("flanking",   Map.of("name","迂回","icon","🏃","atk_mult",1.1,"def_mult",0.8,"loss_mult",0.9));
        TACTICS.put("bombard",    Map.of("name","炮击","icon","💣","atk_mult",0.6,"def_mult",0.3,"loss_mult",0.3));
        TACTICS.put("ambush",     Map.of("name","设伏","icon","🌲","atk_mult",1.8,"def_mult",1.5,"loss_mult",0.5));
        TACTICS.put("fortify",    Map.of("name","设防","icon","🏰","atk_mult",0.4,"def_mult",2.0,"loss_mult",0.7));
        TACTICS.put("night_raid", Map.of("name","夜袭","icon","🌙","atk_mult",1.3,"def_mult",0.5,"loss_mult",1.1));
        TACTICS.put("probe",      Map.of("name","试探","icon","🔍","atk_mult",0.5,"def_mult",1.2,"loss_mult",0.4));
        TACTICS.put("all_out",    Map.of("name","总攻","icon","🔥","atk_mult",2.0,"def_mult",0.0,"loss_mult",2.5));
    }

    /** 区域邻接表 */
    public static final Map<String, Map<String, Object>> MEMORIALS = new LinkedHashMap<>();
    static {
        putM("northeast", Map.of("name","盛京将军 赵尔巽","title","日俄觊觎边境，请拨军费加强边防","desc","日俄自旅顺战后各踞南满北满，铁丝网已划至奉天城外三十里。臣请拨国帑四十万两，于长春—奉天—锦州一线修筑炮台兵站。若不设防，不出三年辽东恐非我有。","region","northeast","cost",40));
        putM("huabei", Map.of("name","直隶总督 袁世凯","title","黄河汛期将至，请拨银修缮堤防","desc","黄河自铜瓦厢改道已逾半纪，豫鲁两省年年漫决。本年春雨过量，河堤报险三十七处。请拨帑银三十五万两修堤疏漕，并可保京师至德州铁路路基。","region","huabei","cost",35));
        putM("southwest", Map.of("name","云贵总督 锡良","title","边陲土司叛乱，请准改土归流","desc","川滇黔交界土司七十二寨，自光绪末已抗粮抗税十二载。法人自滇越铁路北窥，暗输军火予土司。臣请行改土归流，设县置吏，但需饷银三十万两及练勇八千。","region","southwest","cost",30));
        putM("southeast", Map.of("name","两江总督 张人骏","title","革命党煽动商埠，请派兵弹压","desc","上海租界革命报纸已增至九种，同盟会密使自东京南洋潜入，联络会党、策反新军。去岁徐锡麟案震惊朝野。臣请密派缇骑赴沪宁汉三镇搜捕党人，需密费三十五万两。","region","southeast","cost",35));
        putM("lingnan", Map.of("name","两广总督 岑春煊","title","法属越境侵扰，请编新军固防","desc","法属安南驻军去岁越境十二次，测绘广西边境地图。琼崖海面法舰游弋不断。臣请编练新式边防军三协，购德国快炮二十四门，需饷三十万两。","region","lingnan","cost",30));
        putM("nanyang", Map.of("name","闽浙总督 松寿","title","海盗猖獗侨民告急，请扩水师","desc","南洋侨商禀报，马六甲至吕宋海面海盗猖獗，去年劫掠华商货船六十一艘。英荷海军以护航为名扩大巡弋。臣请拨银三十五万两购置快轮十艘，编练南洋水师护侨营。","region","nanyang","cost",35));
        putM("xibei", Map.of("name","陕甘总督 升允","title","沙俄渗透边疆，请设行省治理","desc","俄国自日俄战后全力东进，伊犁—喀什噶尔一线俄商队实为测绘队，已绘新疆详图七十六幅。外蒙王公暗通俄使。臣请筹设新疆行省衙门于迪化，调甘军两协驻防，需帑三十万两。","region","xibei","cost",30));
        putM("flood", Map.of("name","河道总督","title","黄河决口豫鲁告急，请拨银二十万两赈灾","desc","黄河铜瓦厢决口，洪水漫灌豫东鲁西十七县。数百万灾民流离失所，饥民已开始冲击县衙。请即刻拨帑银二十万两赈灾安民。","region","","cost",20));
        putM("revolt", Map.of("name","军机处","title","四川保路运动演变为武装暴动，请调新军弹压","desc","成都保路同志会已聚众十万，川督来电称省城危急。暴民已控制多条铁路线。请调湖北新军两协入川弹压，需饷二十五万两。","region","","cost",25));
        putM("famine", Map.of("name","户部尚书","title","陕甘连年大旱，请开仓放粮并免赋税","desc","陕甘总督急报：去岁至今滴雨未下，麦收不足三成。饥民已食树皮草根。请开仓放粮三十万石并免今年赋税，需帑二十万两。","region","","cost",20));
        putM("foreign", Map.of("name","总理衙门","title","英法公使联名抗议排外运动，要求惩办拳民","desc","英法两国公使今日联名照会：华北多地发生教案，传教士被逐，教堂被毁。要求朝廷严惩肇事者并赔款二十五万两，否则将派军舰示威。","region","","cost",25));
        putM("treasury", Map.of("name","户部侍郎","title","户部奏报库银不足五十万两，请准发行国债","desc","户部急奏：库银不足五十万两，各省应解京饷逾期未至。请准向汇丰、德华等外资银行借款，以海关关税为抵押，可借三十万两暂渡难关。","region","","cost",-30));
        putM("warlord", Map.of("name","军机处","title","地方督军拥兵自重拒绝调防，请旨处置","desc","鄂督急电：鄂北新军一协拒不听调，协统称兵士不愿离乡。实则该协统已与当地士绅暗通，拥兵自重。请旨：是剿是抚？需饷十五万两。","region","","cost",15));
        putM("japan_loan", Map.of("name","日本公使 林权助","title","日本愿提供紧急贷款，以铁路利权为抵押","desc","日本公使林权助求见：日本政府愿提供三十万两白银紧急贷款，年息五厘，以东北两条铁路的运营权为担保。此事关系国体，请陛下圣断。","region","","cost",-30));
        putM("russia_loan", Map.of("name","俄国公使 廓索维慈","title","俄国愿提供紧急贷款，以矿产开采权为抵押","desc","俄国公使廓索维慈求见：俄罗斯帝国愿提供三十万两白银贷款，以新疆三处矿山的开采权为担保。俄方保证不干涉内政。","region","","cost",-30));
        putM("britain_loan", Map.of("name","英国公使 朱尔典","title","英国愿提供紧急贷款，以海关关税为抵押","desc","英国公使朱尔典爵士求见：大英帝国愿提供三十万两白银贷款，以海关关税收入为担保，年息四厘。这是目前最优惠的条件。","region","","cost",-30));
        putM("france_loan", Map.of("name","法国公使 巴斯特","title","法国愿提供紧急贷款，以铁路筑路权为抵押","desc","法国公使巴斯特求见：法兰西共和国愿提供三十万两白银贷款，以滇越铁路延长线的筑路权为担保。此路通，则中国与法属安南连为一体。","region","","cost",-30));
        putM("usa_loan", Map.of("name","美国公使 芮恩施","title","美国愿提供无抵押紧急贷款，以表友好","desc","美国公使芮恩施求见：美利坚合众国愿提供三十万两白银贷款，无需抵押，仅希望中国对美商开放更多口岸。这是最没有附加条件的援助。","region","","cost",-30));
    }
    private static void putM(String k, Map<String, Object> v) { MEMORIALS.put(k, v); }


    public static final Map<String, List<String>> REGION_ADJACENCY = Map.of(
            "northeast", List.of("huabei"),
            "huabei", List.of("northeast","southeast","xibei"),
            "southeast", List.of("huabei","lingnan"),
            "lingnan", List.of("southeast","southwest","nanyang"),
            "southwest", List.of("lingnan","xibei"),
            "xibei", List.of("huabei","southwest"),
            "nanyang", List.of("southeast","lingnan"));

    public static final Map<String, String> FOREIGN_POWERS = Map.of(
            "northeast","日本","huabei","日本","xibei","俄国",
            "southwest","英国","lingnan","法国","nanyang","英国","southeast","美国");

    public static final Map<String, String> REGION_NAMES = Map.of(
            "northeast","东北","huabei","华北","southeast","东南","southwest","西南",
            "lingnan","岭南","nanyang","南洋","xibei","西北");

    public static final Map<Integer, String> PHASE_NAMES = Map.of(
            1,"帝国余晖",2,"帝国大崩溃",3,"区域统一战",4,"七强并立",5,"天下归一");

    public GameEngine(GameDataRepo gameData, MapDataRepo mapData) {
        this.gameData = gameData;
        this.mapData = mapData;
    }

    public MapDataRepo getMapData() { return mapData; }
    public GameDataRepo getGameData() { return gameData; }

    // ═══════════════════════════════════════════ 工具方法 ═══════════════════════════════════════════

    /** 限定值在 [lo, hi] 范围内 */
    public static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    public static int clamp(int v) { return clamp(v, 0, 100); }

    /** 获取省份数据 */
    public Province getProvince(String pid) {
        return mapData.get(pid);
    }

    /** 根据省份名获取PID */
    public String getPidByName(String name) {
        return mapData.getPidByName(name);
    }

    /** 将部队位置（领土名或PID）统一转为PID */
    public String resolvePositionToPid(String pos) {
        if (pos == null) return "beijing";
        String pid = getPidByName(pos);
        return pid != null ? pid : pos;
    }

    /** 获取势力定义 */
    public Optional<FactionDefinition> getFaction(String fid) {
        return gameData.getFaction(fid);
    }

    /** 获取当前玩家的势力定义 */
    public FactionDefinition getPlayerFaction(GameState state) {
        return getFaction(state.getPlayerFactionId()).orElse(null);
    }

    /** 获取玩家所在区域 */
    public String getPlayerRegion(GameState state) {
        FactionDefinition pf = getPlayerFaction(state);
        return pf != null ? pf.getRegion() : "";
    }

    // ═══════════════════════════════════════════ 自动占领 ═══════════════════════════════════════════

    /** 部队到达地块时自动占领无主地（玩家专用）。返回消息或null。 */
    public String autoClaimArrival(GameState state, Unit unit, String posPid) {
        return autoClaimArrival(state, state.getPlayerFactionId(), state.getFactionState(), unit, posPid);
    }

    /** 部队到达地块时自动占领无主地（通用版）。返回消息或null。 */
    @SuppressWarnings("unchecked")
    public String autoClaimArrival(GameState state, String factionId, FactionState fs, Unit unit, String posPid) {
        Province pos = getProvince(posPid);
        if (pos == null || !pos.isClaimable()) return null;
        String pname = pos.getName();
        if (pname == null || pname.isEmpty()) return null;

        // 跨区限制
        FactionDefinition pf = getFaction(factionId).orElse(null);
        if (pf != null) {
            String provRegion = pos.getRegion();
            if (provRegion != null && !provRegion.equals(pf.getRegion())) {
                if (!isRegionUnified(state, factionId)) return null;
            }
        }

        List<String> myTerritories = fs.getTerritories();
        if (myTerritories.contains(pname)) return null;

        // 已被AI占领（动态state优先，静态AiFactionData回退）
        for (var ae : state.getAiFactions().entrySet()) {
            if (state.getDefeatedFactions().contains(ae.getKey())) continue;
            FactionState afs = ae.getValue().getFactionState();
            if (afs != null && afs.getTerritories() != null && afs.getTerritories().contains(pname))
                return null;
            // 回退：AiFactionData静态领土（AI未初始化时）
            List<String> staticTer = ae.getValue().getTerritories();
            if (staticTer != null && staticTer.contains(pname))
                return null;
        }

        // 在未击败势力的静态领土中
        Set<String> allStatic = new HashSet<>();
        for (var fe : gameData.getFactions().entrySet()) {
            if (fe.getKey().equals(state.getPlayerFactionId())) continue;
            if (state.getDefeatedFactions().contains(fe.getKey())) continue;
            allStatic.addAll(fe.getValue().getInitialTerritory());
        }
        for (var ne : gameData.getHostileNpcs().entrySet()) {
            if (state.getDefeatedFactions().contains(ne.getKey())) continue;
            List<String> t = ne.getValue().getTerritories();
            if (t != null) allStatic.addAll(t);
        }
        if (allStatic.contains(pname)) return null;

        // 占领！
        if (fs.getTerritories() == null)
            fs.setTerritories(new ArrayList<>());
        fs.getTerritories().add(pname);

        // 检查是否消灭了NPC势力（领土全失→击败，使用合并后的hostile_npcs）
        String defeatedNpc = null;
        for (var he : gameData.getHostileNpcs().entrySet()) {
            if (state.getDefeatedFactions().contains(he.getKey())) continue;
            List<String> hTerrs = he.getValue().getTerritories();
            if (hTerrs == null || hTerrs.isEmpty()) continue;
            boolean allTaken = true;
            for (String ht : hTerrs) {
                if (!myTerritories.contains(ht) && !pname.equals(ht)) { allTaken = false; break; }
            }
            if (allTaken) { defeatedNpc = he.getKey(); break; }
        }
        if (defeatedNpc != null) {
            state.getDefeatedFactions().add(defeatedNpc);
            return "🏴 占领 " + pname + "，" + getNpcName(defeatedNpc) + " 覆灭！";
        }
        return "🏴 占领 " + pname;
    }

    // ═══════════════════════════════════════════ 意识形态 & 威胁 ═══════════════════════════════════════════

    private static final Map<String, Map<String, Integer>> IDEOLOGY_CONFLICT = new LinkedHashMap<>();
    static {
        put("共产主义","军阀独裁",40); put("共产主义","君主立宪",35);
        put("共产党革命","军阀独裁",40); put("共产党革命","军事独裁",40);
        put("共产党革命","旧官僚独裁",35); put("左翼土地革命","军阀独裁",35);
        put("左翼土地革命","财阀民主",30); put("军阀独裁","民主联邦",25);
        put("军阀独裁","自由主义宪政",25); put("军事独裁","共产主义",40);
        put("地方军阀","共产主义",30); put("国家主义","共产主义",35);
        put("三民主义共和","共产主义",25); put("三民主义共和","军阀独裁",20);
        put("海权军国","共产主义",30); put("华人民族主义","满蒙民族主义",20);
        put("泛突厥民族主义","华人民族主义",25); put("亲英自强","华人民族主义",20);
    }
    private static void put(String a, String b, int v) {
        IDEOLOGY_CONFLICT.put(a, new HashMap<>(Map.of(b, v)));
        IDEOLOGY_CONFLICT.put(b, new HashMap<>(Map.of(a, v)));
    }

    public int ideologyDistance(String ideo1, String ideo2) {
        if (ideo1 == null || ideo2 == null) return 15;
        if (ideo1.equals(ideo2)) return 0;
        var m = IDEOLOGY_CONFLICT.get(ideo1);
        if (m != null && m.containsKey(ideo2)) return m.get(ideo2);
        return 15;
    }

    public int calcThreat(GameState state, String targetFid) {
        int threat = 0;
        String fid = state.getPlayerFactionId();
        FactionDefinition pf = getPlayerFaction(state);
        FactionDefinition tf = getFaction(targetFid).orElse(null);
        if (pf == null || tf == null) return 30;

        // 1. 意识形态冲突 (0-25)
        threat += Math.min(25, ideologyDistance(pf.getIdeology(), tf.getIdeology()));

        // 2. 领土竞争 (0-30)
        if (pf.getRegion().equals(tf.getRegion())) threat += 30;
        else {
            if (REGION_ADJACENCY.getOrDefault(pf.getRegion(), List.of()).contains(tf.getRegion())) threat += 15;
        }

        // 3. 战争/关系历史 (0-20)
        if (state.getActiveWars().contains(targetFid)) threat += 20;
        else {
            var dr = state.getDiplomaticRelations().get(targetFid);
            if (dr != null && dr.getScore() < -20) threat += 10;
        }

        // 4. 军力对比 (0-25)
        int myMil = state.getFactionState().getStats().getMilitary();
        int tMil = tf.getStats().getMilitary();
        if (tMil > myMil * 1.5) threat += 25;
        else if (tMil > myMil * 1.2) threat += 15;
        else if (tMil > myMil) threat += 5;

        return clamp(threat, 0, 100);
    }

    // ═══════════════════════════════════════════ AI投降系统 ═══════════════════════════════════════════

    /** 检查是否有AI势力应投降。返回 [{fid, attacker_fid}, ...] */
    public List<Map<String, String>> checkAiSurrender(GameState state) {
        List<Map<String, String>> surrenders = new ArrayList<>();
        for (var entry : state.getAiFactions().entrySet()) {
            String fid = entry.getKey();
            if (state.getDefeatedFactions().contains(fid)) continue;
            FactionState fs = entry.getValue().getFactionState();
            if (fs == null) continue;

            int mil = fs.getStats().getMilitary();
            List<String> terrs = fs.getTerritories();
            Map<String, Integer> army = fs.getArmy();
            int totalArmy = army != null ? army.values().stream().mapToInt(Integer::intValue).sum() : 0;

            // 无力抵抗条件
            boolean weak = (mil < 20 && (terrs == null || terrs.size() <= 1))
                    || (totalArmy < 20 && (terrs == null || terrs.size() <= 1));
            if (!weak) continue;

            // 找到正在进攻该势力的战役
            String attacker = null;
            for (Campaign c : state.getActiveCampaigns()) {
                if ("ongoing".equals(c.getStatus()) && fid.equals(c.getDefenderFaction())) {
                    attacker = c.getAttackerFaction();
                    break;
                }
            }
            if (attacker == null) continue;

            surrenders.add(Map.of("fid", fid, "attacker_fid", attacker));
        }
        return surrenders;
    }

    /** 执行投降：领地归进攻方，标记覆灭 */
    public Map<String, Object> executeSurrender(GameState state, String fid, String attackerFid) {
        var aiData = state.getAiFactions().get(fid);
        if (aiData == null) return null;
        FactionState fs = aiData.getFactionState();
        if (fs == null) return null;

        String name = fs.getName() != null ? fs.getName() : fid;
        List<String> territories = new ArrayList<>(fs.getTerritories() != null ? fs.getTerritories() : List.of());

        String winnerFid = attackerFid != null ? attackerFid : state.getPlayerFactionId();
        FactionState winnerFs = getFactionState(state, winnerFid);
        String winnerName = winnerFs != null && winnerFs.getName() != null ? winnerFs.getName() : winnerFid;

        // 转移领土
        for (String t : territories) {
            String pid = getPidByName(t);
            if (pid != null) {
                Province p = getProvince(pid);
                if (p != null && !p.isClaimable()) continue;
            }
            if (winnerFs.getTerritories() == null) winnerFs.setTerritories(new ArrayList<>());
            if (!winnerFs.getTerritories().contains(t)) winnerFs.getTerritories().add(t);
        }

        // 标记覆灭
        if (state.getDefeatedFactions() == null) state.setDefeatedFactions(new ArrayList<>());
        state.getDefeatedFactions().add(fid);
        fs.setTerritories(new ArrayList<>());
        if (fs.getUnits() != null) fs.getUnits().clear();

        // 事件
        String text = "🏳 " + name + " 降伏于" + winnerName + "——兵力耗尽，举旗归顺。";
        if (state.getDefeatEvents() == null) state.setDefeatEvents(new ArrayList<>());
        state.getDefeatEvents().add(GameUtils.mapOf(
                "name", name, "turn", state.getTurn(), "text", text,
                "eliminated_faction", name, "eliminator_faction", winnerName,
                "eliminator_fid", winnerFid, "eliminated_fid", fid));

        return GameUtils.mapOf("faction_id", fid, "faction_name", name,
                "territories", territories, "winner_name", winnerName);
    }

    // ═══════════════════════════════════════════ 补给系统 ═══════════════════════════════════════════

    /**
     * 计算部队补给状态。
     * @return ["supplied"|"strained"|"cut_off"|"isolated", distance, nearestCityName]
     */
    public Object[] calcSupply(String unitPosition, List<String> friendlyTerritories) {
        if (friendlyTerritories == null || friendlyTerritories.isEmpty())
            return new Object[]{"supplied", 0, "无己方领地"};

        // 找所有己方城池
        List<String> friendlyCities = new ArrayList<>();
        for (var entry : mapData.getAll().entrySet()) {
            Province p = entry.getValue();
            if ("city".equals(p.getType()) && friendlyTerritories.contains(p.getName())) {
                friendlyCities.add(entry.getKey());
            }
        }
        if (friendlyCities.isEmpty()) return new Object[]{"supplied", 0, "无己方城池"};

        // BFS找最近城池
        int minDist = Integer.MAX_VALUE;
        String nearestCity = null;
        for (String cityPid : friendlyCities) {
            Object[] result = getDistance(unitPosition, cityPid);
            if (result[0] != null) {
                int dist = ((Number) result[0]).intValue();
                if (dist < minDist) {
                    minDist = dist;
                    Province cp = getProvince(cityPid);
                    nearestCity = cp != null ? cp.getName() : cityPid;
                }
            }
        }
        if (minDist == Integer.MAX_VALUE) return new Object[]{"isolated", 999, "无连接"};

        // 铁路加速
        if (hasRailway(unitPosition)) minDist = Math.max(1, minDist / 2);

        // 友方领地内改善
        Province up = getProvince(unitPosition);
        if (up != null && friendlyTerritories.contains(up.getName())) minDist = Math.max(0, minDist - 1);

        String level;
        if (minDist <= 1) level = "supplied";
        else if (minDist <= 3) level = "strained";
        else if (minDist <= 5) level = "cut_off";
        else level = "isolated";

        return new Object[]{level, minDist, nearestCity != null ? nearestCity : "?"};
    }

    /** 获取国家精神 */
    public NationalSpirit getNationalSpirit(FactionDefinition faction) {
        if (faction.getNationalSpirit() != null && faction.getNationalSpirit().getName() != null) {
            return faction.getNationalSpirit();
        }
        // 默认按意识形态匹配
        return getDefaultSpirit(faction.getIdeology());
    }

    private NationalSpirit getDefaultSpirit(String ideology) {
        Map<String, NationalSpirit> defaults = new LinkedHashMap<>();
        defaults.put("军阀独裁", ns("强人政治","一人号令，三军听命。",Map.of("military",5,"ideology",-3)));
        defaults.put("共产主义", ns("先锋队革命","彻底的革命，全新的世界。",Map.of("ideology",10,"economy",-5)));
        defaults.put("三民主义共和", ns("三民主义","民族、民权、民生。",Map.of("ideology",5,"economy",3,"diplomacy",2)));
        return defaults.getOrDefault(ideology, new NationalSpirit(){{setName("暂无国魂");setDesc("乱世中尚未形成独特的精神力量。");setEffects(Map.of());}});
    }

    private static NationalSpirit ns(String name, String desc, Map<String,Integer> effects) {
        NationalSpirit s = new NationalSpirit();
        s.setName(name); s.setDesc(desc); s.setEffects(effects);
        return s;
    }

    // ═══════════════════════════════════════════ 地图系统 ═══════════════════════════════════════════

    /** BFS计算两省份最短距离 */
    public Object[] getDistance(String fromPid, String toPid) {
        if (fromPid == null || toPid == null) return new Object[]{null, null};
        if (fromPid.equals(toPid)) return new Object[]{0, List.of(fromPid)};

        Set<String> visited = new HashSet<>();
        visited.add(fromPid);
        Queue<Object[]> queue = new LinkedList<>();
        queue.add(new Object[]{fromPid, new ArrayList<>(List.of(fromPid))});

        while (!queue.isEmpty()) {
            Object[] item = queue.poll();
            String current = (String) item[0];
            @SuppressWarnings("unchecked")
            List<String> path = (List<String>) item[1];
            Province p = getProvince(current);
            if (p == null || p.getConnections() == null) continue;
            for (String neighbor : p.getConnections().keySet()) {
                if (neighbor.equals(toPid)) {
                    List<String> fullPath = new ArrayList<>(path);
                    fullPath.add(neighbor);
                    return new Object[]{fullPath.size() - 1, fullPath};
                }
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(neighbor);
                    queue.add(new Object[]{neighbor, newPath});
                }
            }
        }
        return new Object[]{null, null}; // unreachable
    }

    /** 省份是否有铁路 */
    public boolean hasRailway(String pid) {
        Province p = getProvince(pid);
        return p != null && p.getRailway() > 0;
    }

    // ═══════════════════════════════════════════ 军队系统 ═══════════════════════════════════════════

    /** 获取按位置分组的部队部署 */
    public Map<String, List<Unit>> listArmyPositions(List<Unit> units) {
        Map<String, List<Unit>> result = new LinkedHashMap<>();
        if (units == null) return result;
        for (Unit u : units) {
            String pos = u.getPosition();
            if (pos != null && !pos.isEmpty()) {
                result.computeIfAbsent(pos, k -> new ArrayList<>()).add(u);
            }
        }
        return result;
    }

    /** 汇总领土经济 */
    public Map<String, Object> aggregateTerritoryEconomy(FactionState fs) {
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("industry", 0);
        totals.put("agriculture", 0);
        totals.put("commerce", 0);
        totals.put("railway_provinces", 0);
        totals.put("port_provinces", 0);
        totals.put("population", 0);
        totals.put("resources", new HashMap<String, Integer>());
        totals.put("province_count", 0);

        List<String> territories = fs.getTerritories();
        if (territories == null) return totals;

        Map<String, Map<String, Integer>> buildings = fs.getProvinceBuildings();
        if (buildings == null) buildings = Map.of();

        for (String tname : territories) {
            String pid = getPidByName(tname);
            if (pid == null) continue;
            Province p = getProvince(pid);
            if (p == null) continue;

            int ind = p.getIndustry();
            int agr = p.getAgriculture();
            Map<String, Integer> bld = buildings.getOrDefault(pid, Map.of());
            ind = Math.min(10, ind + bld.getOrDefault("factory", 0) * 2);
            agr = Math.min(10, agr + bld.getOrDefault("irrigation", 0) * 2);

            totals.put("industry", (int) totals.get("industry") + ind);
            totals.put("agriculture", (int) totals.get("agriculture") + agr);
            totals.put("commerce", (int) totals.get("commerce") + p.getCommerce());
            if (p.getRailway() > 0) totals.put("railway_provinces", (int) totals.get("railway_provinces") + 1);
            if (p.getPort() > 0) totals.put("port_provinces", (int) totals.get("port_provinces") + 1);
            totals.put("population", (int) totals.get("population") + p.getPopulation());
            @SuppressWarnings("unchecked")
            Map<String, Integer> resMap = (Map<String, Integer>) totals.get("resources");
            for (String r : p.getResources()) {
                resMap.merge(r, 1, Integer::sum);
            }
            totals.put("province_count", (int) totals.get("province_count") + 1);
        }
        return totals;
    }

    /** 计算城市有效农业（铁路权重输送） */
    public int calcCitySupplyAgriculture(String cityPid, List<String> controlledNames,
                                          Map<String, Map<String, Integer>> buildings) {
        Province cityP = getProvince(cityPid);
        if (cityP == null) return 0;
        if (buildings == null) buildings = Map.of();

        int ownAgri = cityP.getAgriculture() + buildings.getOrDefault(cityPid, Map.of()).getOrDefault("irrigation", 0) * 2;
        ownAgri = Math.min(10, ownAgri);
        Map<Integer, Double> railWeights = Map.of(0, 0.3, 1, 0.6, 2, 0.8, 3, 1.0);
        double adjacentAgri = 0.0;

        if (cityP.getConnections() != null) {
            for (String adjPid : cityP.getConnections().keySet()) {
                Province adjP = getProvince(adjPid);
                if (adjP == null) continue;
                if (!controlledNames.contains(adjP.getName())) continue;
                if (!"rural".equals(adjP.getType())) continue;
                int rw = adjP.getRailway();
                double weight = railWeights.getOrDefault(rw, 0.3);
                int baseAgri = adjP.getAgriculture() + buildings.getOrDefault(adjPid, Map.of()).getOrDefault("irrigation", 0) * 2;
                adjacentAgri += Math.min(10, baseAgri) * weight;
            }
        }
        return (int)(ownAgri + adjacentAgri);
    }

    /** 计算单支部队维持费 */
    public int calcUnitMaintenance(Unit unit, FactionState fs) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ut = (Map<String, Object>) UNIT_TYPES.getOrDefault(unit.getType(), UNIT_TYPES.get("infantry"));
        int base = ut.containsKey("maintenance_cost") ? ((Number) ut.get("maintenance_cost")).intValue() : 2;

        String pos = unit.getPosition();
        String posPid = getPidByName(pos);
        if (posPid == null) return base;

        Province p = getProvince(posPid);
        if (p == null) return base;

        Map<String, Map<String, Integer>> bld = fs.getProvinceBuildings();
        if (bld == null) bld = Map.of();

        if ("rural".equals(p.getType())) {
            int bonusAgri = bld.getOrDefault(posPid, Map.of()).getOrDefault("irrigation", 0) * 2;
            int agri = Math.max(1, Math.min(10, p.getAgriculture() + bonusAgri));
            return Math.max(1, (int) Math.round((double) base / agri));
        } else {
            int effectiveAgri = Math.max(1, calcCitySupplyAgriculture(posPid, fs.getTerritories(), bld));
            return Math.max(1, (int) Math.round((double) base / effectiveAgri));
        }
    }

    /** 计算势力总维持费 */
    public int calcTotalMaintenance(FactionState fs) {
        if (fs.getUnits() == null) return 0;
        return fs.getUnits().stream()
                .filter(Unit::isActive)
                .mapToInt(u -> calcUnitMaintenance(u, fs))
                .sum();
    }

    /** 计算回合收入（税率驱动） */
    public int calcIncome(FactionState fs) {
        Map<String, Object> eco = aggregateTerritoryEconomy(fs);
        int commerce = (int) eco.getOrDefault("commerce", 0);
        int agriculture = (int) eco.getOrDefault("agriculture", 0);
        int industry = (int) eco.getOrDefault("industry", 0);
        int economy = fs.getStats().getEconomy();

        // 税基 = 领土产出 × 系数
        double agriBase = agriculture * 0.8;
        double commBase = commerce * 1.5;

        // 实际税收 = 税基 × 税率%
        double agriTax = agriBase * (fs.getAgriTaxRate() / 100.0);
        double commTax = commBase * (fs.getCommerceTaxRate() / 100.0);

        return (int)(agriTax + commTax) + economy / 8 + industry / 10;
    }

    // ═══════════════════════════════════════════ 状态初始化 ═══════════════════════════════════════════

    /** 创建新游戏状态 */
    public GameState newState(String factionId, List<String> policies) {
        FactionDefinition faction = gameData.getFaction(factionId)
                .orElseThrow(() -> new IllegalArgumentException("势力不存在: " + factionId));

        GameState state = new GameState();
        boolean isQuickStart = (policies != null && !policies.isEmpty());
        state.setVersion(7);
        state.setPhase(isQuickStart ? 2 : 1); // 快速开局跳过帝国阶段
        state.setTurn(0);
        state.setGameDate("1910-03");
        state.setPlayerFactionId(factionId);
        state.setPhase1Policies(policies != null ? policies : List.of());
        state.setActionPoints(3);
        state.setApMax(3);

        // 势力状态
        FactionState fs = new FactionState();
        fs.setName(isQuickStart ? faction.getName() : "大清帝国");
        fs.setStats(faction.getStats().copy());
        fs.setTreasury(200 - (faction.getStats().getEconomy() / 3)); // 帝国余财
        fs.setPopulationSupport(35); // Phase1 民心疲敝
        fs.setCorruption(40);         // Phase1 腐败蔓延
        fs.setMilitaryTech(1);
        fs.setCapital(faction.getInitialTerritory().isEmpty() ? "" : faction.getInitialTerritory().get(0));
        if (isQuickStart) {
            fs.setTerritories(new ArrayList<>(faction.getInitialTerritory()));
        } else {
            // Phase 1: 全图统一帝国
            fs.setTerritories(new ArrayList<>());
            for (var pe : mapData.getAll().entrySet()) {
                if (pe.getValue() != null && pe.getValue().getName() != null)
                    fs.getTerritories().add(pe.getValue().getName());
            }
        }
        fs.setForces(new ArrayList<>(faction.getInitialForces()));
        fs.setEvolutionStage(0);
        fs.setUnitSerial(new HashMap<>(Map.of("total", 0)));

        // 生成部队
        List<Unit> units = autoGenerateUnits(faction);
        fs.setUnits(units);
        fs.setArmy(recountArmyFromUnits(units));

        // 番号前缀（Phase1帝国用大清，快速开局用势力名）
        fs.setUnitPrefix(isQuickStart ? deriveUnitPrefix(faction.getName()) : "大清");

        state.setFactionState(fs);

        // 初始化全势力外交关系（区域内所有势力对）
        Map<String, Map<String, Object>> allRel = new LinkedHashMap<>();
        var allFactionIds = new ArrayList<>(gameData.getFactions().keySet());
        for (var ne : gameData.getNpcFactions().entrySet()) allFactionIds.add(ne.getKey());
        for (int i = 0; i < allFactionIds.size(); i++) {
            for (int j = i + 1; j < allFactionIds.size(); j++) {
                String a = allFactionIds.get(i), b = allFactionIds.get(j);
                if (a.compareTo(b) > 0) { String tmp = a; a = b; b = tmp; }
                String key = a + "↔" + b;
                var fa = getFaction(allFactionIds.get(i)).orElse(null);
                var fb = getFaction(allFactionIds.get(j)).orElse(null);
                // 同区域默认敌对(-10~-30)，异区域默认中立(-10~+5)
                int score = 0;
                if (fa != null && fb != null && fa.getRegion() != null && fa.getRegion().equals(fb.getRegion()))
                    score = -15 - new java.util.Random().nextInt(20);
                else
                    score = -5 + new java.util.Random().nextInt(10);
                // 意识形态修正
                if (fa != null && fb != null)
                    score -= ideologyDistance(fa.getIdeology(), fb.getIdeology()) / 3;
                allRel.put(key, new LinkedHashMap<>(Map.of("score", score, "pact", "", "turns", 0)));
            }
        }
        state.setAllDiplomaticRelations(allRel);

        if (isQuickStart) {
            // 快速开局：应用奏折效果 + 初始化AI势力
            applyMemorialEffects(state);
            for (var fe : gameData.getFactions().entrySet()) {
                String fid = fe.getKey();
                if (fid.equals(factionId)) continue;
                FactionDefinition fdef = fe.getValue();
                AiFactionData ad = new AiFactionData();
                ad.setTerritories(new ArrayList<>(fdef.getInitialTerritory()));
                ad.setRegion(fdef.getRegion());
                state.getAiFactions().put(fid, ad);
            }
        } else {
            // Phase 1: 帝国统一，所有领土归玩家，无AI势力
            // 奏折队列（按回合分配）
            List<String> queue = new ArrayList<>();
            queue.add("northeast"); queue.add("xibei");         // Turn 1
            queue.add("huabei");                                 // Turn 2
            queue.add("southwest"); queue.add("southeast");     // Turn 3
            queue.add("lingnan");                               // Turn 4
            queue.add("nanyang");                               // Turn 5
            // 紧急奏折池子随机打散
            String[] emergency = {"flood","revolt","famine","foreign","treasury","warlord"};
            var rng = new java.util.Random();
            for (int i = 0; i < 4; i++) {
                queue.add(emergency[rng.nextInt(emergency.length)]);
            }
            state.getCustomOrderFlags().addAll(queue); // 复用customOrderFlags暂存奏折队列
        }

        return state;
    }

    /** 应用Phase1奏折效果到游戏状态（国魂数据从 game_data.json:memorial_spirits 读取） */
    @SuppressWarnings("unchecked")
    public void applyMemorialEffects(GameState state) {
        List<String> policies = state.getPhase1Policies();
        if (policies == null || policies.isEmpty()) return;

        FactionState fs = state.getFactionState();
        Map<String, NationalSpirit> pending = new LinkedHashMap<>();

        // 读国魂数据
        Map<String, Map<String, Object>> spiritData = (Map) gameData.getMemorialSpirits();
        if (spiritData == null) return;

        // 处理所有7个区域（批的用approve，驳的用reject）
        for (String region : List.of("northeast","huabei","southwest","southeast","lingnan","nanyang","xibei")) {
            boolean approved = policies.contains(region);
            Map<String, Object> mem = MEMORIALS.get(region);
            Map<String, Object> regionSpirits = (Map) spiritData.get(region);
            if (mem == null || regionSpirits == null) continue;

            // 读对应方向（approve/reject）
            String direction = approved ? "approve" : "reject";
            Map<String, Object> factionSpirits = (Map) regionSpirits.get(direction);
            if (factionSpirits == null) continue;

            // 全局效果：批→扣国库；驳→扣民心/加腐败/加崩溃
            int cost = ((Number) mem.get("cost")).intValue();
            if (approved) {
                fs.setTreasury(Math.max(0, fs.getTreasury() - cost));
            } else {
                // 驳回惩罚（减半）
                fs.setPopulationSupport(clamp(fs.getPopulationSupport() - 4));
                fs.setCorruption(clamp(fs.getCorruption() + 3));
            }

            // 分配国魂给各势力
            for (var entry : factionSpirits.entrySet()) {
                String fid = entry.getKey();
                Map<String, Object> spData = (Map) entry.getValue();
                String sname = (String) spData.get("name");
                Map<String, Integer> effects = spData.containsKey("effects")
                        ? ((Map<String, Integer>) (Object) spData.get("effects")) : null;

                // 有name→创建国魂；无name→只加属性
                if (sname != null && !sname.isEmpty()) {
                    NationalSpirit ns = new NationalSpirit();
                    ns.setName(sname);
                    ns.setDesc((String) spData.getOrDefault("desc", ""));
                    ns.setEffects(effects);
                    pending.put(fid, ns);
                } else if (effects != null && fid.equals(state.getPlayerFactionId())) {
                    for (var e : effects.entrySet())
                        fs.getStats().add(e.getKey(), e.getValue());
                }
            }
        }

        // 处理外国贷款等非区域奏折
        for (String policy : policies) {
            if (policy.startsWith("_turn") || policy.startsWith("rej_")) continue;
            if (List.of("northeast","huabei","southwest","southeast","lingnan","nanyang","xibei").contains(policy)) continue;
            Map<String, Object> regionSpirits = (Map) spiritData.get(policy);
            if (regionSpirits == null) continue;
            Map<String, Object> factionSpirits = (Map) regionSpirits.get("approve");
            if (factionSpirits == null) continue;
            for (var entry : factionSpirits.entrySet()) {
                String fid = entry.getKey();
                Map<String, Object> spData = (Map) entry.getValue();
                String sname = (String) spData.get("name");
                if (sname == null || sname.isEmpty()) continue;
                NationalSpirit ns = new NationalSpirit();
                ns.setName(sname);
                ns.setDesc((String) spData.getOrDefault("desc", ""));
                ns.setEffects((Map<String, Integer>) (Object) spData.get("effects"));
                pending.put(fid, ns);
            }
        }

        state.setPendingSpirits(pending);
        // 立即应用到玩家势力
        NationalSpirit playerSpirit = pending.get(state.getPlayerFactionId());
        if (playerSpirit != null) {
            fs.setNationalSpirit(playerSpirit);
            if (playerSpirit.getEffects() != null)
                for (var e : playerSpirit.getEffects().entrySet())
                    fs.getStats().add(e.getKey(), e.getValue());
        }
    }

    /** 从势力初始部队名生成Unit列表 */
    public List<Unit> autoGenerateUnits(FactionDefinition faction) {
        List<Unit> units = new ArrayList<>();
        Map<String, Integer> serial = new HashMap<>();
        serial.put("total", 0);
        serial.put("infantry", 0);
        serial.put("cavalry", 0);
        serial.put("artillery", 0);
        serial.put("engineer", 0);
        serial.put("naval", 0);

        List<String> forces = faction.getInitialForces();
        String prefix = deriveUnitPrefix(faction.getName());

        for (String forceName : forces) {
            String type = inferUnitType(forceName);
            serial.merge("total", 1, Integer::sum);
            serial.merge(type, 1, Integer::sum);

            Unit u = new Unit();
            u.setName(generateUnitName(prefix, type, serial.get(type)));
            u.setType(type);
            @SuppressWarnings("unchecked")
            Map<String, Object> ut = (Map<String, Object>) UNIT_TYPES.getOrDefault(type, UNIT_TYPES.get("infantry"));
            u.setAttack(((Number) ut.get("atk_bonus")).intValue() + 7);
            u.setDefense(((Number) ut.get("def_bonus")).intValue() + 5);
            u.setMorale(55);
            u.setExperience(20);
            u.setSpeed("cavalry".equals(type) ? 2 : 1);
            u.setStrength(100);
            u.setMaxStrength(100);
            u.setStatus("ready");

            // 部署位置：有领土则放在首府，否则随机
            List<String> territories = faction.getInitialTerritory();
            if (!territories.isEmpty()) {
                u.setPosition(territories.get(units.size() % territories.size()));
            }
            units.add(u);
        }
        return units;
    }

    /** 根据名称推断兵种 */
    public String inferUnitType(String name) {
        if (name.contains("骑兵") || name.contains("马队") || name.contains("蒙旗")) return "cavalry";
        if (name.contains("炮兵") || name.contains("炮队")) return "artillery";
        if (name.contains("工兵") || name.contains("铁道")) return "engineer";
        if (name.contains("海军") || name.contains("舰") || name.contains("水师")) return "naval";
        return "infantry";
    }

    /** 生成部队番号 */
    public String generateUnitName(String prefix, String type, int serialNum) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ut = (Map<String, Object>) UNIT_TYPES.getOrDefault(type, UNIT_TYPES.get("infantry"));
        String suffix = (String) ut.get("suffix");
        return prefix + ut.get("name") + "第" + serialNum + suffix;
    }

    /** 从势力名推断番号前缀 */
    public String deriveUnitPrefix(String name) {
        if (name.length() <= 4) return name;
        // 简单截取前3-4字
        return name.substring(0, Math.min(4, name.length()));
    }

    /** 从部队列表重算各兵种计数 */
    public Map<String, Integer> recountArmyFromUnits(List<Unit> units) {
        Map<String, Integer> army = new HashMap<>();
        army.put("infantry", 0);
        army.put("cavalry", 0);
        army.put("artillery", 0);
        army.put("engineer", 0);
        army.put("naval", 0);
        if (units != null) {
            for (Unit u : units) {
                if (u.isActive()) {
                    army.merge(u.getType(), 1, Integer::sum);
                }
            }
        }
        return army;
    }

    // ═══════════════════════════════════════════ 外交/区域/战术辅助 ═══════════════════════════════════════════

    /** 检查势力所在区域是否已统一（该区无其他存活的势力，含合并后的NPC） */
    public boolean isRegionUnified(GameState state, String factionId) {
        var faction = getFaction(factionId).orElse(null);
        if (faction == null) return false;
        String region = faction.getRegion();
        for (var fe : gameData.getFactions().entrySet()) {
            String fid = fe.getKey();
            if (fid.equals(factionId)) continue;
            if (state.getDefeatedFactions().contains(fid)) continue;
            if (region.equals(fe.getValue().getRegion())) return false;
        }
        for (var he : gameData.getHostileNpcs().entrySet()) {
            String hid = he.getKey();
            if (state.getDefeatedFactions().contains(hid)) continue;
            String hr = he.getValue().getRegion();
            if (hr != null && region.equals(hr)) return false;
        }
        return true;
    }

    /** 获取NPC名称 */
    private String getNpcName(String npcId) {
        var hn = gameData.getHostileNpcs().get(npcId);
        if (hn != null) return hn.getName();
        return npcId;
    }

    /** 检查两势力间是否有互不侵犯协议 */
    public boolean hasNonAggression(GameState state, String fid1, String fid2) {
        Map<String, Integer> pacts = state.getNonAggressionPacts();
        if (pacts == null) return false;
        String key1 = fid1 + "↔" + fid2;
        String key2 = fid2 + "↔" + fid1;
        return (pacts.containsKey(key1) && pacts.get(key1) > 0)
                || (pacts.containsKey(key2) && pacts.get(key2) > 0);
    }

    /** 获取全部战术（内置+自定义），返回 {tacticId: {name, icon, atk_mult, def_mult, loss_mult}} */
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> getAllTactics(GameState state) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (var entry : TACTICS.entrySet()) {
            merged.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        if (state != null && state.getCustomTactics() != null) {
            for (var entry : state.getCustomTactics().entrySet()) {
                CustomTactic ct = entry.getValue();
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("name", ct.getName());
                t.put("icon", ct.getIcon());
                t.put("atk_mult", ct.getAtkMult());
                t.put("def_mult", ct.getDefMult());
                t.put("loss_mult", ct.deriveLossMult());
                t.put("pro", ct.getPro());
                t.put("con", ct.getCon());
                merged.put(entry.getKey(), t);
            }
        }
        return merged;
    }

    /** 如果失去的领土是首都，迁移到剩余领土中的第一个 */
    public String relocateCapitalIfLost(FactionState fs, String lostName) {
        if (fs.getCapital() == null || !fs.getCapital().equals(lostName)) return null;
        List<String> territories = fs.getTerritories();
        if (territories == null || territories.isEmpty()) {
            fs.setCapital("");
            return "⚠ 首都" + lostName + "沦陷！已无领土可迁都！";
        }
        fs.setCapital(territories.get(0));
        return "⚠ 首都" + lostName + "沦陷！迁都至" + territories.get(0) + "。";
    }

    /** 获取某势力的领土列表 */
    public List<String> getFactionTerritories(GameState state, String fid) {
        if (fid == null) return List.of();
        if (fid.equals(state.getPlayerFactionId())) {
            return state.getFactionState() != null && state.getFactionState().getTerritories() != null
                    ? state.getFactionState().getTerritories() : List.of();
        }
        var aiData = state.getAiFactions().get(fid);
        if (aiData == null) return List.of();
        FactionState afs = aiData.getFactionState();
        return afs != null && afs.getTerritories() != null ? afs.getTerritories() : List.of();
    }

    /** 获取某势力的 faction_state */
    public FactionState getFactionState(GameState state, String fid) {
        if (fid == null) return null;
        if (fid.equals(state.getPlayerFactionId())) return state.getFactionState();
        var aiData = state.getAiFactions().get(fid);
        if (aiData == null) return null;
        return aiData.getFactionState() != null ? aiData.getFactionState()
                : buildLegacyFactionState(aiData);
    }

    private FactionState buildLegacyFactionState(AiFactionData aiData) {
        FactionState fs = aiData.getFactionState();
        if (fs == null) {
            fs = new FactionState();
            fs.setTerritories(aiData.getTerritories());
        }
        return fs;
    }
}
