// 七域逐鹿 · Web Client

// ── 全局状态 ────────────────────────────────────────────────
var API = 'http://localhost:5000';
var gameState = null;
var selectedFactionId = null;
var allFactions = [];
var customMode = 'auto';

var selectedDiploTarget = null;
var diploTargets = [];
var currentMenuType = null;
window.API = API;
window.gameState = gameState;

// ── 初始化 ──────────────────────────────────────────────────
async function init() {
  updateConnection(false);
  // 首页模式：检查连接但停留在首页
  await checkConnection();
  setInterval(checkConnection, 10000);
  // 自定义指令栏事件委托
  document.getElementById('dept-content').addEventListener('click', function(e) {
    var btn = e.target.closest('.btn-resupply');
    if (btn) { showResupplyPopup(btn.dataset.unitName, parseInt(btn.dataset.unitStr), parseInt(btn.dataset.unitMax)); }
  });
  // 旧版兼容
  var sp = document.getElementById('submenu-panel');
  if (sp) sp.addEventListener('click', function(e) {
    const btn = e.target.closest('.btn-resupply');
    if (btn) {
      const name = btn.dataset.unitName;
      const str = parseInt(btn.dataset.unitStr) || 0;
      const max = parseInt(btn.dataset.unitMax) || 100;
      showResupplyPopup(name, str, max);
    }
  });
}

async function checkConnection() {
  try {
    const r = await fetch(`${API}/`);
    if (r.ok) {
      updateConnection(true);
      // 加载AI配置显示
      try {
        const cfgData = await apiGet('/api/config');
        if (cfgData && cfgData.config) {
          const provider = cfgData.config.provider || 'local';
          const modeNames = window._AI_MODES;
          const el = document.getElementById('ai-mode');
          if (el) {
            el.textContent = modeNames[provider] || provider;
            el.title = 'AI GM: ' + (cfgData.providers?.[provider]?.available ? '可用' : '未配置');
          }
        }
      } catch(e) {}
      // 仅首页时自动尝试加载已有游戏
      if (!gameState) {
        try {
          const stateData = await apiGet('/api/state');
          if (stateData && !stateData.error && stateData.turn > 0) {
            gameState = stateData;
          }
        } catch(e) {}
      }
    }
  } catch (e) {
    updateConnection(false);
  }
}

function updateConnection(ok) {
  const el = document.getElementById('connection');
  if (el) { el.className = ok ? 'on' : 'off'; el.textContent = ok ? '● 已连接' : '● 未连接'; }
  var dot = document.getElementById('home-dot');
  var txt = document.getElementById('home-conn-text');
  if (dot) dot.className = 'dot ' + (ok ? 'on' : 'off');
  if (txt) txt.textContent = ok ? '服务器已连接' : '未连接 — 请启动 python server.py';
}

