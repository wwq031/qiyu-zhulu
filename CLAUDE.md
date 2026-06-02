# 七域逐鹿 · Java Edition

> 架空1910年代中华大地 · 28势力争霸 · 文字策略战棋
> Spring Boot 3.2 + Java 17 + Leaflet 地图

## 启动

```bash
mvn package -DskipTests
java -jar target/qiyuzhulu-2.2.0.jar
# → http://localhost:5000
```

## 项目结构

```
src/main/java/com/qiyuzhulu/
├── QiyuApplication.java           Spring Boot 入口
├── config/WebConfig.java          静态文件路由/CORS
├── model/                         18个数据类
│   ├── GameState.java             顶层状态 (~50字段)
│   ├── FactionState.java          势力状态 (stats/treasury/units/territories...)
│   ├── FactionDefinition.java     势力定义 (game_data.json映射)
│   ├── NpcDefinition.java         NPC势力 (hostile_npcs)
│   ├── Province.java              省份数据
│   ├── Unit.java                  部队
│   ├── Campaign.java              战役
│   ├── Stats.java                 六围属性
│   ├── NationalSpirit.java        国魂
│   ├── AiPersonality.java         AI性格 (record, 6常量)
│   ├── ConstructionItem/TrainingItem  建设/训练队列
│   ├── DiplomaticRelation.java    外交关系
│   ├── ActionResult.java          服务层统一返回 (record)
│   └── ...
├── repo/
│   ├── GameDataRepo.java          加载 game_data.json
│   ├── MapDataRepo.java           加载 map_data.json (340省)
│   └── SaveRepo.java              SQLite存档读写
├── service/
│   ├── GameEngine.java            核心引擎 (state/territory/claim/distance/autoGen...)
│   ├── TurnAdvanceService.java    回合推进 (收入/队列/腐败/事件/崩溃检测)
│   ├── MilitaryService.java       训练/部署
│   ├── CampaignService.java       战役系统 (发动/结算/增援/撤退)
│   ├── CivilService.java          建设/税率
│   ├── DiplomacyService.java      外交/国策/情报
│   ├── AiFactionService.java      AI势力 (招募/移动/攻击/外交/国策/建设)
│   ├── AiProviderService.java     AI API对接 (DeepSeek/OpenAI/Claude/local)
│   ├── EventService.java          随机事件/事件链/史诗事件
│   ├── SandboxService.java        自由指令/沙盒模式
│   ├── TechService.java           科技树 (框架)
│   ├── PanelRenderer.java         面板渲染
│   └── GameUtils.java             工具方法 (mapOf)
└── controller/
    ├── StateController.java       /api/state, /api/action (26分支), /api/memorial, /api/empire, /api/debug
    ├── MapController.java         /api/map (完整地图数据), /api/map/province-detail, /api/map/faction-info
    ├── CampaignController.java    /api/map/reachable, /api/map/move, /api/map/attack, /api/campaign/*
    ├── CustomOrderController.java /api/custom-order, /api/custom-order/apply, /api/custom-order/auto
    └── DataController.java        /api/factions, /api/regions, /api/config, /api/saves

src/main/resources/
├── static/                       前端 (零构建工具)
│   ├── index.html                HTML骨架
│   ├── css/game.css              全站样式
│   ├── js/                       9个模块
│   │   ├── utils.js              API封装 + 全局常量
│   │   ├── app.js                全局状态/renderAll/部门面板/奏折系统
│   │   ├── map.js                Leaflet地图 (所有权/驻军/首都/标签)
│   │   ├── map-commands.js       移动/攻击命令模式
│   │   ├── popups.js             弹窗系统 (战役/事件/战术/增援)
│   │   ├── menus.js              子菜单渲染 (25+类型)
│   │   ├── newgame.js            新游戏向导
│   │   ├── custom-order.js       自然语言指令
│   │   └── settings.js           AI/API配置
│   ├── vendor/                   Leaflet
│   └── test.html                 独立前端测试页
└── data/
    ├── game_data.json             28势力+29NPC+7区域+科技树+75事件+奏折国魂
    ├── map_data.json              340省数据 (含连接/坐标/属性)
    ├── resolutions.json           57条国策决议 (含8条spirit)
    └── events.json                9种史诗事件+2历史事件
```

