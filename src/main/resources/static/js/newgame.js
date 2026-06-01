// 七域逐鹿 · Web Client

// ── 新游戏 ──────────────────────────────────────────────────
async function showNewGameModal() {
  document.getElementById('newgame-modal').classList.add('show');
  var startBtn = document.getElementById('start-game-btn');
  if (startBtn) startBtn.disabled = true;
  selectedFactionId = null;

  // 并行加载势力和区域数据
  const [fData, rData] = await Promise.all([
    apiGet('/api/factions'),
    apiGet('/api/regions'),
  ]);
  if (fData.error) { alert(fData.error); return; }
  allFactions = fData.factions || [];
  window._regionData = rData.regions || [];

  // 构建区域筛选选项（含区域特征）
  const sel = document.getElementById('region-filter');
  sel.innerHTML = '<option value="all">全部区域（28势力）</option>';
  for (const r of window._regionData) {
    sel.innerHTML += `<option value="${r.id}">${r.name} · ${(r.terrain||[]).slice(0,2).join('/')} · ${r.ai_personality||''}</option>`;
  }

  // 显示区域概览
  const overview = document.getElementById('region-overview');
  if (overview) {
    overview.innerHTML = '<div style="font-size:0.8em;color:var(--text-dim);line-height:1.8;">' +
      window._regionData.map(r =>
        `<span style="margin-right:12px;white-space:nowrap;" title="${r.strategic_value||''}"><b style="color:var(--gold);">${r.name}</b>: ${r.faction_count}势力 · ${r.ai_personality||'?'}</span>`
      ).join('') + '</div>';
  }

  filterFactions();
}

function filterFactions() {
  const region = document.getElementById('region-filter').value;
  const list = document.getElementById('faction-list');
  const filtered = region === 'all'
    ? allFactions
    : allFactions.filter(f => f.region === region);

  // 显示当前区域特征
  const overview = document.getElementById('region-overview');
  if (overview && region !== 'all') {
    const rInfo = (window._regionData||[]).find(r => r.id === region);
    if (rInfo) {
      const lossColor = (rInfo.core_loss_days||60) < 60 ? 'var(--red)' : (rInfo.core_loss_days||60) < 90 ? 'var(--yellow)' : 'var(--text-dim)';
      overview.innerHTML = `<div style="background:var(--panel2);border:1px solid var(--gold-dim);border-radius:4px;padding:10px;margin-bottom:8px;">
        <div style="color:var(--gold);font-weight:bold;font-size:0.9em;margin-bottom:4px;">${rInfo.name} · ${rInfo.faction_count||4}势力</div>
        <div style="color:var(--text-dim);font-size:0.8em;line-height:1.6;">
          <div>📍 ${rInfo.strategic_value||''}</div>
          <div>⛰ 地形：${(rInfo.terrain||[]).join(' · ')}</div>
          <div>🧠 AI性格：${rInfo.ai_personality||'?'} · ${rInfo.ai_typical_op||''}</div>
          <div>🗺 扩张路径：${(rInfo.expansion_paths||[]).map(p => '<span style="color:var(--yellow);">'+p+'</span>').join(' → ')}</div>
          <div>💀 敌对势力：${(rInfo.hostile_forces||[]).join(' · ')}</div>
          <div style="color:${lossColor};">⚠ 核心沦陷：${rInfo.core_loss_days||60}天</div>
        </div>
      </div>`;
    }
  }

  list.innerHTML = filtered.map(f => {
    const st = f.stats || {};
    const evo = f.evolution || ['?','?','?'];
    const ld = f.leader || {};
    const ldInfo = ld.name ? ` @${ld.name} · ${ld.title||''}` : '';
    const diplo = f.diplomacy || '';
    const lore = f.lore || '';
    const ns2 = f.national_spirit || {};
    return `<div class="faction-card" data-id="${f.id}" onclick="selectFaction('${f.id}', this)">
      <div class="fname">${f.name}</div>
      <div class="fideo">${f.ideology}${ldInfo}</div>
      ${ns2.name && ns2.name !== '暂无国魂' ? `<div class="fstats" style="color:var(--gold);font-size:0.75em;">⚜ ${ns2.name}</div>` : ''}
      <div class="fstats">🏭${st.industry||0} 🌾${st.agriculture||0} ⚔${st.military||0} 💰${st.economy||0} 📖${st.ideology||0} 🌐${st.diplomacy||0}${('naval_power' in st)?' ⚓'+st.naval_power:''}</div>
      <div class="fstats" style="color:var(--gold-dim);font-size:0.78em;">${evo[0]} → ${evo[1]} → ★${evo[2]}★</div>
      ${lore ? `<div class="fstats" style="color:var(--text-dim);font-style:italic;font-size:0.78em;">「${lore}」</div>` : ''}
      ${diplo ? `<div class="fstats" style="color:var(--blue);font-size:0.75em;">🌐 ${diplo}</div>` : ''}
    </div>`;
  }).join('');

  // 清除旧选择
  document.querySelectorAll('.faction-card.selected').forEach(c => c.classList.remove('selected'));
  selectedFactionId = null;
  document.getElementById('start-game-btn').disabled = true;
}