// ── 渲染函数 ────────────────────────────────────────────────
function renderAll(data) {
  var wasEmpire = (gameState && gameState.phase === 1); // 必须在 gameState=data 之前
  gameState = data;
  if (!data || data.error) return;
  clearEventPopups(); // 清空上次残留的弹窗队列

  // 合并自定义战术到全局战术定义（供所有下拉框使用）
  if (data.custom_tactics) {
    for (const [tid, td] of Object.entries(data.custom_tactics)) {
      window._tacticDefs[tid] = td;
    }
  }


  // 切换到游戏视图
  document.getElementById('home-view').style.display = 'none';
  document.getElementById('game-view').style.display = 'flex';

  // Phase 1 帝国模式：奏折处理
  if (data.phase === 1) {
    var sm = document.getElementById('side-menu');
    if (sm) sm.style.display = 'flex';
    document.getElementById('status-text').textContent = '🏛 帝国余晖 · Turn ' + (data.turn||0) + ' · 输入 "E" 退朝';
    var empStats = document.getElementById('empire-stats');
    if (!empStats) {
      empStats = document.createElement('div');
      empStats.id = 'empire-stats';
      empStats.style.cssText = 'position:absolute;top:8px;left:60px;z-index:1000;display:flex;gap:16px;background:rgba(17,25,34,0.9);border:1px solid var(--gold-dim);border-radius:6px;padding:6px 14px;font-size:0.85em;';
      document.getElementById('map-wrapper').appendChild(empStats);
    }
    empStats.innerHTML = '<span style="color:var(--gold);">🏛 大清帝国</span> <span>💰' + (data.treasury||0) + '万两</span> <span>❤' + (data.population_support||0) + '</span> <span>🦠' + (data.corruption||0) + '</span>';
    checkMemorial(800);
  } else if (wasEmpire && data.phase >= 2) {
    // Phase 1→2 崩溃！先弹叙事再选势力
    var sm3 = document.getElementById('side-menu');
    if (sm3) sm3.style.display = 'flex';
    var es2 = document.getElementById('empire-stats');
    if (es2) es2.remove();
    showCollapseNarrative(data);
  } else {
    var sm4 = document.getElementById('side-menu');
    if (sm4) sm4.style.display = 'flex';
    var es3 = document.getElementById('empire-stats');
    if (es3) es3.remove();
  }

  // 顶栏更新（v2.2新版可能无此元素，加保护）
  var el;
  el=document.getElementById('tb-faction'); if(el)el.textContent = data.faction || '';
  el=document.getElementById('tb-turn'); if(el)el.textContent = 'Turn ' + (data.turn||0);
  el=document.getElementById('tb-date'); if(el)el.textContent = data.date || '';
  el=document.getElementById('tb-phase'); if(el)el.textContent = data.phase_name || '';
  el=document.getElementById('tb-gold'); if(el)el.textContent = '💰' + (data.treasury||0);
  el=document.getElementById('tb-corruption'); if(el)el.textContent = '🦠' + (data.corruption||0) + '%';
  el=document.getElementById('tb-support'); if(el)el.textContent = '❤️' + (data.population_support||0) + '%';
  el=document.getElementById('tb-ap'); if(el)el.textContent = 'AP:' + (data.action_points||0);
  el=document.getElementById('status-text'); if(el)el.textContent = '就绪';

  // AI叙事弹出
  if (data.narrative) showEventPopup('📜 AI GM 裁决', data.narrative, false);
  if (data.reason) showEventPopup('⚠ GM 判定', data.reason, false);

  // 事件日志（弹窗形式）
  if (data.output && data.output.trim()) {
    addLogEntry(data.output.trim());
  }
  if (data.narrative) {
    addLogEntry(`📜 ${data.narrative}`);
  }

  // 自由行动执行结果（→日志）
  if (data.action_results && data.action_results.length) {
    data.action_results.forEach(function(r) { addLogEntry('✅ ' + String(r)); });
  }
  if (data.action_errors && data.action_errors.length) {
    data.action_errors.forEach(function(e) { addLogEntry('⚠ ' + String(e)); });
  }

  // 势力覆灭/投降事件 → 弹窗（以被消灭势力为主语）
  if (data.defeat_events && data.defeat_events.length) {
    data.defeat_events.forEach(ev => {
      const evName = ev.name || '某势力';
      const evText = ev.text || `${evName} 覆灭`;
      showEventPopup(`💀 ${evName}`, evText, false);
      addLogEntry(evText);
    });
    // 领土变动后强制刷新地图
    if (typeof mapInitialized !== 'undefined' && mapInitialized) refreshMapOwnership();
  }

  // 回合事件 → 仅日志，不弹窗
  if (data.turn_events && data.turn_events.length) {
    data.turn_events.forEach(function(ev) {
      var evStr = typeof ev === 'string' ? ev : (ev.text || ev.desc || ev.body || ev.title || '');
      if (evStr) addLogEntry(evStr);
    });
  }

  // 史诗事件 → 弹窗（HTML格式）
  if (data.epic_events && data.epic_events.length) {
    data.epic_events.forEach(ep => {
      let html = `<div style="margin-bottom:8px;">`;
      if (ep.quote) html += `<div style="color:var(--text-dim);font-style:italic;margin-bottom:8px;">「${ep.quote}」</div>`;
      if (ep.scene) html += `<div style="color:var(--text-dim);margin:4px 0;line-height:1.7;">${ep.scene}</div>`;
      if (ep.climax) html += `<div style="color:var(--cyan);margin:6px 0;font-weight:bold;">${ep.climax}</div>`;
      if (ep.finale) html += `<div style="color:var(--text);margin:4px 0;">${ep.finale}</div>`;
      if (ep.effects) {
        let eff = '';
        for (const [k,v] of Object.entries(ep.effects)) {
          const icons = window._STAT_ICONS;
          eff += (v>=0?'+':'')+v+icons[k]+' ';
        }
        html += `<div style="color:var(--green);font-size:0.85em;margin-top:4px;">[${eff.trim()}]</div>`;
      }
      html += '</div>';
      showEventPopup('⚔ ' + (ep.name || '史诗事件'), html, true);
    });
  }

  // 战役结算 → 弹窗（带授勋按钮）
  if (data.campaign_results && data.campaign_results.length) {
    data.campaign_results.forEach(cr => {
      showCampaignPopup(cr);
    });
  }

  // 事件链抉择 → 弹窗（带选项按钮）
  if (data.event_chain_choices && data.event_chain_choices.length) {
    data.event_chain_choices.forEach(ch => {
      showChainChoicePopup(ch);
    });
  }

  // 天下传闻（→日志）
  if (data.rumors && data.rumors.length) {
    data.rumors.forEach(function(r) { addLogEntry('📡 ' + r); });
  }

  // 游戏结束
  if (data.game_over) {
    addLogEntry('⚡ 势力覆灭 — 游戏结束');
  }

  // 子菜单渲染
  if (data.result_type && data.result_type !== 'ok') {
    renderSubmenu(data.result_type, data.data || {});
  } else {
    hideSubmenu();
  }

  // 阶段按钮（新版无此元素，跳过）
  var phaseBtns = document.getElementById('phase-btns');
  if (phaseBtns) {
    let pbHtml = '';
    if (data.phase >= 4) {
      pbHtml += '<button class="btn-reso" onclick="sendAction(\'9\')" title="大国博弈" style="border-left:3px solid var(--gold);">🌍 博弈</button>';
    }
    if (data.phase >= 5) {
      pbHtml += '<button class="btn-war" onclick="sendAction(\'0\')" title="终局决战" style="border-left:3px solid var(--red);">⚡ 决战</button>';
    }
    phaseBtns.innerHTML = pbHtml;
    phaseBtns.style.display = pbHtml ? 'contents' : 'none';
  }

  document.getElementById('status-text').textContent =
    `回合${data.turn||0} · ${data.faction||'?'}`;

  // 嵌入地图：确保 game-map 中总有地图
  const gmDiv = document.getElementById('game-map');
  const gmHasMap = gmDiv && gmDiv.querySelector('.leaflet-container');
  if (!gmHasMap) {
    if (typeof mapInitialized !== 'undefined' && mapInitialized) {
      // map 已在模态框中初始化，移动到 game-view
      if (typeof moveMapToGameView === 'function') moveMapToGameView();
    } else if (typeof initLeafletMap === 'function') {
      initLeafletMap('game-map');
    }
  }
  if (gmHasMap || (typeof mapInitialized !== 'undefined' && mapInitialized)) {
    // 始终全量刷新地图（/api/map 含全部势力部队，/api/state 只有玩家部队）
    refreshMapOwnership();
  }
}