## 游戏阶段

| Phase | 名称 | 说明 |
|-------|------|------|
| 1 | 帝国余晖 | 统一大清，批阅奏折，帝国崩溃前 |
| 2 | 帝国大崩溃 | 势力选择，28势力分裂 |
| 3 | 区域统一战 | 同区4势力厮杀，统一后可跨区 |
| 4 | 七强并立 | 大国博弈 |
| 5 | 天下归一 | 终局决战 |

## 关键API

| 端点 | 说明 |
|------|------|
| `POST /api/new-game` | 创建游戏。`policies:[]` → Phase1; `policies:[...]` → 快速开局 |
| `POST /api/action` | 游戏动作。action字符串驱动26分支if-else |
| `GET /api/map` | 完整地图 (ownership/garrisons/capitals/city_store/active_campaigns) |
| `POST /api/map/reachable` | BFS可达省份 (支持unit_indices/unit_index/position) |
| `POST /api/map/move` | 移动部队 |
| `POST /api/map/attack` | 发动攻击 |
| `POST /api/memorial/resolve` | Phase1奏折批阅 (每回合1份) |
| `POST /api/empire/switch-faction` | 崩溃后切换势力 |
| `GET /api/debug/rankings` | 全势力排行榜 |
| `POST /api/debug/switch` | 调试用随时切换势力 |

## Phase 1 帝国系统

### 奏折
- 7份核心奏折 (东北/华北/西南/东南/岭南/南洋/西北) + 6份紧急 + 5份外国贷款
- 每回合1份，准/驳影响国库/民心/腐败
- 崩溃条件: (国库<30 || 民心<10 || 腐败>80) && 处理>=4份核心
- 崩溃后动态叙事 + 势力选择 (28势力详情页)

### 国魂分配
- 数据: game_data.json → memorial_spirits
- 批→区域政府派得正面国魂; 驳→反对派得负面/补偿国魂
- 外国贷款→对应NPC势力得列强支持国魂

### 帝国收入
```
income = base × 0.15 × max(0.08, 1 - collapsePct/100)
collapsePct = (100-support) + corruption/2
```

## 前端架构

### 部门面板 (v2.2 侧栏)
7个部门: 国防/政府/外交/情报/建设/科技/国策
- openDept(type) → apiPost('/api/action', {action}) → renderDeptContent

### 地图渲染
- applyOwnership(): 全量刷新 (city fill/garrison/capital/battle labels)
- _updateGarrisonLayer(): 增量驻军更新
- rebuildFactionLabels(): 势力名称标签
- 填色: 势力色×22%不透度 / 中立×10%

### 事件弹窗
- showEventPopup() → 队列系统 → showNextEventPopup()
- renderAll开头清队列 (clearEventPopups)
- 部门面板打开时抑制renderSubmenu

### 命令模式 (移动/攻击)
- enterCommandMode() → /api/map/reachable → 高亮可达省份
- showMoveDest() / showAttackDest() → confirmMove() / confirmAttack()

## 常见问题速查

### Map.of 限制
`Map.of()` 最多10个键值对。超过用 `new LinkedHashMap<>()` + put。

### 前端全局变量
var声明在顶层，所有JS文件共享。window.API, window.gameState, window._STAT_ICONS等。
数组方法用 `function` 关键字不用箭头 (兼容性)。

### 版本戳
index.html 中JS引用带 `?v=2.3.0` 破浏览器缓存。每次发版更新。

### 弹窗组件
所有弹窗共用 `#event-popup-overlay`。自定义按钮需隐藏默认"确定"按钮:
```javascript
setTimeout(function() {
  document.querySelectorAll('#event-popup-overlay .btn-gold').forEach(function(b){b.style.display='none';});
}, 50);
```
