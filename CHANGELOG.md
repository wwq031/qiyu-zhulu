# 七域逐鹿 Java 版 · 更新日志

## v2.3.1 (2026-06-03)

### Bug 修复
- **存档加载崩溃**：`FactionState.getActiveUnits()` 返回不可变列表，Jackson 反序列化失败 → 添加 `@JsonIgnore`
- **帝国崩溃不触发**：崩溃条件 `国库<30` 在 340 省帝国下几乎达不到 → 改为处理 4 份核心奏折即崩溃
- **快速开局弹窗空白**：`apiGet` Promise 拒绝无 `.catch()`，静默失败 → 改回一键随机势力直接开局
- **部门子菜单全部无内容**：`renderAll` 逻辑 "部门面板开时不弹子菜单" 导致子项点击无响应 → 改为先关面板再弹子菜单
- **1.2/1.3/1.4 无后端 handler**：军事菜单定义了子项但后端未实现 → 新增 `campaigns_menu`/`deployment_menu`/`operations_menu` handler
- **建设部跳过省份选择**：部门面板建设按钮直接调 `sendAction`，跳过 `buildItem` 流程 → 改走完整省份选择路径
- **建设 `province` 参数不匹配**：前端发 `meta.province`，后端读 `location_pid` → 后端兼容三种来源
- **设计局无自定义内容**：1.5 只返 keySet，前端不认 → 返回 `options` 数组；1.5.1/1.5.2 → `design_result`
- **app.js:679 语法错误**：单引号字符串内 `\\'` 意外闭合 → 改为 `\'`

### 新增功能
- **GlobalExceptionHandler**：`@ControllerAdvice` 全局异常拦截 → `{error, error_id, type, path}` 结构化 JSON
- **前端报错系统**：
  - `apiGet`/`apiPost` 每次调用自动控制台日志（含耗时 ms）
  - `showToast(msg, type)` — 顶部通知条（info/error/warn/success）
  - `window.onerror` — 全局 JS 错误自动弹 toast
  - `unhandledrejection` — 未捕获 Promise 自动弹 toast
  - 所有入口函数 + `renderAll` 加 try-catch
  - 所有 `alert()` 改为 `showToast()`
- **军事子菜单全部接入**：1.1(训练)/1.2(战争)/1.3(部署)/1.4(行动)/1.5(设计局)
- **设计局自定义内容**：创建自定义战术/兵种 → 实时注册 → 设计局列表即时更新

### 文件变更
| 文件 | 类型 | 说明 |
|------|------|------|
| `config/GlobalExceptionHandler.java` | 新增 | 全局异常处理 |
| `model/FactionState.java` | 修改 | `@JsonIgnore` on getActiveUnits |
| `service/TurnAdvanceService.java` | 修改 | 崩溃条件放宽 |
| `service/MilitaryService.java` | 修改 | getUnitIcon/TypeName 改 public |
| `controller/StateController.java` | 修改 | +1.2/1.3/1.4/1.5 handler, 建设省名兼容, design_result |
| `static/js/utils.js` | 重写 | Toast+日志+全局错误捕获 |
| `static/js/app.js` | 修改 | renderAll 关面板逻辑, renderDeptContent build 路由, try-catch |
| `static/js/menus.js` | 修改 | doBuild 省名修复, alert→showToast |
| `static/js/newgame.js` | 修改 | quickStart 恢复一键行为, try-catch |
| `CLAUDE.md` | 修改 | 架构文档同步 |
| `README.md` | 修改 | 版本演进更新 |
| `CHANGELOG.md` | 新增 | 本文件 |

---

## v2.3.0-dev (2026-06-02)
Phase 1 帝国系统：御前奏折(13份)、崩溃叙事、势力选择详情页、开场/入场叙事、区域敌对势力、帝国收入崩溃值压制、紧急奏折(6份)、外国贷款(5份)。全势力外交关系初始化+AI间外交互不侵犯。AI全行为(国策+建设)。排行榜+势力切换调试工具。

## v2.2.1 (2026-06-02)
全面修复：税收拉条、六维图、设计局接入。20+Bug修复。NPC数据合并。BFS跳数修复。跨区封锁修复。

## v2.2.0 (2026-05-31)
UI大改：全屏地图+侧栏部门菜单+精简顶栏。腐败度系统。SQLite持久化。战役系统完整移植。前端12文件模块化。

## v2.0-dev (2026-05-29)
Java 迁移初始版本：Spring Boot + Maven · 41文件 · 9261行。
