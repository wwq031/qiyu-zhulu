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
          const modeNames = {local:'本地模板',deepseek:'DeepSeek',openai:'OpenAI',anthropic:'Claude'};
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
  const dot = document.getElementById('home-dot');
  const txt = document.getElementById('home-conn-text');
  if (dot) { dot.className = 'dot ' + (ok ? 'on' : 'off'); }
  if (txt) { txt.textContent = ok ? '服务器已连接' : '未连接 — 请启动 python server.py'; }
}

// ── 渲染函数 ────────────────────────────────────────────────
function renderAll(data) {
  gameState = data;
  if (!data || data.error) return;

  // 合并自定义战术到全局战术定义（供所有下拉框使用）
  if (data.custom_tactics) {
    for (const [tid, td] of Object.entries(data.custom_tactics)) {
      window._tacticDefs[tid] = td;
    }
  }


  // 切换到游戏视图
  document.getElementById('home-view').style.display = 'none';
  document.getElementById('game-view').style.display = 'flex';

  // 顶栏更新
  document.getElementById('tb-faction').textContent = data.faction || '';
  document.getElementById('tb-turn').textContent = 'Turn ' + (data.turn||0);
  document.getElementById('tb-date').textContent = data.date || '';
  document.getElementById('tb-phase').textContent = data.phase_name || '';
  document.getElementById('tb-gold').textContent = '💰' + (data.treasury||0);
  document.getElementById('tb-corruption').textContent = '🦠' + (data.corruption||0) + '%';
  document.getElementById('tb-support').textContent = '❤️' + (data.population_support||0) + '%';
  document.getElementById('tb-ap').textContent = 'AP:' + (data.action_points||0);
  document.getElementById('status-text').textContent = '就绪';

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

  // 自由行动执行结果
  if (data.action_results && data.action_results.length) {
    const arDiv = document.createElement('div');
    arDiv.style.cssText = 'margin:8px 0;padding:8px;background:#1a3a2a;border-left:3px solid var(--green);border-radius:3px;';
    arDiv.innerHTML = '<div style="color:var(--green);font-weight:bold;margin-bottom:4px;">✅ 已执行操作</div>';
    data.action_results.forEach(r => {
      arDiv.innerHTML += `<div style="color:var(--text);font-size:0.85em;line-height:1.5;">${escapeHtml(String(r))}</div>`;
    });
    panel.appendChild(arDiv);
  }
  if (data.action_errors && data.action_errors.length) {
    const aeDiv = document.createElement('div');
    aeDiv.style.cssText = 'margin:8px 0;padding:8px;background:#3a1a1a;border-left:3px solid var(--red);border-radius:3px;';
    aeDiv.innerHTML = '<div style="color:var(--red);font-weight:bold;margin-bottom:4px;">⚠ 操作失败</div>';
    data.action_errors.forEach(e => {
      aeDiv.innerHTML += `<div style="color:var(--text-dim);font-size:0.85em;line-height:1.5;">${escapeHtml(String(e))}</div>`;
    });
    panel.appendChild(aeDiv);
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

  // 回合事件 → 弹窗通知
  if (data.turn_events && data.turn_events.length) {
    data.turn_events.forEach(ev => {
      const evStr = typeof ev === 'string' ? ev : (ev.text || ev.desc || ev.body || ev.title || '');
      if (evStr) showEventPopup('📋 回合事件', evStr, false);
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
          const icons = {industry:'🏭',agriculture:'🌾',military:'⚔',economy:'💰',ideology:'📖',diplomacy:'🌐',naval_power:'⚓'};
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

  // 天下传闻
  if (data.rumors && data.rumors.length) {
    const rmDiv = document.createElement('div');
    rmDiv.style.cssText = 'margin:8px 0;padding:8px;background:var(--panel2);border-left:3px solid var(--cyan);border-radius:3px;';
    rmDiv.innerHTML = '<div style="color:var(--cyan);font-weight:bold;margin-bottom:4px;">📡 天下传闻</div>';
    data.rumors.forEach(r => {
      rmDiv.innerHTML += `<div style="color:var(--text-dim);font-size:0.9em;">—— ${r}</div>`;
    });
    panel.appendChild(rmDiv);
  }

  // 游戏结束
  if (data.game_over) {
    panel.innerHTML += '\n<span style="color:var(--red)">⚡ 势力覆灭 — 游戏结束</span>';
  }

  // 子菜单渲染
  if (data.result_type && data.result_type !== 'ok') {
    renderSubmenu(data.result_type, data.data || {});
  } else {
    hideSubmenu();
  }

  // 阶段按钮
  const phaseBtns = document.getElementById('phase-btns');
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
    // 只在领土变更时全量刷新，其余情况只增量更新动态元素
    const newSig = (data.territories || []).sort().join(',');
    if (newSig !== _lastTerritorySig) {
      _lastTerritorySig = newSig;
      refreshMapOwnership();
    } else if (typeof mapInitialized !== 'undefined' && mapInitialized && leafletMap) {
      // 轻量增量更新：只刷新驻军和战役标记
      refreshDynamicMarkers(data);
    }
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
        const icons = {industry:'🏭',agriculture:'🌾',military:'⚔',economy:'💰',ideology:'📖',diplomacy:'🌐',naval_power:'⚓'};
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
  const log = document.getElementById('event-log');
  const turn = gameState ? gameState.turn || '?' : '?';
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
  const content = document.getElementById('faction-dossier-content');
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
}

function showTutorial() {
  document.getElementById('tutorial-modal').classList.add('show');
}

let eventPopupQueue = [];
let eventPopupActive = false;
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
  panel.classList.add('open');
  content.innerHTML = '<div style="color:var(--text-dim);text-align:center;padding:20px;">加载中...</div>';

  // 根据类型发送对应 action 获取菜单数据
  var actionMap = {
    military: '1', domestic: '3', diplomacy: '3', intel: '4',
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
      html += '<div class="submenu-target">国库: 💰' + t + ' | 民心: ❤️' + (data.population_support||0) + '% | 腐败: 🦠' + (data.corruption||0) + '%</div>';
      html += '<div style="font-size:0.82em;color:var(--text-dim);margin:4px 0;">';
      html += '🏭' + (s.industry||0) + ' 🌾' + (s.agriculture||0) + ' ⚔' + (s.military||0) + ' 💰' + (s.economy||0) + ' 📖' + (s.ideology||0) + ' 🌐' + (s.diplomacy||0);
      html += '</div>';
      html += '<div class="submenu-item" onclick="sendAction(\'9\')"><span class="icon">🛡</span>反腐行动 (20💰, -5~15腐败)</div>';
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

// ── 启动 ──────────────────────────────────────────────────
init();
