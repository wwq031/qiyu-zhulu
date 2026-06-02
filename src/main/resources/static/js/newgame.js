// 七域逐鹿 · Web Client
// 新游戏向导：奏折→崩溃→选区→势力

var ngStep = 0;           // 0=奏折, 1=崩溃叙事, 2=选区势力
var ngPolicies = [];      // 已批的区域列表
var ngTreasury = 200;     // 国库
var ngSupport = 35;       // 民心
var ngCorruption = 40;    // 腐败
var ngMemorials = [];     // 奏折数据（从后端加载）
var ngAllFactions = [];   // 势力列表

// ── 奏折定义 ──
var MEMORIALS = [
  {id:'northeast',icon:'🏯',name:'盛京将军 赵尔巽',title:'日俄觊觎边境，请拨军费加强边防',
   desc:'日俄自旅顺战后各踞南满北满，铁丝网已划至奉天城外三十里。臣请拨国帑四十万两，于长春—奉天—锦州一线修筑炮台兵站。若不设防，不出三年辽东恐非我有。',
   cost:40, effect:'东北边防+军事6 工业4', region:'东北'},
  {id:'huabei',icon:'🌊',name:'直隶总督 袁世凯',title:'黄河汛期将至，请拨银修缮堤防',
   desc:'黄河自铜瓦厢改道已逾半纪，豫鲁两省年年漫决。本年春雨过量，河堤报险三十七处。请拨帑银三十五万两修堤疏漕，并可保京师至德州铁路路基。',
   cost:35, effect:'华北治河+经济6 农业4', region:'华北'},
  {id:'southwest',icon:'⛰',name:'云贵总督 锡良',title:'边陲土司叛乱，请准改土归流',
   desc:'川滇黔交界土司七十二寨，自光绪末已抗粮抗税十二载。法人自滇越铁路北窥，暗输军火予土司。臣请行改土归流，设县置吏，但需饷银三十万两及练勇八千。',
   cost:30, effect:'西南改制+外交6 经济4 反腐5', region:'西南'},
  {id:'southeast',icon:'🏭',name:'两江总督 张人骏',title:'革命党煽动商埠，请派兵弹压',
   desc:'上海租界革命报纸已增至九种，同盟会密使自东京南洋潜入，联络会党、策反新军。去岁徐锡麟案震惊朝野。臣请密派缇骑赴沪宁汉三镇搜捕党人，需密费三十五万两。',
   cost:35, effect:'东南镇压+军事5 工业3', region:'东南'},
  {id:'lingnan',icon:'🌴',name:'两广总督 岑春煊',title:'法属越境侵扰，请编新军固防',
   desc:'法属安南驻军去岁越境十二次，测绘广西边境地图。琼崖海面法舰游弋不断。臣请编练新式边防军三协，购德国快炮二十四门，需饷三十万两。',
   cost:30, effect:'岭南新军+军事8 工业2', region:'岭南'},
  {id:'nanyang',icon:'⛵',name:'闽浙总督 松寿',title:'海盗猖獗侨民告急，请扩水师',
   desc:'南洋侨商禀报，马六甲至吕宋海面海盗猖獗，去年劫掠华商货船六十一艘。英荷海军以护航为名扩大巡弋。臣请拨银三十五万两购置快轮十艘，编练南洋水师护侨营。',
   cost:35, effect:'南洋水师+海军10 经济4', region:'南洋'},
  {id:'xibei',icon:'🏔',name:'陕甘总督 升允',title:'沙俄渗透边疆，请设行省治理',
   desc:'俄国自日俄战后全力东进，伊犁—喀什噶尔一线俄商队实为测绘队，已绘新疆详图七十六幅。外蒙王公暗通俄使。臣请筹设新疆行省衙门于迪化，调甘军两协驻防，需帑三十万两。',
   cost:30, effect:'西北设省+外交5 军事4 经济3', region:'西北'}
];

// ── 新游戏入口：直接进入帝国（Phase 1）──
async function showNewGameModal() {
  document.getElementById('status-text').textContent = '创建帝国...';
  closeModal('newgame-modal');
  // 自动用京师拱卫军代表中枢
  var data = await apiPost('/api/new-game', {faction_id: 'capital_garrison', policies: []});
  if (data.error) { alert(data.error); return; }
  data._empirePending = true; // 标记为帝国模式，崩溃后选势力
  renderAll(data);
  addLogEntry('🏛 宣统二年 · 帝国余晖');
}

function closeNewGameWizard() {
  closeModal('newgame-modal');
}

