# 七域逐鹿 · Java Edition

> 架空1910年代中华大地 · 28势力文字策略战棋 · Spring Boot 3.2 + Java 17
> 对应 Python v2.1 完整移植 + Phase 1 帝国系统（Java独有）

## 默认行为

- 用户说"打开项目"/"继续更新"/"更新七域逐鹿java" → cd 到此目录并操作
- 用户说"跑测试" → mvn package + java -jar + curl 验证API
- 用户说"存进度" → git add -A + git commit + 更新 memory
- 修改前端后提醒 Ctrl+F5 刷新（版本戳 ?v=X.Y.Z）

## 启动

```bash
mvn package -DskipTests
java -jar target/qiyuzhulu-2.2.0.jar --server.port=5000
# → http://localhost:5000
```

## 项目结构

```
七域逐鹿-java/
├── pom.xml                          Spring Boot 3.2.5 + Java 17
├── CLAUDE.md                       本文件
├── README.md                       项目说明
├── start.bat
├── saves/                           SQLite 存档
└── src/main/
    ├── java/com/qiyuzhulu/
    │   ├── QiyuApplication.java
    │   ├── config/
    │   │   ├── WebConfig.java
    │   │   └── GlobalExceptionHandler.java  全局异常拦截 → 结构化报错JSON
    │   ├── model/                   (18 POJOs)
    │   │   ├── GameState.java       顶层状态 (~50字段, @JsonProperty)
    │   │   ├── FactionState.java    势力状态 (stats/treasury/units/territories/spirit/corruption...)
    │   │   ├── FactionDefinition.java 势力定义 (game_data.json映射, 含collapse_intro)
    │   │   ├── NpcDefinition.java   NPC势力 (color/territories/forceNames)
    │   │   ├── Province.java        省份数据 (lat/lng/type/connections/economy...)
    │   │   ├── Unit.java            部队 (toMap方法)
    │   │   ├── Campaign.java        战役 (attackerCache/defenderCache/round...)
    │   │   ├── Stats.java           六围 (get/set/add方法)
    │   │   ├── NationalSpirit.java  国魂 (name/desc/effects)
    │   │   ├── AiFactionData.java   AI势力数据
    │   │   ├── AiPersonality.java   AI性格 (record, 6常量: minAttackRatio/defaultTactic)
    │   │   ├── ConstructionItem.java 建设队列项
    │   │   ├── TrainingItem.java    训练队列项
    │   │   ├── DiplomaticRelation.java 外交关系
    │   │   ├── ActionResult.java    统一返回类型 (ok/message/extra)
    │   │   ├── CampaignResult.java  战役结果
    │   │   ├── CustomTactic/UnitType.java 自定义战术/兵种
    │   │   ├── EnemyProvince.java   敌方省份
    │   │   ├── Leader.java          领袖
    │   │   └── ProvinceRef.java     省份引用 (record)
    │   ├── repo/
    │   │   ├── GameDataRepo.java    加载 game_data.json (含NPC数据合并)
    │   │   ├── MapDataRepo.java     加载 map_data.json (340省)
    │   │   └── SaveRepo.java        SQLite 存档读写
    │   ├── service/
    │   │   ├── GameEngine.java      核心引擎 (~850行)
    │   │   │   ├── MEMORIALS        13份奏折定义 (LinkedHashMap)
    │   │   │   ├── REGION_ADJACENCY 区域邻接表
    │   │   │   ├── FOREIGN_POWERS   列强映射
    │   │   │   ├── newState()       创建游戏 (Phase1/快速开局双路径)
    │   │   │   ├── applyMemorialEffects() 应用奏折国魂
    │   │   │   ├── autoGenerateUnits() 生成部队
    │   │   │   ├── autoClaimArrival() 自动占领 (支持任意势力)
    │   │   │   ├── isRegionUnified() 区域统一检查
    │   │   │   ├── calcIncome/Maintenance() 收入/维持费计算
    │   │   │   └── getDistance()    BFS距离计算
    │   │   ├── TurnAdvanceService.java 回合推进 (~700行)
    │   │   │   ├── advance()        主方法 (收入/队列/腐败/崩溃检测/AI/事件)
    │   │   │   ├── 帝国崩溃检测     Phase1→2 (allDone/critical)
    │   │   │   ├── 外国贷款触发     国库<50
    │   │   │   └── generateRumors() 世界传言
    │   │   ├── MilitaryService.java 训练/部署 (~350行)
    │   │   ├── CampaignService.java 战役系统 (~1100行)
    │   │   │   ├── startCampaign()  发动战役 (NPC空城直接占领)
    │   │   │   ├── resolveAllCampaigns() 多轮结算
    │   │   │   └── listEnemyProvinces() 敌省列表 (含NPC)
    │   │   ├── CivilService.java    建设/税率 (~150行, BUILD_DEFS)
    │   │   ├── DiplomacyService.java 外交/国策/情报 (~520行)
    │   │   ├── AiFactionService.java AI势力 (~670行)
    │   │   │   ├── process()        主循环 (收入/募兵/移动/战役)
    │   │   │   ├── aiRecruit()      AI募兵
    │   │   │   ├── aiMoveUnits()    AI移动
    │   │   │   ├── aiLaunchCampaigns() AI战役
    │   │   │   ├── processAiToAiDiplomacy() AI间外交
    │   │   │   ├── checkAiDiplomacy() AI→玩家外交
    │   │   │   └── findAiTargets()  找攻击目标 (NAP检查)
    │   │   ├── AiProviderService.java AI API对接 (~410行)
    │   │   ├── EventService.java    随机事件/事件链/史诗 (~380行)
    │   │   ├── SandboxService.java  自由指令/沙盒 (~590行)
    │   │   ├── TechService.java     科技树（框架）(~130行)
    │   │   ├── PanelRenderer.java   面板渲染 (~190行)
    │   │   └── GameUtils.java       工具 (mapOf)
    │   └── controller/
    │       ├── StateController.java /api/state, /api/action (30+分支), /api/new-game,
    │       │                       /api/memorial/resolve, /api/empire/switch-faction,
    │       │                       /api/debug/switch, /api/debug/rankings
    │       │                       + 1.2-1.5全子菜单handler + 建设三省名兼容
    │       ├── MapController.java   /api/map (ownership/garrisons/capitals/city_store/active_campaigns),
    │       │                       /api/map/province-detail, /api/map/faction-info
    │       ├── CampaignController.java /api/map/reachable, /api/map/move, /api/map/attack,
    │       │                         /api/campaign/*
    │       ├── CustomOrderController.java /api/custom-order/*
    │       └── DataController.java  /api/factions, /api/regions, /api/config, /api/saves
    └── resources/
        ├── application.yml
        ├── static/
        │   ├── index.html           HTML骨架 (~180行) · 测试面板 (按`呼出)
        │   ├── css/game.css          全站样式 (~140行) · 滚动条暗色主题
        │   ├── vendor/leaflet/       Leaflet 地图库
        │   ├── js/utils.js           API封装+Toast通知+API日志+全局JS错误捕获
        │   ├── js/app.js             主应用 (~1050行) + try-catch全入口覆盖
        │   │   ├── renderAll()       主渲染 (Phase1检测/部门面板/事件弹窗/战役结果)
        │   │   ├── openDept()        部门面板 (7部门action映射)
        │   │   ├── renderDeptContent() 部门内容渲染
        │   │   ├── showCollapseNarrative() 崩溃叙事
        │   │   ├── showPostCollapseFactionPicker() 势力选择 (左右双栏)
        │   │   ├── renderCollapseDetail() 势力详情 (右侧面板)
        │   │   ├── showMemorialPopup() 奏折弹窗
        │   │   └── resolveMemorial() 批阅奏折
        │   ├── js/map.js             Leaflet地图 (~1200行)
        │   │   ├── initLeafletMap()  初始化
        │   │   ├── applyOwnership()  全量刷新 (填色/驻军/首都/标签)
        │   │   ├── _updateGarrisonLayer() 增量驻军
        │   │   ├── _updateCapitalLabels() 首都标签
        │   │   └── rebuildFactionLabels() 势力名称标签
        │   ├── js/map-commands.js    命令模式 (~210行)
        │   ├── js/popups.js          弹窗系统 (~410行)
        │   │   ├── showCampaignPopup() 战役结算
        │   │   ├── showBattleInfoPopup() 战役详情 (战术调整/增援/撤退)
        │   │   └── submitReinforce() 增援提交
        │   ├── js/menus.js           子菜单 (~1000行, 25+类型)
        │   ├── js/newgame.js         新游戏向导 (~220行)
        │   ├── js/custom-order.js    自然语言指令
        │   └── js/settings.js        AI/API配置 (动态表单)
        └── data/
            ├── game_data.json        28势力+29NPC+7区域+科技树+75事件+memorial_spirits
            ├── map_data.json         340省 (100城+237郊+1港+2关)
            ├── resolutions.json      57条国策 (含8条spirit)
            └── events.json           11种史诗/历史事件
