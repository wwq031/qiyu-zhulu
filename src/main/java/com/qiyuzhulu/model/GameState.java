package com.qiyuzhulu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

/**
 * 游戏顶层状态。对应 Python 的顶级 state dict。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameState {

    private int version;                            // 存档版本号（当前7）
    @JsonProperty("save_name")
    private String saveName;                        // 存档名
    @JsonProperty("created_at")
    private String createdAt;                       // 创建时间 ISO
    @JsonProperty("updated_at")
    private String updatedAt;                       // 更新时间 ISO

    private int phase;                              // 游戏阶段 1-5
    private int turn;                               // 当前回合
    @JsonProperty("game_date")
    private String gameDate;                        // 游戏日期 YYYY-MM
    @JsonProperty("player_faction_id")
    private String playerFactionId;                 // 玩家势力ID
    @JsonProperty("phase1_policies")
    private List<String> phase1Policies;            // 阶段1国策选择
    @JsonProperty("action_points")
    private int actionPoints;                       // 当前行动点
    @JsonProperty("ap_max")
    private int apMax;                              // 最大行动点（默认3）
    @JsonProperty("faction_state")
    private FactionState factionState;              // 玩家势力状态
    @JsonProperty("construction_queue")
    private List<ConstructionItem> constructionQueue;  // 建设队列
    @JsonProperty("training_queue")
    private List<TrainingItem> trainingQueue;       // 训练队列
    @JsonProperty("diplomatic_relations")
    private Map<String, DiplomaticRelation> diplomaticRelations;  // 外交关系（玩家↔目标）
    @JsonProperty("all_diplomatic_relations")
    private Map<String, Map<String, Object>> allDiplomaticRelations; // 全势力外交（fidA↔fidB→{score,pact,turns}）
    @JsonProperty("ai_factions")
    private Map<String, AiFactionData> aiFactions;  // AI势力数据
    @JsonProperty("defeated_factions")
    private List<String> defeatedFactions;          // 已灭亡势力ID列表
    @JsonProperty("pending_spirits")
    private Map<String, NationalSpirit> pendingSpirits; // Phase1奏折分配的国魂（fid→spirit），初始化后清除
    @JsonProperty("active_wars")
    private List<String> activeWars;                // 当前战争中的势力ID列表
    @JsonProperty("non_aggression_pacts")
    private Map<String, Integer> nonAggressionPacts; // 互不侵犯协议 {fid: 剩余回合}
    @JsonProperty("active_chains")
    private List<Map<String, Object>> activeChains; // 进行中的事件链
    @JsonProperty("active_campaigns")
    private List<Campaign> activeCampaigns;         // 进行中的战役
    @JsonProperty("event_history")
    private List<Map<String, Object>> eventHistory; // 事件历史
    @JsonProperty("triggered_epic_events")
    private List<String> triggeredEpicEvents;       // 已触发的史诗事件ID
    @JsonProperty("enacted_resolutions")
    private List<String> enactedResolutions;        // 已执行的国策ID列表
    @JsonProperty("custom_regime_name")
    private String customRegimeName;                // 自定义政权名
    @JsonProperty("background_simulation")
    private Map<String, Object> backgroundSimulation; // 背景推演
    @JsonProperty("stats_tracker")
    private Map<String, Object> statsTracker;       // 统计追踪器
    @JsonProperty("researched_techs")
    private List<String> researchedTechs;           // 已研发科技
    @JsonProperty("custom_order_flags")
    private List<String> customOrderFlags;          // 自定义指令持久标记
    @JsonProperty("custom_tactics")
    private Map<String, CustomTactic> customTactics; // 自定义战术
    @JsonProperty("custom_unit_types")
    private Map<String, CustomUnitType> customUnitTypes; // 自定义兵种
    @JsonProperty("ai_personalities")
    private Map<String, Object> aiPersonalities;    // AI性格

    // ── 瞬态（不持久化）──
    @JsonProperty("_events_this_turn")
    private List<String> eventsThisTurn;
    @JsonProperty("_epic_events_this_turn")
    private List<String> epicEventsThisTurn;
    @JsonProperty("_tech_events_this_turn")
    private List<String> techEventsThisTurn;
    @JsonProperty("_defeat_events")
    private List<Map<String, Object>> defeatEvents;
    @JsonProperty("_campaign_results_this_turn")
    private List<CampaignResult> campaignResultsThisTurn;

    // ── 内部标记 ──
    private boolean movingUnitsAdvancedThisTurn;
    private boolean turnSnapshotted;
    private transient Set<String> honoredCampaigns;

    public GameState() {
        this.constructionQueue = new ArrayList<>();
        this.trainingQueue = new ArrayList<>();
        this.diplomaticRelations = new HashMap<>();
        this.allDiplomaticRelations = new HashMap<>();
        this.aiFactions = new HashMap<>();
        this.defeatedFactions = new ArrayList<>();
        this.activeWars = new ArrayList<>();
        this.nonAggressionPacts = new HashMap<>();
        this.activeChains = new ArrayList<>();
        this.activeCampaigns = new ArrayList<>();
        this.eventHistory = new ArrayList<>();
        this.triggeredEpicEvents = new ArrayList<>();
        this.enactedResolutions = new ArrayList<>();
        this.researchedTechs = new ArrayList<>();
        this.customOrderFlags = new ArrayList<>();
        this.customTactics = new HashMap<>();
        this.customUnitTypes = new HashMap<>();
        this.eventsThisTurn = new ArrayList<>();
        this.epicEventsThisTurn = new ArrayList<>();
        this.techEventsThisTurn = new ArrayList<>();
        this.defeatEvents = new ArrayList<>();
        this.campaignResultsThisTurn = new ArrayList<>();
    }

    // ── Getters / Setters (essential ones) ──

    public int getVersion() { return version; }
    public void setVersion(int v) { this.version = v; }
    public String getSaveName() { return saveName; }
    public void setSaveName(String v) { this.saveName = v; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String v) { this.createdAt = v; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String v) { this.updatedAt = v; }
    public int getPhase() { return phase; }
    public void setPhase(int v) { this.phase = v; }
    public int getTurn() { return turn; }
    public void setTurn(int v) { this.turn = v; }
    public String getGameDate() { return gameDate; }
    public void setGameDate(String v) { this.gameDate = v; }
    public String getPlayerFactionId() { return playerFactionId; }
    public void setPlayerFactionId(String v) { this.playerFactionId = v; }
    public List<String> getPhase1Policies() { return phase1Policies; }
    public void setPhase1Policies(List<String> v) { this.phase1Policies = v; }
    public Map<String, NationalSpirit> getPendingSpirits() { return pendingSpirits; }
    public void setPendingSpirits(Map<String, NationalSpirit> v) { this.pendingSpirits = v; }
    public int getActionPoints() { return actionPoints; }
    public void setActionPoints(int v) { this.actionPoints = v; }
    public int getApMax() { return apMax; }
    public void setApMax(int v) { this.apMax = v; }
    public FactionState getFactionState() { return factionState; }
    public void setFactionState(FactionState v) { this.factionState = v; }
    public List<ConstructionItem> getConstructionQueue() { return constructionQueue; }
    public void setConstructionQueue(List<ConstructionItem> v) { this.constructionQueue = v; }
    public List<TrainingItem> getTrainingQueue() { return trainingQueue; }
    public void setTrainingQueue(List<TrainingItem> v) { this.trainingQueue = v; }
    public Map<String, DiplomaticRelation> getDiplomaticRelations() { return diplomaticRelations; }
    public void setDiplomaticRelations(Map<String, DiplomaticRelation> v) { this.diplomaticRelations = v; }
    public Map<String, Map<String, Object>> getAllDiplomaticRelations() { return allDiplomaticRelations; }
    public void setAllDiplomaticRelations(Map<String, Map<String, Object>> v) { this.allDiplomaticRelations = v; }
    public Map<String, AiFactionData> getAiFactions() { return aiFactions; }
    public void setAiFactions(Map<String, AiFactionData> v) { this.aiFactions = v; }
    public List<String> getDefeatedFactions() { return defeatedFactions; }
    public void setDefeatedFactions(List<String> v) { this.defeatedFactions = v; }
    public List<String> getActiveWars() { return activeWars; }
    public void setActiveWars(List<String> v) { this.activeWars = v; }
    public Map<String, Integer> getNonAggressionPacts() { return nonAggressionPacts; }
    public void setNonAggressionPacts(Map<String, Integer> v) { this.nonAggressionPacts = v; }
    public List<Campaign> getActiveCampaigns() { return activeCampaigns; }
    public void setActiveCampaigns(List<Campaign> v) { this.activeCampaigns = v; }
    public List<String> getEnactedResolutions() { return enactedResolutions; }
    public void setEnactedResolutions(List<String> v) { this.enactedResolutions = v; }
    public List<String> getResearchedTechs() { return researchedTechs; }
    public void setResearchedTechs(List<String> v) { this.researchedTechs = v; }
    public List<String> getCustomOrderFlags() { return customOrderFlags; }
    public void setCustomOrderFlags(List<String> v) { this.customOrderFlags = v; }
    public Map<String, CustomTactic> getCustomTactics() { return customTactics; }
    public void setCustomTactics(Map<String, CustomTactic> v) { this.customTactics = v; }
    public Map<String, CustomUnitType> getCustomUnitTypes() { return customUnitTypes; }
    public void setCustomUnitTypes(Map<String, CustomUnitType> v) { this.customUnitTypes = v; }
    public Map<String, Object> getStatsTracker() { return statsTracker; }
    public void setStatsTracker(Map<String, Object> v) { this.statsTracker = v; }
    public List<Map<String, Object>> getActiveChains() { return activeChains; }
    public void setActiveChains(List<Map<String, Object>> v) { this.activeChains = v; }
    public List<Map<String, Object>> getEventHistory() { return eventHistory; }
    public void setEventHistory(List<Map<String, Object>> v) { this.eventHistory = v; }
    public List<String> getTriggeredEpicEvents() { return triggeredEpicEvents; }
    public void setTriggeredEpicEvents(List<String> v) { this.triggeredEpicEvents = v; }
    public String getCustomRegimeName() { return customRegimeName; }
    public void setCustomRegimeName(String v) { this.customRegimeName = v; }
    public Map<String, Object> getBackgroundSimulation() { return backgroundSimulation; }
    public void setBackgroundSimulation(Map<String, Object> v) { this.backgroundSimulation = v; }
    public Map<String, Object> getAiPersonalities() { return aiPersonalities; }
    public void setAiPersonalities(Map<String, Object> v) { this.aiPersonalities = v; }
    public List<String> getEventsThisTurn() { return eventsThisTurn; }
    public void setEventsThisTurn(List<String> v) { this.eventsThisTurn = v; }
    public List<String> getEpicEventsThisTurn() { return epicEventsThisTurn; }
    public void setEpicEventsThisTurn(List<String> v) { this.epicEventsThisTurn = v; }
    public List<String> getTechEventsThisTurn() { return techEventsThisTurn; }
    public void setTechEventsThisTurn(List<String> v) { this.techEventsThisTurn = v; }
    public List<Map<String, Object>> getDefeatEvents() { return defeatEvents; }
    public void setDefeatEvents(List<Map<String, Object>> v) { this.defeatEvents = v; }
    public List<CampaignResult> getCampaignResultsThisTurn() { return campaignResultsThisTurn; }
    public void setCampaignResultsThisTurn(List<CampaignResult> v) { this.campaignResultsThisTurn = v; }

    public boolean isMovingUnitsAdvancedThisTurn() { return movingUnitsAdvancedThisTurn; }
    public void setMovingUnitsAdvancedThisTurn(boolean v) { this.movingUnitsAdvancedThisTurn = v; }
    public boolean isTurnSnapshotted() { return turnSnapshotted; }
    public void setTurnSnapshotted(boolean v) { this.turnSnapshotted = v; }

    public Set<String> getHonoredCampaigns() { return honoredCampaigns; }
    public void setHonoredCampaigns(Set<String> v) { this.honoredCampaigns = v; }
}