// ═══════════════════ Step 0: 御前奏折 ═══════════════════
function renderMemorialStep() {
  var wiz = document.getElementById('ng-wizard');
  var html = '';

  // 顶部状态栏
  html += '<div style="background:var(--panel2);border:1px solid var(--gold-dim);border-radius:6px;padding:10px 14px;margin-bottom:12px;display:flex;gap:24px;align-items:center;font-size:0.9em;">';
  html += '<span style="color:var(--gold);font-weight:bold;">🏛 养心殿 · 御前议事</span>';
  html += '<span>💰国库 <b id="ng-treasury" style="color:' + (ngTreasury<50?'var(--red)':'var(--gold)') + '">' + ngTreasury + '</b>万两</span>';
  html += '<span>❤民心 <b id="ng-support" style="color:' + (ngSupport<20?'var(--red)':'var(--text)') + '">' + ngSupport + '</b></span>';
  html += '<span>🦠腐败 <b id="ng-corruption" style="color:' + (ngCorruption>70?'var(--red)':'var(--text-dim)') + '">' + ngCorruption + '</b></span>';
  html += '<span style="margin-left:auto;font-size:0.8em;color:var(--text-dim);">已批 <b id="ng-count">' + ngPolicies.length + '</b>/4 · 至少2份</span>';
  html += '</div>';

  // 奏折列表
  html += '<div style="max-height:55vh;overflow-y:auto;">';
  MEMORIALS.forEach(function(mem, i) {
    var approved = ngPolicies.indexOf(mem.id) >= 0;
    var canAfford = ngTreasury >= mem.cost;
    var disabled = approved ? false : (!canAfford && ngPolicies.length < 2);
    var borderColor = approved ? 'var(--green)' : (disabled ? 'var(--border)' : 'var(--gold-dim)');
    var bgColor = approved ? 'rgba(76,175,80,0.08)' : 'var(--panel2)';

    html += '<div id="mem-' + mem.id + '" style="background:' + bgColor + ';border:1px solid ' + borderColor + ';border-radius:6px;padding:10px 14px;margin:6px 0;transition:all 0.2s;">';
    html += '<div style="display:flex;align-items:center;gap:8px;margin-bottom:4px;">';
    html += '<span style="font-size:1.2em;">' + mem.icon + '</span>';
    html += '<b style="color:var(--gold);">' + mem.name + '</b>';
    html += '<span style="color:var(--text-dim);font-size:0.8em;">' + mem.region + '</span>';
    html += '</div>';
    html += '<div style="font-weight:bold;margin-bottom:4px;color:var(--text);">' + mem.title + '</div>';
    html += '<div style="font-size:0.8em;color:var(--text-dim);line-height:1.5;margin-bottom:8px;">' + mem.desc + '</div>';
    html += '<div style="display:flex;justify-content:space-between;align-items:center;">';
    html += '<span style="font-size:0.8em;color:var(--text-dim);">💰' + mem.cost + '万两 · ' + mem.effect + '</span>';
    html += '<div>';
    if (approved) {
      html += '<button onclick="toggleMemorial(\'' + mem.id + '\')" style="background:rgba(76,175,80,0.2);color:var(--green);border:1px solid var(--green);padding:4px 12px;border-radius:4px;cursor:pointer;font-size:0.85em;">✅ 已准奏</button>';
    } else {
      var rejectStyle = 'background:rgba(200,60,40,0.1);color:var(--red);border:1px solid rgba(200,60,40,0.3);';
      var approveDisabled = '';
      if (!canAfford && ngPolicies.length < 2) {
        approveDisabled = 'disabled style="opacity:0.3;cursor:not-allowed;background:var(--panel2);color:var(--text-dim);border:1px solid var(--border);"';
      }
      html += '<button onclick="toggleMemorial(\'' + mem.id + '\')" ' + approveDisabled + ' style="background:var(--gold-dim);color:#000;border:none;padding:4px 12px;border-radius:4px;cursor:pointer;font-size:0.85em;margin-right:4px;">朱批：准</button>';
      html += '<button onclick="rejectMemorial(\'' + mem.id + '\')" style="' + rejectStyle + 'padding:4px 12px;border-radius:4px;cursor:pointer;font-size:0.85em;">驳</button>';
    }
    html += '</div></div></div>';
  });
  html += '</div>';

  // 底部操作
  html += '<div style="margin-top:12px;text-align:right;border-top:1px solid var(--border);padding-top:10px;">';
  if (ngPolicies.length >= 2) {
    html += '<button onclick="confirmMemorials()" style="background:var(--gold);color:#000;border:none;padding:8px 24px;border-radius:4px;cursor:pointer;font-weight:bold;font-size:0.95em;">📜 批阅完毕 · 帝国将崩</button>';
  } else {
    html += '<span style="color:var(--text-dim);font-size:0.8em;">至少准奏2份方可退朝</span>';
  }
  html += '</div>';

  wiz.innerHTML = html;
}