```

## 游戏阶段

| Phase | 名称 | 说明 | 触发 |
|-------|------|------|------|
| 1 | 帝国余晖 | 统一大清，批阅奏折 | newState (policies=[]) |
| 2 | 帝国大崩溃 | 势力选择，28势力分裂 | 帝国崩溃条件满足 |
| 3 | 区域统一战 | 同区4势力厮杀 | Phase2 + turn>=2 或 switch-faction |
| 4 | 七强并立 | 可跨区作战 | 区域统一 |
| 5 | 天下归一 | 终局决战 | — |

## 关键API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/state` | GET | 完整游戏状态 |
| `/api/action` | POST | 游戏动作 (action字符串, 30+分支含军事/内政/外交/情报/科技/国策/设计局) |
| `/api/new-game` | POST | 创建游戏 (policies:[]→Phase1, policies:[...]→快速开局) |
| `/api/load` | POST | 读档 {slot} |
| `/api/save` | POST | 存档 {slot} |
| `/api/map` | GET | 完整地图 (ownership/garrisons/capitals/city_store/active_campaigns) |
| `/api/map/reachable` | POST | BFS可达省份 (unit_indices/unit_index/position) |
| `/api/map/move` | POST | 移动部队 {unit_indices, dest_pid} |
| `/api/map/attack` | POST | 发动攻击 {unit_indices, dest_pid} |
| `/api/map/province-detail?pid=X` | GET | 省份详情+建筑+归属 |
| `/api/map/faction-info?pid=X` | GET | 省份所属势力详情 |
| `/api/spectator/map` | GET | 旁观模式地图 |
| `/api/campaign/honor` | POST | 战役授勋 |
| `/api/campaign/tactics` | POST | 战中战术调整 |
| `/api/campaign/retreat` | POST | 战役撤退 |
| `/api/campaign/reinforce` | POST | 战役增援 |
| `/api/memorial/resolve` | POST | Phase1 奏折批阅 ({action:"next"\|"resolve", memorial_id, approved}) |
| `/api/empire/switch-faction` | POST | 崩溃后切换势力 {faction_id} |
| `/api/debug/switch` | POST | 调试用随时切换势力 |
| `/api/debug/rankings` | GET | 全势力排行榜 |
| `/api/factions` | GET | 势力列表 |
| `/api/regions` | GET | 区域列表 |
| `/api/config` | GET/POST | AI配置 |
| `/api/saves` | GET | 存档列表 |