function renderInfoBar(data) {
  const bar = document.getElementById('info-bar');
  if (!bar) return;
  const stats = data.stats || {};

  // 六围紧凑条
  const cfg = [
    {key:'industry', icon:'🏭', cls:'bar-ind'},
    {key:'agriculture', icon:'🌾', cls:'bar-agr'},
    {key:'military', icon:'⚔', cls:'bar-mil'},
    {key:'economy', icon:'💰', cls:'bar-eco'},
    {key:'ideology', icon:'📖', cls:'bar-ide'},
    {key:'diplomacy', icon:'🌐', cls:'bar-dip'},
  ];
  if ('naval_power' in stats) cfg.push({key:'naval_power', icon:'⚓', cls:'bar-nav'});

  let statBars = cfg.map(c => {
    const v = stats[c.key] || 0;
    return `<span style="display:inline-flex;align-items:center;gap:2px;margin-right:10px;" title="${c.key}=${v}">
      <span style="font-size:0.85em;">${c.icon}</span><span style="font-weight:bold;font-size:0.8em;min-width:18px;">${v}</span>
      <span style="display:inline-block;width:40px;height:5px;background:#0f1923;border-radius:2px;overflow:hidden;vertical-align:middle;">
        <span class="${c.cls}" style="display:block;height:100%;width:${v}%;border-radius:2px;"></span>
      </span>
    </span>`;
  }).join('');

  // 国库 + 民心 + AP + 军事科技
  const info = [];
  if (data.treasury !== undefined) info.push(`💰${data.treasury}`);
  if (data.population_support !== undefined) info.push(`❤${data.population_support}%`);
  info.push(`⚡${data.action_points||0}/${data.ap_max||3}`);
  if (data.military_tech) info.push(`🔬Lv.${data.military_tech}`);
  if (data.total_maintenance !== undefined && data.total_maintenance > 0) {
    const warn = data.total_maintenance > (data.treasury||0) * 0.5;
    info.push(`<span style="color:${warn?'var(--red)':'var(--text-dim)'};">🔧${data.total_maintenance}金</span>`);
  }
  const infoStr = info.join(' &nbsp;|&nbsp; ');

  // 领土经济摘要
  let teconLine = '';
  if (data.territory_economy) {
    const e = data.territory_economy;
    teconLine = ` <span style="color:var(--cyan);font-size:0.78em;">| 🏭${e.industry||0} 🌾${e.agriculture||0} 🧧${e.commerce||0} | 👥${e.population||0}万</span>`;
  }

  // 国家精神
  let spiritStr = '';
  const ns = data.national_spirit;
  if (ns && ns.name && ns.name !== '暂无国魂') {
    let effStr = '';
    if (ns.effects) {
      for (const [k,v] of Object.entries(ns.effects)) {
        const icons = window._STAT_ICONS;
        effStr += (v>=0?'+':'')+v+icons[k]+' ';
      }
    }
    spiritStr = ` <span style="color:var(--gold);">⚜ ${ns.name}</span> <span style="font-size:0.85em;">${effStr}</span>`;
  }

  bar.innerHTML = `<span style="margin-right:16px;">${statBars}</span><span>${infoStr}</span>${teconLine}${spiritStr}`;
  bar.style.display = 'block';
}
var _logDedup = new Set();