function toggleMemorial(id) {
  var idx = ngPolicies.indexOf(id);
  if (idx >= 0) {
    ngPolicies.splice(idx, 1);
    var mem = MEMORIALS.find(function(m) { return m.id === id; });
    ngTreasury += mem.cost;
  } else {
    if (ngPolicies.length >= 4) { statusText('最多批4份奏折'); return; }
    var mem = MEMORIALS.find(function(m) { return m.id === id; });
    if (ngTreasury < mem.cost) { statusText('国库不足！需' + mem.cost + '万两'); return; }
    ngPolicies.push(id);
    ngTreasury -= mem.cost;
  }
  renderMemorialStep();
}

function rejectMemorial(id) {
  var mem = MEMORIALS.find(function(m) { return m.id === id; });
  if (!mem) return;
  ngSupport = Math.max(0, ngSupport - 6);
  ngCorruption = Math.min(100, ngCorruption + 4);
  // 灰掉该奏折
  var el = document.getElementById('mem-' + id);
  if (el) { el.style.opacity = '0.4'; el.querySelectorAll('button').forEach(function(b) { b.disabled = true; }); }
  renderMemorialStep();
}

// ═══════════════════ Step 1: 崩溃叙事 ═══════════════════
var CRASH_NARRATIVES = [
  '公元1910年，宣统二年。帝国户部库银仅余数十万两，各省索饷无应。',
  '北洋新军六镇，五镇拒不奉诏。中枢号令，不出京师百里。',
  '列强公使团联名照会：各国将自行保护在华利益。',
  '三月，革命党武昌首义的消息传遍天下——尽管武昌此时尚无枪声，但人心已散。',
  '二十八路督军、镇守使、革命党、保皇派……各据一方，通电自保。',
  '帝国，终于崩塌。'
];

function confirmMemorials() {
  ngStep = 1;
  var wiz = document.getElementById('ng-wizard');
  var approvedNames = ngPolicies.map(function(id) {
    var m = MEMORIALS.find(function(m2) { return m2.id === id; });
    return m ? m.title : id;
  });

  var extraLines = [];
  if (ngTreasury < 40) extraLines.push('户部库银见底，中枢已无力支撑任何开销。');
  if (ngSupport < 20) extraLines.push('民变蜂起，各省咨议局通电要求立宪，京师震动。');
  if (ngCorruption > 60) extraLines.push('廷臣尽皆自谋出路，各部衙门十室九空。');

  var html = '<div style="text-align:center;padding:20px 0;">';
  html += '<h2 style="color:var(--red);margin-bottom:16px;">⚡ 帝国大崩溃 ⚡</h2>';
  html += '<div style="font-size:0.85em;color:var(--text-dim);line-height:2;max-width:500px;margin:0 auto;text-align:left;">';
  CRASH_NARRATIVES.forEach(function(line) { html += '<p>' + line + '</p>'; });
  extraLines.forEach(function(line) { html += '<p style="color:var(--red);">' + line + '</p>'; });
  html += '</div>';
  html += '<p style="color:var(--gold);margin-top:20px;">准奏 ' + ngPolicies.length + ' 份：' + approvedNames.join('、') + '</p>';
  html += '<button onclick="goToRegionSelect()" style="margin-top:16px;background:var(--gold);color:#000;border:none;padding:10px 32px;border-radius:4px;cursor:pointer;font-weight:bold;font-size:1em;">七域逐鹿 · 选择你的势力</button>';
  html += '</div>';
  wiz.innerHTML = html;
}