## Phase 1 帝国系统

### 奏折种类
- **7核心** (northeast/huabei/southwest/southeast/lingnan/nanyang/xibei)：区域总督奏折，批则国库-30~40+区域国魂，驳则民心-6~12+腐败+3~8
- **6紧急** (flood/revolt/famine/foreign/treasury/warlord)：随机池子插入队列，批/驳同上但无区域国魂
- **5列强贷款** (japan_loan/russia_loan/britain_loan/france_loan/usa_loan)：国库<50触发，批则+30💰+对应NPC获得列强国魂

### 队列机制
- 每回合最多1份（`_turn_N`标记防重复）
- newState时预设：T1:northeast,xibei T2:huabei T3:southwest,southeast T4:lingnan T5:nanyang + 随机4份紧急
- 列强贷款在国库<50时动态注入

### 崩溃条件
```
崩溃 = (国库<30 || 民心<10 || 腐败>80) && 已处理核心奏折>=4
    || 核心奏折全部处理完 (7/7)
```
仅统计7份核心奏折，紧急奏折不参与判定。

### 帝国收入公式
```
collapsePct = (100-populationSupport) + corruption/2
multiplier = max(0.08, 1 - collapsePct/100)
income = calcIncome(territories) × 0.15 × multiplier
```
开局收入约25-35💰/回合（340省帝国，崩溃值~85%）。

