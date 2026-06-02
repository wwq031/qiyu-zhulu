# 七域逐鹿 · Java Edition v2.3

> 架空1910年代中华大地 · 28势力文字策略战棋 · Spring Boot

## 快速启动

```bash
# 构建
mvn package -DskipTests

# 运行
java -jar target/qiyuzhulu-2.2.0.jar

# 浏览器打开
http://localhost:5000
```

## 游戏简介

1910年，大清帝国奄奄一息。你是帝国的最后决策者——批阅奏折，在国库空虚与民变四起之间艰难抉择。帝国终将崩塌，28路势力在七大区域崛起。选择你的势力，统一本区，逐鹿天下。

## 玩法

### Phase 1：帝国余晖
- 全图统一的大清帝国，340省
- 每回合批阅一份奏折（准/驳），影响国库/民心/腐败
- 可在各省建设工厂、军校、水利
- 国库<30或民心<10触发帝国崩溃

### Phase 2-3：群雄逐鹿
- 28势力选择，每个有独立的领袖、国魂、进化路径
- 募兵、移动、发动战役
- 内政建设 + 税率调节 + 国策颁布
- 外交：互不侵犯、贸易、同盟、宣战、和谈
- 统一本区域后可跨区作战

### 交互方式
- 侧栏7个部门面板（国防/政府/外交/情报/建设/科技/国策）
- 地图点击部队 → 移动/攻击命令模式
- 左下角事件日志
- 按 `` ` `` 键打开调试面板（排行榜/切换势力/DOM检查）

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot 3.2.5 · Java 17 · Maven |
| 数据 | Jackson JSON · SQLite (HikariCP) |
| 前端 | 原生 JS (零构建工具) · Leaflet 地图 |
| AI | 本地模板 / DeepSeek / OpenAI / Claude |

## 项目规模

- Java: 44文件 · ~10,000行
- 前端: 9 JS文件 + 1 HTML + 1 CSS · ~8,000行
- 数据: game_data.json (28势力+29NPC) · map_data.json (340省) · resolutions.json (57国策) · events.json (11事件)

## 目录结构

```
七域逐鹿-java/
├── pom.xml
├── src/main/java/com/qiyuzhulu/
│   ├── QiyuApplication.java
│   ├── model/      (18 POJOs)
│   ├── repo/       (数据加载 + SQLite)
│   ├── service/    (13 服务)
│   └── controller/ (6 控制器)
├── src/main/resources/
│   ├── static/     (前端)
│   └── data/       (JSON数据文件)
└── saves/          (存档目录)
```

## 存档

SQLite存储在 `saves/` 目录，JSON格式兼容Python版老存档。

## 开发

```bash
# 编译
mvn compile

# 运行测试
mvn test

# 打包
mvn package -DskipTests
```

## 相关

- Python原版: `../七域逐鹿/`
- 架构文档: `CLAUDE.md`