function selectFaction(fid, el) {
  document.querySelectorAll('.faction-card.selected').forEach(c => c.classList.remove('selected'));
  el.classList.add('selected');
  selectedFactionId = fid;
  document.getElementById('start-game-btn').disabled = false;
  // Show faction detail
  const f = allFactions.find(x => x.id === fid);
  if (f) showFactionDetailPanel(f);
}

function showFactionDetailPanel(f) {
  let panel = document.getElementById('faction-detail-panel');
  if (!panel) {
    panel = document.createElement('div');
    panel.id = 'faction-detail-panel';
    panel.style.cssText = 'background:var(--panel2);border:1px solid var(--gold-dim);border-radius:4px;padding:14px;margin-top:10px;font-size:0.82em;max-height:300px;overflow-y:auto;';
    const list = document.getElementById('faction-list');
    list.parentNode.insertBefore(panel, list.nextSibling);
  }
  const st = f.stats || {};
  const ld = f.leader || {};
  const ns = f.national_spirit || {};
  const evo = f.evolution || ['?','?','?'];
  let html = '<h4 style="color:var(--gold);margin:0 0 8px;">' + f.name + ' <span style="color:var(--text-dim);">' + f.ideology + '</span></h4>';
  if (ld.name) {
    html += '<div style="margin-bottom:8px;padding:6px;background:var(--panel);border-radius:3px;">';
    html += '<span style="color:var(--yellow);font-weight:bold;">@' + ld.name + '</span>';
    if (ld.title) html += ' <span style="color:var(--text-dim);">' + ld.title + '</span>';
    if (ld.birth_death) html += ' <span style="color:var(--text-dim);">(' + ld.birth_death + ')</span>';
    if (ld.background) html += '<div style="color:var(--text-dim);margin-top:4px;">' + ld.background + '</div>';
    if (ld.style) html += '<div style="color:var(--purple);">✨ ' + ld.style + '</div>';
    html += '</div>';
  }
  if (ns.name && ns.name !== '暂无国魂') {
    html += '<div style="margin-bottom:8px;padding:6px;background:var(--panel);border-radius:3px;border-left:3px solid var(--gold);">';
    html += '<span style="color:var(--gold);font-weight:bold;">⚜ ' + ns.name + '</span>';
    if (ns.desc) html += '<div style="color:var(--text-dim);">' + ns.desc + '</div>';
    if (ns.effects) {
      html += '<div style="color:var(--green);">';
      for (const [k,v] of Object.entries(ns.effects)) {
        html += (v>=0?'+':'') + v + ' ' + {industry:'🏭',agriculture:'🌾',military:'⚔',economy:'💰',ideology:'📖',diplomacy:'🌐',naval_power:'⚓'}[k] + ' ';
      }
      html += '</div>';
    }
    html += '</div>';
  }
  html += '<div style="color:var(--text-dim);">📈 ' + evo[0] + ' → ' + evo[1] + ' → ★' + evo[2] + '★</div>';
  if (f.lore) html += '<div style="color:var(--text-dim);font-style:italic;margin-top:4px;">「' + f.lore + '」</div>';
  if (f.ai) html += '<div style="color:var(--purple);margin-top:4px;">🧠 AI性格: ' + f.ai + '</div>';
  if (f.warfare && f.warfare.length) html += '<div style="margin-top:4px;">⚔ 作战: ' + f.warfare.join(' · ') + '</div>';
  if (f.special_units && f.special_units.length) html += '<div>🗡 特殊兵种: ' + f.special_units.join(' · ') + '</div>';
  if (f.domestic_policy && f.domestic_policy.length) html += '<div>🏛 内政: ' + f.domestic_policy.join(' · ') + '</div>';
  if (f.social_system) html += '<div>🏛 制度: ' + f.social_system + '</div>';
  if (f.diplomacy) html += '<div>🌐 外交: ' + f.diplomacy + '</div>';
  if (f.initial_territory && f.initial_territory.length) html += '<div>📍 领土: ' + f.initial_territory.join(' · ') + '</div>';
  if (f.initial_forces && f.initial_forces.length) html += '<div>🗡 部队: ' + f.initial_forces.join(' · ') + '</div>';
  panel.innerHTML = html;
}