// ═══════════════════ Step 2: 选区 → 势力 ═══════════════════
function goToRegionSelect() {
  ngStep = 2;
  var wiz = document.getElementById('ng-wizard');
  // 按区域分组
  var byRegion = {};
  ngAllFactions.forEach(function(f) {
    var r = f.region_name || f.region || '?';
    if (!byRegion[r]) byRegion[r] = [];
    byRegion[r].push(f);
  });

  var html = '<h3 style="color:var(--gold);">选择势力</h3>';
  html += '<p style="color:var(--text-dim);font-size:0.85em;margin-bottom:12px;">帝国已崩。七域二十八路豪杰，择一而仕。</p>';

  var regionNames = {'东北大区':'northeast','华北大区':'huabei','西南大区':'southwest','东南大区':'southeast','岭南大区':'lingnan','南洋大区':'nanyang','西北大区':'xibei'};
  for (var rname in byRegion) {
    var factions = byRegion[rname];
    html += '<div style="margin:12px 0;border-top:1px solid var(--border);padding-top:8px;">';
    html += '<b style="color:var(--gold);">' + rname + '</b><span style="color:var(--text-dim);font-size:0.8em;"> · ' + factions.length + '势力</span>';
    html += '<div style="display:grid;grid-template-columns:1fr 1fr;gap:6px;margin-top:6px;">';
    factions.forEach(function(f) {
      var st = f.stats || {};
      var ns = f.national_spirit || {};
      html += '<div class="faction-card" onclick="selectFactionAndStart(\'' + f.id + '\',\'' + f.name + '\')" style="cursor:pointer;padding:8px;">';
      html += '<div class="fname" style="font-size:0.9em;">' + f.name + '</div>';
      html += '<div class="fideo" style="font-size:0.75em;">' + (f.ideology||'') + '</div>';
      html += '<div class="fstats" style="font-size:0.72em;">🏭' + (st.industry||0) + ' 🌾' + (st.agriculture||0) + ' ⚔' + (st.military||0) + ' 💰' + (st.economy||0) + ' 📖' + (st.ideology||0) + ' 🌐' + (st.diplomacy||0) + '</div>';
      if (ns.name && ns.name !== '暂无国魂') html += '<div style="font-size:0.7em;color:var(--gold);">⚜ ' + ns.name + '</div>';
      html += '</div>';
    });
    html += '</div></div>';
  }
  html += '<div style="margin-top:12px;text-align:center;"><button onclick="renderMemorialStep();ngStep=0;" style="background:none;border:1px solid var(--border);color:var(--text-dim);padding:6px 16px;border-radius:4px;cursor:pointer;">← 返回奏折</button></div>';
  wiz.innerHTML = html;
}

function selectFactionAndStart(fid, fname) {
  document.getElementById('status-text').textContent = '创建游戏中...';
  closeModal('newgame-modal');
  apiPost('/api/new-game', {faction_id: fid, policies: ngPolicies}).then(function(data) {
    if (data.error) { alert(data.error); return; }
    renderAll(data);
    addLogEntry('⚡ 帝国崩溃 · 新游戏开始 — ' + fname);
  }).catch(function(e) { alert('创建失败: ' + e.message); });
}

// ── 快速开局 ──
function quickStart() {
  // 随机批2-3份奏折
  var ids = MEMORIALS.map(function(m) { return m.id; });
  var count = Math.random() < 0.5 ? 2 : 3;
  ngPolicies = [];
  while (ngPolicies.length < count) {
    var idx = Math.floor(Math.random() * ids.length);
    if (ngPolicies.indexOf(ids[idx]) < 0) ngPolicies.push(ids[idx]);
  }
  // 跳到选区
  ngStep = 2;
  document.getElementById('newgame-modal').classList.add('show');
  try { apiGet('/api/factions').then(function(d) { ngAllFactions = d.factions || []; goToRegionSelect(); }); } catch(e) {}
}

// ── 读档（保留）──
async function showLoadModal() {
  document.getElementById('load-modal').classList.add('show');
  var data = await apiGet('/api/saves');
  var list = document.getElementById('save-list');
  if (data.error) { list.innerHTML = '<li>加载失败</li>'; return; }
  if (!data.saves || !data.saves.length) {
    list.innerHTML = '<li style="color:var(--text-dim)">暂无存档</li>';
    return;
  }
  list.innerHTML = data.saves.map(function(s) {
    return '<li onclick="loadGame(\'' + s.slot + '\')"><span>📁 <b>' + s.faction + '</b></span><span style="color:var(--text-dim)">回合' + s.turn + ' · ' + s.date + ' · 阶段' + s.phase + '</span></li>';
  }).join('');
}

async function loadGame(slot) {
  document.getElementById('status-text').textContent = '加载中...';
  var data = await apiPost('/api/load', {slot: slot});
  if (data.error) { alert(data.error); return; }
  closeModal('load-modal');
  renderAll(data);
  addLogEntry('📂 已加载存档 [' + slot + ']');
}

async function saveGame() {
  var slot = prompt('存档名称（留空=auto）：', 'auto');
  if (slot === null) return;
  var data = await apiPost('/api/save', {slot: slot});
  if (data.error) alert(data.error);
  else statusText('✅ ' + data.message);
}

// ── 选择势力（兼容旧版）──
function selectFaction(fid, el) {
  selectedFactionId = fid;
  var cards = document.querySelectorAll('.faction-card');
  cards.forEach(function(c) { c.style.borderColor = 'var(--border)'; });
  if (el) el.style.borderColor = 'var(--gold)';
  var f = ngAllFactions.find(function(x) { return x.id === fid; });
  if (f) showFactionDetailPanel(f);
}