### 崩溃后流程
1. TurnAdvanceService 检测崩溃→设置phase=2→收窄玩家领土到京师周边
2. 前端检测 Phase1→2 变化→showCollapseNarrative (动态叙事根据每份奏折准驳)
3. 叙事弹窗→点"选择崛起势力"→左右双栏势力选择器 (左列表/右详情)
4. 点势力卡片→右侧显示领袖/六维/进化/国魂/领土/兵力→"确认选择"
5. 调用 /api/empire/switch-faction → 返回入场叙事 (collapse_intro + 区域敌对势力)
6. Phase设为3，游戏正式开始

## 数据流注意事项

### Map.of 限制
`Map.of()` 最多10个键值对。超过用 `new LinkedHashMap<>()` + put。

### NPC数据
GameDataRepo.load() 中 hostile_npcs 与 npc_factions 按名字合并。hostile_npcs 是主数据源 (NpcDefinition)，npc_factions 补充颜色/完整领土/部队名。合并后所有业务代码只读 hostile_npcs。

### 前端全局变量
- 顶层 var 声明 (跨JS文件共享): window.API, window.gameState, window._STAT_ICONS, window._AI_MODES
- 地图变量: leafletMap, mapProvinceData, garrisonMarkers, ownedBy, capitalPids
- 命令模式: moveMode, moveHighlightLayer, movePathLayer
- 部队选择: selectedUnitIndices (Set), unitCache (Map)

### 版本戳
index.html 中 JS 引用带 `?v=2.3.0` 破浏览器缓存。每次发版更新版本号。

### 弹窗组件
所有弹窗共用 `#event-popup-overlay` (含默认"确定"按钮)。
自定义按钮页面需隐藏默认按钮：
```javascript
setTimeout(function() {
  document.querySelectorAll('#event-popup-overlay .btn-gold')
    .forEach(function(b){b.style.display='none';});
}, 50);
```

### 部门面板 vs 子菜单
部门面板打开时，renderAll 中抑制 renderSubmenu 调用（避免重叠）：
```javascript
var deptOpen = document.getElementById('dept-panel');
if (... && (!deptOpen || !deptOpen.classList.contains('open')))
    renderSubmenu(...);
```

### BFS 移动计算
- 每跳固定+1，忽略连线地理距离权重
- 起点 dist=0
- effectiveSpeed = hasRailway(pos) ? baseSpeed*2 : max(2, baseSpeed)
- 队列推进用 totalDist (不是 totalDist+1)

### 战役多轮结算
- 守方空且round>=2 → 占领
- 攻方空 → 守方固守
- annihilate + round>=2 + 50% → 占领
- decisive_win + round>=3 + 40% → 占领
- costly_win + round>=4 + 25% → 占领
- 每轮结算一次，maxRounds=4