async function startNewGame() {
  if (!selectedFactionId) return;
  document.getElementById('status-text').textContent = '创建新游戏...';
  var data = await apiPost('/api/new-game', {faction_id: selectedFactionId});
  if (data.error) { alert(data.error); return; }
  // 防御性校验：检查返回的势力是否与请求一致
  var selFaction = allFactions.find(function(f) { return f.id === selectedFactionId; });
  var expectedName = selFaction ? selFaction.name : '';
  if (expectedName && data.faction && data.faction !== expectedName) {
    console.warn('Faction mismatch in startNewGame: requested=' + expectedName + ' returned=' + data.faction + ', reloading state...');
    data = await apiGet('/api/state');
    if (data.error || (data.faction && data.faction !== expectedName)) {
      alert('势力匹配异常，请重新开局。\n请求: ' + expectedName + '\n返回: ' + (data.faction || 'unknown'));
      location.reload();
      return;
    }
  }
  closeModal('newgame-modal');
  renderAll(data);
  addLogEntry('🆕 新游戏开始');
}

// ── 读档 ──────────────────────────────────────────────────
async function showLoadModal() {
  document.getElementById('load-modal').classList.add('show');
  const data = await apiGet('/api/saves');
  const list = document.getElementById('save-list');
  if (data.error) { list.innerHTML = '<li>加载失败</li>'; return; }
  if (!data.saves || !data.saves.length) {
    list.innerHTML = '<li style="color:var(--text-dim)">暂无存档</li>';
    return;
  }
  list.innerHTML = data.saves.map(s =>
    `<li onclick="loadGame('${s.slot}')">
      <span>📁 <b>${s.faction}</b></span>
      <span style="color:var(--text-dim)">回合${s.turn} · ${s.date} · 阶段${s.phase}</span>
    </li>`
  ).join('');
}

async function loadGame(slot) {
  document.getElementById('status-text').textContent = '加载中...';
  const data = await apiPost('/api/load', {slot});
  if (data.error) { alert(data.error); return; }
  closeModal('load-modal');
  renderAll(data);
  addLogEntry(`📂 已加载存档 [${slot}]`);
}

async function saveGame() {
  const slot = prompt('存档名称（留空=auto）：', 'auto');
  if (slot === null) return;
  const data = await apiPost('/api/save', {slot: slot || 'auto'});
  if (data.error) { alert(data.error); return; }
  document.getElementById('status-text').textContent = `已保存 → ${data.slot}`;
}