function addLogEntry(msg) {
  var log = document.getElementById('event-log');
  if (!log) return; // 新版UI无事件日志
  var turn = gameState ? gameState.turn || '?' : '?';
  // 去掉ANSI转义码
  const clean = msg.replace(/\x1b\[[0-9;]*m/g, '').trim();
  if (!clean) return;
  // 去重：同回合同消息不重复添加
  const dedupKey = `${turn}|${clean}`;
  if (_logDedup.has(dedupKey)) return;
  _logDedup.add(dedupKey);
  // 限制去重集合大小
  if (_logDedup.size > 200) { const it = _logDedup.values(); for (let i = 0; i < 100; i++) _logDedup.delete(it.next().value); }
  const entry = document.createElement('div');
  entry.className = 'evt';
  entry.innerHTML = `<span class="turn">T${turn}</span>${escapeHtml(clean)}`;
  log.prepend(entry);
  // 限制日志条数
  while (log.children.length > 50) log.lastChild.remove();
}

// ── 首页导航 ────────────────────────────────────────────────
function goHome() {
  document.getElementById('home-view').style.display = 'flex';
  document.getElementById('game-view').style.display = 'none';
  hideSubmenu();
}

async function quickStart() {
  // 快速开局：随机势力
  document.getElementById('status-text').textContent = '快速开局...';
  var factionsData = await apiGet('/api/factions');
  if (factionsData.error) { alert(factionsData.error); return; }
  var factions = factionsData.factions || [];
  if (!factions.length) { alert('无可用势力'); return; }
  var pick = factions[Math.floor(Math.random() * factions.length)];
  var data = await apiPost('/api/new-game', {faction_id: pick.id});
  if (data.error) { alert(data.error); return; }
  // 防御性校验：检查返回的势力是否与请求一致
  if (data.faction && data.faction !== pick.name) {
    console.warn('Faction mismatch in quickStart: requested=' + pick.name + ' returned=' + data.faction + ', reloading state...');
    data = await apiGet('/api/state');
    if (data.error || (data.faction && data.faction !== pick.name)) {
      alert('势力匹配异常，请重新开局。\n请求: ' + pick.name + '\n返回: ' + (data.faction || 'unknown'));
      location.reload();
      return;
    }
  }
  closeModal('newgame-modal');
  renderAll(data);
  addLogEntry('⚡ 快速开局 — ' + pick.name);
}

async function showFactionDossier() {
  document.getElementById('faction-dossier-modal').classList.add('show');
  const content = document.getElementById('dossier-content');
  content.innerHTML = '<span class="dim">加载中...</span>';

  // 并行加载势力和区域数据
  const [fData, rData] = await Promise.all([
    apiGet('/api/factions'),
    apiGet('/api/regions'),
  ]);
  if (fData.error) { content.innerHTML = '加载失败'; return; }

  const factions = fData.factions || [];
  const regions = rData.regions || [];
  const regionMap = {};
  for (const r of regions) {
    regionMap[r.id] = r;
  }

  // 按区域分组
  const byRegion = {};
  for (const f of factions) {
    const rKey = f.region_name || f.region || '?';
    if (!byRegion[rKey]) byRegion[rKey] = {id: f.region, factions: []};
    byRegion[rKey].factions.push(f);
  }

  let html = '';
  for (const [regionName, group] of Object.entries(byRegion)) {
    const rInfo = regionMap[group.id] || {};
    html += `<div style="margin:16px 0 8px;padding-bottom:8px;border-bottom:1px solid var(--border);">`;
    html += `<h3 style="color:var(--gold);margin:0;">${regionName}</h3>`;
    if (rInfo.strategic_value) {
      html += `<span style="color:var(--text-dim);font-size:0.82em;">${rInfo.strategic_value}</span>`;
    }
    html += ` <span style="color:var(--text-dim);font-size:0.78em;">· ${(rInfo.terrain||[]).join('·')} · AI: ${rInfo.ai_personality||'?'}</span>`;
    html += `</div>`;

    html += '<div class="faction-cards">';
    for (const f of group.factions) {
      const st = f.stats || {};
      const evo = f.evolution || ['?','?','?'];
      const ld = f.leader || {};
	      const ns = f.national_spirit || {};
      html += `<div class="faction-card" style="cursor:default;">
        <div class="fname">${f.name}</div>
        <div class="fideo">${f.ideology} ${ld.name ? '· @'+ld.name+' · '+(ld.title||'') : ''}</div>
        <div class="fstats">🏭${st.industry||0} 🌾${st.agriculture||0} ⚔${st.military||0} 💰${st.economy||0} 📖${st.ideology||0} 🌐${st.diplomacy||0}${('naval_power' in st)?' ⚓'+st.naval_power:''}</div>
        <div class="fstats" style="color:var(--gold-dim);">${evo[0]} → ${evo[1]} → <b>★${evo[2]}★</b></div>
        ${ns.name && ns.name !== '暂无国魂' ? `<div class="fstats" style="color:var(--gold);">⚜ ${ns.name} ${ns.desc||''}</div>` : ''}
        ${f.lore ? `<div class="fstats" style="color:var(--text-dim);font-style:italic;">「${f.lore}」</div>` : ''}
        ${f.ai ? `<div class="fstats" style="color:var(--purple);">🧠 ${f.ai}</div>` : ''}
        ${f.warfare && f.warfare.length ? `<div class="fstats">⚔ ${f.warfare.join(' · ')}</div>` : ''}
        ${f.special_units && f.special_units.length ? `<div class="fstats">🗡 ${f.special_units.join(' · ')}</div>` : ''}
      </div>`;
    }
    html += '</div>';
  }
  content.innerHTML = html;
}

function showRulesModal() {
  document.getElementById('rules-modal').classList.add('show');
  var el = document.querySelector('#rules-modal .rules-text');
  if (el && !el.innerHTML.trim()) {
    el.innerHTML = '<div style="line-height:1.8;color:var(--text-dim);">\
<p><b style="color:var(--gold)">五阶段</b>: 帝国余晖 → 大崩溃 → 区域统一战 → 七强并立 → 天下归一</p>\
<p><b style="color:var(--gold)">六围属性</b>: 🏭工业 🌾农业 ⚔军事 💰经济 📖思想 🌐外交</p>\
<p><b style="color:var(--gold)">行动点(AP)</b>: 每回合3点，行动消耗1点</p>\
<p><b style="color:var(--gold)">胜利条件</b>: 消灭所有势力统一七域</p>\
<p><b style="color:var(--gold)">军事</b>: 训练部队→移动→发动战役。骑兵速度2步，步兵1步，铁路加速。</p>\
<p><b style="color:var(--gold)">内政</b>: 建设工厂/军校/水利提升属性，调整税率平衡收入与民心。</p>\
<p><b style="color:var(--gold)">外交</b>: 互不侵犯/结盟/贸易协定/宣战/和谈。</p>\
<p><b style="color:var(--gold)">战役</b>: 选部队→选战术→每回合一轮。可增援/换战术/撤退。</p>\
</div>';
  }
}

function showTutorial() {
  var el = document.getElementById('tutorial-modal');
  if (el) el.classList.add('show');
}

let eventPopupQueue = [];
let eventPopupActive = false;
function clearEventPopups() { eventPopupQueue = []; eventPopupActive = false; var ov=document.getElementById('event-popup-overlay'); if(ov)ov.classList.remove('show'); }
function fmtTactics(tacticsDict) {
  if (!tacticsDict || typeof tacticsDict !== 'object') return '?';
  const vals = Object.values(tacticsDict);
  if (!vals.length) return '?';
  const names = [...new Set(vals)].map(t => (window._tacticDefs[t] || {}).name || t);
  return names.join('/');
}
function showEventPopup(title, body, isHTML) {
  eventPopupQueue.push({ title, body, isHTML });
  if (!eventPopupActive) showNextEventPopup();
}

function showNextEventPopup() {
  if (eventPopupQueue.length === 0) { eventPopupActive = false; return; }
  eventPopupActive = true;
  const ev = eventPopupQueue.shift();
  const overlay = document.getElementById('event-popup-overlay');
  const titleEl = document.getElementById('event-popup-title');
  const bodyEl = document.getElementById('event-popup-body');
  if (!overlay || !titleEl || !bodyEl) { eventPopupActive = false; return; }
  titleEl.textContent = ev.title || '事件';
  if (ev.isHTML) {
    bodyEl.innerHTML = ev.body;
  } else {
    bodyEl.textContent = ev.body;
  }
  overlay.classList.add('show');
}

function dismissEventPopup() {
  document.getElementById('event-popup-overlay').classList.remove('show');
  // 短暂延迟后显示下一个
  setTimeout(showNextEventPopup, 200);
}

// 点击遮罩层关闭
document.addEventListener('click', function(e) {
  if (e.target.id === 'event-popup-overlay') {
    dismissEventPopup();
  }
});

// ═══════════════════════════════════════ 部门面板 ═══════════════════════════════════════

function openDept(type) {
  var panel = document.getElementById('dept-panel');
  var content = document.getElementById('dept-content');
  if (typeof hideSubmenu === 'function') hideSubmenu();
  panel.classList.add('open');
  content.innerHTML = '<div style="color:var(--text-dim);text-align:center;padding:20px;">加载中...</div>';

  // 根据类型发送对应 action 获取菜单数据
  var actionMap = {
    military: '1', domestic: '2', diplomacy: '3', intel: '4',
    build: '2', tech: '8', resolutions: '7'
  };
  var action = actionMap[type] || '1';

  apiPost('/api/action', {action: action}).then(function(data) {
    if (data.error) { content.innerHTML = '<p style="color:var(--red)">错误: ' + data.error + '</p>'; return; }
    renderDeptContent(type, data);
  }).catch(function(e) {
    content.innerHTML = '<p style="color:var(--red)">连接失败</p>';
  });
}

function closeDept() {
  document.getElementById('dept-panel').classList.remove('open');
}

function renderDeptContent(type, data) {
  var content = document.getElementById('dept-content');
  var html = '';
  var titleMap = {military:'⚔ 国防部', domestic:'🏛 政府', diplomacy:'🌐 外交院', intel:'🕵 情报局', build:'🏗 建设部', tech:'🔬 科技院', resolutions:'📜 国策院'};
  html += '<h3>' + (titleMap[type]||type) + '</h3>';

  switch(type) {
    case 'military':
      var ap = data.action_points || 0;
      html += '<div class="submenu-target">AP: ' + ap + ' | 部队: ' + ((data.data||{}).unit_count||0) + '支</div>';
      html += '<div class="submenu-item" onclick="sendAction(\'1.1\')"><span class="icon">🎖</span>军队训练</div>';
      html += '<div class="submenu-item" onclick="sendAction(\'1.4\')"><span class="icon">🎯</span>军事行动</div>';
      html += '<div class="submenu-item" onclick="sendAction(\'1.2\')"><span class="icon">⚡</span>当前战争</div>';
      html += '<div class="submenu-item" onclick="sendAction(\'1.3\')"><span class="icon">📋</span>兵力部署</div>';
      html += '<div class="submenu-item" onclick="sendAction(\'1.5\')"><span class="icon">🔧</span>军事设计局</div>';
      break;
    case 'domestic':
      var t = data.treasury || 0; var s = data.stats || {};
      html += '<div class="submenu-target" style="margin-bottom:8px;">国库: 💰' + t + ' | 民心: ❤️' + (data.population_support||0) + '% | 腐败: 🦠' + (data.corruption||0) + '%</div>';
      // 六维图
      var statDefs = [
        {key:'industry',name:'工业',icon:'🏭',color:'#e67e22'},
        {key:'agriculture',name:'农业',icon:'🌾',color:'#4caf50'},
        {key:'military',name:'军事',icon:'⚔',color:'#e74c3c'},
        {key:'economy',name:'经济',icon:'💰',color:'#f1c40f'},
        {key:'ideology',name:'思想',icon:'📖',color:'#9b59b6'},
        {key:'diplomacy',name:'外交',icon:'🌐',color:'#3498db'}
      ];
      html += '<div class="stat-bars" style="margin:8px 0;">';
      statDefs.forEach(function(d) {
        var v = s[d.key] || 0;
        var pct = Math.min(100, v);
        html += '<div style="display:flex;align-items:center;gap:6px;margin:3px 0;font-size:0.78em;">';
        html += '<span style="width:36px;text-align:right;color:var(--text-dim);">' + d.icon + '</span>';
        html += '<span style="width:24px;color:var(--text-dim);">' + d.name + '</span>';
        html += '<div style="flex:1;height:12px;background:var(--bg);border-radius:6px;overflow:hidden;">';
        html += '<div style="width:' + pct + '%;height:100%;background:' + d.color + ';border-radius:6px;transition:width 0.3s;"></div>';
        html += '</div>';
        html += '<span style="width:24px;text-align:right;font-weight:bold;color:' + d.color + ';">' + v + '</span>';
        html += '</div>';
      });
      html += '</div>';
      html += '<div class="submenu-item" onclick="sendAction(\'9\')"><span class="icon">🛡</span>反腐行动 (20💰, -5~15腐败)</div>';
      // 税率拉条
      var agriRate = data.agri_tax_rate != null ? data.agri_tax_rate : 20;
      var commRate = data.commerce_tax_rate != null ? data.commerce_tax_rate : 20;
      var projIncome = data.data ? data.data.projected_income : '?';
      html += '<div class="tax-panel">';
      html += '<div class="tax-header">💰 税率调整 <span style="font-size:0.7em;color:var(--text-dim)">预计收入:' + projIncome + '</span></div>';
      html += '<div class="tax-row"><span class="tax-label">🌾 农业税</span>';
      html += '<input type="range" min="0" max="100" value="' + agriRate + '" step="5" class="tax-slider" oninput="var v=this.value;var el=document.getElementById(\'d-agri-val\');if(el)el.textContent=v+\'%\';" onchange="setTaxRateCommit(\'agri\', this.value)">';
      html += '<span class="tax-val" id="d-agri-val">' + agriRate + '%</span></div>';
      html += '<div class="tax-row"><span class="tax-label">🏪 商业税</span>';
      html += '<input type="range" min="0" max="100" value="' + commRate + '" step="5" class="tax-slider" oninput="var v=this.value;var el=document.getElementById(\'d-comm-val\');if(el)el.textContent=v+\'%\';" onchange="setTaxRateCommit(\'commerce\', this.value)">';
      html += '<span class="tax-val" id="d-comm-val">' + commRate + '%</span></div>';
      html += '<div class="tax-hint">⚠ 税率&gt;30%影响民心 | &gt;70%损害工农</div>';
      html += '</div>';
      // 国魂
      var ns = data.national_spirit;
      if (ns && ns.name && ns.name !== '暂无国魂') {
        html += '<div class="tax-panel" style="border-color:var(--gold);">';
        html += '<div class="tax-header">⚜ ' + ns.name + '</div>';
        html += '<div style="font-size:0.78em;color:var(--text-dim);line-height:1.5;">' + (ns.desc||'') + '</div>';
        if (ns.effects) {
          html += '<div style="font-size:0.75em;color:var(--gold-dim);margin-top:4px;">';
          for (var ek in ns.effects) {
            var v = ns.effects[ek];
            var icon = window._STAT_ICONS[ek] || ek;
            html += (v>0?'+':'') + v + icon + ' ';
          }
          html += '</div>';
        }
        html += '</div>';
      }
      // Phase1 奏折记录
      var policies = data.policies || [];
      var memNames = {'northeast':'东北边防','huabei':'华北治河','southwest':'西南改土归流','southeast':'东南镇压','lingnan':'岭南新军','nanyang':'南洋水师','xibei':'西北设省'};
      html += '<div style="font-size:0.72em;color:var(--text-dim);margin:4px 0;">📜 奏折: ' + (policies.length ? policies.map(function(p){return memNames[p]||p;}).join('、') : '无（快速开局随机分配）') + '</div>';
      html += '<hr><div class="section-title">自定义指令</div>';
      html += '<div style="margin-top:4px;"><input id="quick-order" style="width:100%;padding:6px;background:var(--bg);border:1px solid var(--border);color:var(--text);border-radius:3px;" placeholder="输入自由指令..."><button class="btn btn-small" style="margin-top:4px;width:100%;" onclick="var o=document.getElementById(\'quick-order\').value;if(o){document.getElementById(\'custom-input\').value=o;sendCustomOrder();closeDept();}">✧ 执行</button></div>';
      break;
    case 'diplomacy':
      var targets = (data.data||{}).diplo_targets || [];
      html += '<div class="submenu-target">外交对象: ' + targets.length + '个势力</div>';
      targets.slice(0,8).forEach(function(t,i) {
        var warTag = t.at_war ? ' [交战中]' : '';
        html += '<div class="submenu-item" onclick="sendAction(\'3.1\',{target_index:'+i+'})"><span class="icon">🤝</span>' + t.name + warTag + '<span class="cost">军' + t.military + '</span></div>';
      });
      html += '<div class="submenu-item" onclick="sendAction(\'3.5\')"><span class="icon">🌍</span>列强援助</div>';
      break;
    case 'intel':
      html += '<div class="submenu-item" onclick="sendAction(\'4.1\')"><span class="icon">🔍</span>侦察敌情</div>';
      html += '<div class="submenu-item" onclick="sendAction(\'4.2\')"><span class="icon">🛡</span>内部维稳 (5💰)</div>';
      html += '<div class="submenu-item" onclick="sendAction(\'4.3\')"><span class="icon">📡</span>邻区侦察</div>';
      html += '<div class="submenu-item" onclick="sendAction(\'4.4\')"><span class="icon">🕵</span>反间谍 (6💰)</div>';
      break;
    case 'build':
      var items = (data.data||{}).items || [];
      html += '<div class="submenu-target">建设项目: ' + items.length + '个可用</div>';
      items.forEach(function(item) {
        html += '<div class="submenu-item" onclick="sendAction(\''+item.id+'\')"><span class="icon">'+item.icon+'</span>'+item.name+'<span class="cost">'+item.cost+'💰/'+item.turns+'回合</span></div>';
      });
      break;
    case 'tech':
      var avail = (data.data||{}).available || [];
      var researched = (data.data||{}).researched || [];
      html += '<div class="submenu-target">已研发: ' + researched.length + ' | 可用: ' + avail.length + '</div>';
      avail.forEach(function(t) {
        var tag = t.prereqs_met ? '✅' : '🔒';
        html += '<div class="submenu-item" onclick="sendAction(\'8.'+t.id+'\')"><span class="icon">🔬</span>'+tag+' '+t.name+'<span class="cost">'+t.cost+'💰/'+t.turns+'回合</span></div>';
      });
      break;
    case 'resolutions':
      var resos = (data.data||{}).resolutions || [];
      var availR = resos.filter(function(r){return r.available;});
      html += '<div class="submenu-target">可用国策: ' + availR.length + '/' + resos.length + '</div>';
      availR.forEach(function(r) {
        html += '<div class="submenu-item" onclick="sendAction(\'7.'+r.id+'\')"><span class="icon">📜</span>'+r.name+'<span class="cost">'+r.category+'</span></div>';
      });
      break;
    default:
      html += '<p style="color:var(--text-dim)">' + JSON.stringify(data.output || '无数据') + '</p>';
  }
  content.innerHTML = html;
}

// ── 税率调整 ────────────────────────────────────────────────
function setTaxRate(type, value) {
  var el = document.getElementById('tax-' + type + '-val');
  if (el) el.textContent = value + '%';
}

function setTaxRateCommit(type, value) {
  apiPost('/api/action', {action: '2.tax.' + type + '.' + value}).then(function(data) {
    if (data.error) { statusText('错误: ' + data.error); return; }
    renderAll(data);
    // 如果部门面板开着，刷新它
    var panel = document.getElementById('dept-panel');
    if (panel && panel.classList.contains('open')) {
      openDept('domestic');
    }
  });
}

// ═══════════════════ Phase 1 御前奏折 ═══════════════════
var _memQueue = [];     // 待处理的奏折队列
var _memProcessed = 0;  // 已处理数
var _memTotal = 0;      // 总数
var _collapseFactions = {}; // 崩溃后可选势力

function checkMemorial(delay) {
  setTimeout(function() {
    apiPost('/api/memorial/resolve', {action: 'next'}).then(function(data) {
      if (data.error) return;
      if (data.done) { statusText('📜 奏折已全部批阅，等待帝国崩溃...'); return; }
      if (data.memorial) {
        _memProcessed = data.processed || 0;
        _memTotal = data.total || 0;
        showMemorialPopup(data.memorial, data);
      }
    });
  }, delay || 500);
}

function showMemorialPopup(mem, state) {
  var overlay = document.createElement('div');
  overlay.className = 'event-popup-overlay show';
  overlay.style.display = 'flex';
  overlay.id = 'memorial-overlay';

  var icons = {northeast:'🏯',huabei:'🌊',southwest:'⛰',southeast:'🏭',lingnan:'🌴',nanyang:'⛵',xibei:'🏔',
               flood:'🌊',revolt:'⚔',famine:'🌾',foreign:'🏴',treasury:'💰',warlord:'🗡'};
  var regNames = {northeast:'东北',huabei:'华北',southwest:'西南',southeast:'东南',lingnan:'岭南',nanyang:'南洋',xibei:'西北'};

  var html = '<div class="event-popup" style="max-width:550px;text-align:left;padding:20px;">';
  // 顶部状态
  html += '<div style="display:flex;justify-content:space-between;margin-bottom:12px;font-size:0.8em;">';
  html += '<span>💰<b>' + (state.treasury) + '</b>万两</span>';
  html += '<span>❤<b>' + (state.support) + '</b></span>';
  html += '<span>🦠<b>' + (state.corruption) + '</b></span>';
  html += '<span style="color:var(--gold-dim);">已批' + (_memProcessed) + '</span>';
  html += '</div>';
  // 奏折内容
  html += '<div style="border-left:3px solid var(--gold);padding-left:12px;margin-bottom:12px;">';
  html += '<div style="font-size:1.1em;color:var(--gold);margin-bottom:2px;">' + (icons[mem.region]||'📜') + ' ' + (mem.name||'军机处') + '</div>';
  if (mem.region) html += '<div style="font-size:0.8em;color:var(--text-dim);margin-bottom:6px;">[' + (regNames[mem.region]||mem.region) + ']</div>';
  html += '<div style="font-weight:bold;color:var(--text);margin-bottom:4px;">' + (mem.title||'') + '</div>';
  html += '<div style="font-size:0.85em;color:var(--text-dim);line-height:1.6;">' + (mem.desc||'') + '</div>';
  html += '<div style="font-size:0.8em;color:var(--gold-dim);margin-top:6px;">💰耗费 ' + (mem.cost||'?') + ' 万两</div>';
  html += '</div>';
  // 按钮
  html += '<div style="display:flex;gap:12px;justify-content:center;">';
  html += '<button id="mem-approve-btn" onclick="resolveMemorial(\'' + (mem.region||mem.memorial_id) + '\',true)" style="background:var(--gold);color:#000;border:none;padding:10px 32px;border-radius:4px;cursor:pointer;font-weight:bold;font-size:1em;">朱批：准奏</button>';
  html += '<button onclick="resolveMemorial(\'' + (mem.region||mem.memorial_id) + '\',false)" style="background:rgba(200,60,40,0.2);color:var(--red);border:1px solid var(--red);padding:10px 24px;border-radius:4px;cursor:pointer;font-size:1em;">驳</button>';
  html += '</div>';
  html += '</div>';

  overlay.innerHTML = html;
  document.body.appendChild(overlay);

  // 国库不足禁批准
  if (state.treasury < (mem.cost||0)) {
    var btn = document.getElementById('mem-approve-btn');
    if (btn) { btn.disabled = true; btn.style.opacity = '0.4'; btn.textContent = '国库不足'; }
  }
}

function resolveMemorial(id, approved) {
  var overlay = document.getElementById('memorial-overlay');
  if (overlay) overlay.remove();
  apiPost('/api/memorial/resolve', {action: 'resolve', memorial_id: id, approved: approved}).then(function(data) {
    if (data.error) { statusText('错误: ' + data.error); return; }
    statusText((approved ? '✅ 准奏 ' : '❌ 驳回 ') + id);
    addLogEntry((approved ? '📜 准奏: ' : '📜 驳回: ') + id);
    if (gameState) {
      gameState.treasury = data.treasury;
      gameState.population_support = data.support;
      gameState.corruption = data.corruption;
    }
    // 每回合只出一份奏折——不立即弹出下一份
  });
}

function showCollapseNarrative(data) {
  var treasury = data.treasury || 0;
  var support = data.population_support || 0;
  var corruption = data.corruption || 0;
  // 提取后端返回的动态崩溃消息
  var events = data.turn_events || [];
  var collapseMsg = '';
  for (var i = 0; i < events.length; i++) {
    if (events[i].indexOf('帝国崩塌') >= 0) { collapseMsg = events[i]; break; }
  }

  var html = '<div style="text-align:center;max-width:550px;margin:0 auto;">';
  html += '<h2 style="color:var(--red);margin-bottom:16px;">⚡ 帝国大崩溃 ⚡</h2>';
  html += '<div style="color:var(--text-dim);line-height:2;text-align:left;font-size:0.85em;">';
  html += '<p>宣统二年冬。帝国最后一道诏书无人接旨。</p>';
  // 动态奏折后果
  if (collapseMsg) {
    // 去掉前导"⚡ 帝国崩塌！" → 剩余按"；"和"。"拆分
    var body = collapseMsg.replace(/^⚡ 帝国崩塌！\s*/, '');
    var parts = body.split(/[；。]/);
    for (var i = 0; i < parts.length; i++) {
      var line = parts[i].trim();
      if (line && line.indexOf('二十八路')<0) {
        var color = (line.indexOf('驳：')>=0) ? 'var(--red)' : (line.indexOf('批：')>=0) ? 'var(--text-dim)' : 'var(--text)';
        html += '<p style="color:' + color + ';">' + line + '</p>';
      }
    }
  }
  // 全局后果
  if (treasury < 30) html += '<p style="color:var(--red);">户部库银见底。北洋六镇，五镇拒不奉诏。</p>';
  if (support < 15) html += '<p style="color:var(--red);">民变蜂起。各省咨议局通电自保。</p>';
  if (corruption > 70) html += '<p style="color:var(--red);">廷臣尽皆自谋出路。帝国躯壳已空。</p>';
  html += '<p>列强公使团联名照会：各国将自行保护在华利益。</p>';
  html += '<p style="color:var(--gold);font-weight:bold;text-align:center;margin-top:16px;">二十八路豪杰各据一方。帝国，终于崩塌。</p>';
  html += '</div>';
  html += '<div style="margin-top:16px;">';
  html += '<p style="color:var(--text-dim);font-size:0.8em;">国库余 ' + treasury + ' 万两 · 民心 ' + support + ' · 腐败 ' + corruption + '</p>';
  html += '<button onclick="dismissEventPopup();showPostCollapseFactionPicker();" style="margin-top:12px;background:var(--gold);color:#000;border:none;padding:10px 32px;border-radius:4px;cursor:pointer;font-weight:bold;font-size:1em;">选择崛起势力</button>';
  html += '</div></div>';
  showEventPopup('⚡ 帝国崩塌', html, true);
}

function showPostCollapseFactionPicker() {
  var html = '<div id="collapse-faction-list" style="max-height:55vh;overflow-y:auto;text-align:left;"></div>';
  showEventPopup('⚡ 帝国崩塌 · 选择你的势力', html, true);
  setTimeout(function() {
    apiGet('/api/factions').then(function(fData) {
      var factions = fData.factions || [];
      _collapseFactions = {};
      factions.forEach(function(f) { _collapseFactions[f.id] = f; });
      var byRegion = {};
      factions.forEach(function(f) {
        var r = f.region_name || f.region || '?';
        if (!byRegion[r]) byRegion[r] = [];
        byRegion[r].push(f);
      });
      var list = document.getElementById('collapse-faction-list');
      if (!list) return;
      var inner = '';
      for (var rname in byRegion) {
        inner += '<div style="margin:10px 0 6px;border-top:1px solid var(--border);padding-top:8px;"><b style="color:var(--gold);font-size:0.95em;">' + rname + '</b><span style="color:var(--text-dim);font-size:0.8em;"> · ' + byRegion[rname].length + '势力</span></div>';
        inner += '<div style="display:grid;grid-template-columns:1fr 1fr;gap:6px;">';
        byRegion[rname].forEach(function(f) {
          var st = f.stats || {};
          var ld = f.leader || {};
          var evo = f.evolution || ['?','?','?'];
          var ns = f.national_spirit || {};
          var nsName = (ns.name && ns.name !== '暂无国魂') ? ns.name : null;
          inner += '<div onclick="showFactionPickDetail(\'' + f.id + '\')" style="cursor:pointer;padding:8px 10px;background:var(--panel2);border:1px solid var(--border);border-radius:4px;transition:all 0.15s;" onmouseover="this.style.borderColor=\'var(--gold-dim)\'" onmouseout="this.style.borderColor=\'var(--border)\'">';
          inner += '<div style="font-weight:bold;color:var(--text);margin-bottom:2px;">' + f.name + '</div>';
          inner += '<div style="color:var(--text-dim);font-size:0.78em;">' + (f.ideology||'') + (ld.name ? ' · @' + ld.name : '') + '</div>';
          inner += '<div style="font-size:0.75em;color:var(--text-dim);margin:3px 0;">🏭' + (st.industry||0) + ' 🌾' + (st.agriculture||0) + ' ⚔' + (st.military||0) + ' 💰' + (st.economy||0) + ' 📖' + (st.ideology||0) + ' 🌐' + (st.diplomacy||0) + '</div>';
          inner += '<div style="font-size:0.72em;color:var(--gold-dim);">' + evo[0] + ' → ' + evo[1] + ' → ★' + evo[2] + '★</div>';
          if (nsName) inner += '<div style="font-size:0.7em;color:var(--gold);margin-top:2px;">⚜ ' + nsName + '</div>';
          inner += '</div>';
        });
        inner += '</div>';
      }
      list.innerHTML = inner;
    });
  }, 300);
}

function showFactionPickDetail(fid) {
  var f = _collapseFactions[fid];
  if (!f) return;
  var st = f.stats || {};
  var ld = f.leader || {};
  var evo = f.evolution || ['?','?','?'];
  var ns = f.national_spirit || {};
  var terr = f.initial_territory || [];
  var forces = f.initial_forces || [];

  var html = '<div style="text-align:left;max-width:450px;margin:0 auto;">';
  html += '<h3 style="color:var(--gold);margin-bottom:2px;">' + f.name + '</h3>';
  html += '<div style="color:var(--text-dim);font-size:0.85em;margin-bottom:8px;">' + (f.ideology||'') + (ld.name ? ' · @' + ld.name + ' · ' + (ld.title||'') : '') + '</div>';
  // 背景
  if (ld.background) html += '<div style="font-size:0.8em;color:var(--text-dim);margin:6px 0;padding:6px 10px;background:var(--panel2);border-radius:4px;">' + ld.background + '</div>';
  if (f.lore) html += '<div style="font-size:0.8em;color:var(--text-dim);font-style:italic;margin:4px 0;">「' + f.lore + '」</div>';
  // 六维
  html += '<div style="margin:8px 0;">';
  ['industry','agriculture','military','economy','ideology','diplomacy'].forEach(function(k) {
    var v = st[k]||0; var ic = {industry:'🏭',agriculture:'🌾',military:'⚔',economy:'💰',ideology:'📖',diplomacy:'🌐'}[k]||'?';
    html += '<span style="margin-right:12px;font-size:0.85em;">' + ic + '<b>' + v + '</b></span>';
  });
  html += '</div>';
  // 进化路径
  html += '<div style="font-size:0.82em;color:var(--gold-dim);margin:4px 0;">' + evo[0] + ' → ' + evo[1] + ' → ★' + evo[2] + '★</div>';
  // 国魂
  if (ns.name && ns.name !== '暂无国魂') {
    html += '<div style="margin:6px 0;padding:6px 10px;background:var(--panel2);border-left:3px solid var(--gold);border-radius:3px;">';
    html += '<div style="color:var(--gold);font-weight:bold;font-size:0.85em;">⚜ ' + ns.name + '</div>';
    if (ns.desc) html += '<div style="color:var(--text-dim);font-size:0.78em;">' + ns.desc + '</div>';
    html += '</div>';
  }
  // 初始领土和兵力
  html += '<div style="font-size:0.78em;color:var(--text-dim);margin:6px 0;">📍' + terr.slice(0,5).join('、') + (terr.length>5?' +'+(terr.length-5):'') + '</div>';
  html += '<div style="font-size:0.78em;color:var(--text-dim);">🗡' + forces.slice(0,4).join('、') + (forces.length>4?' +'+(forces.length-4):'') + '</div>';
  // 按钮
  html += '<div style="margin-top:12px;display:flex;gap:8px;">';
  html += '<button onclick="pickPostCollapseFaction(\'' + f.id + '\',\'' + f.name + '\')" style="flex:1;background:var(--gold);color:#000;border:none;padding:8px;border-radius:4px;cursor:pointer;font-weight:bold;">确认选择</button>';
  html += '<button onclick="dismissEventPopup();setTimeout(showPostCollapseFactionPicker,200);" style="background:var(--panel2);color:var(--text-dim);border:1px solid var(--border);padding:8px 16px;border-radius:4px;cursor:pointer;">← 返回</button>';
  html += '</div></div>';
  showEventPopup(f.name, html, true);
}

function pickPostCollapseFaction(fid, fname) {
  dismissEventPopup();
  document.getElementById('status-text').textContent = '切换势力...';
  apiPost('/api/empire/switch-faction', {faction_id: fid}).then(function(data) {
    if (data.error) { alert(data.error); return; }
    renderAll(data);
    addLogEntry('⚡ 帝国崩溃 · ' + fname + ' 崛起！');
  });
}

// ── 启动 ──────────────────────────────────────────────────
init();
