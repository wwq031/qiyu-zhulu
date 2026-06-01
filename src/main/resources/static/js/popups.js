// 七域逐鹿 · Web Client

function showCustomOrderPopup(data) {
  const cost = data.cost || {};
  const effects = data.effects || {};
  const hasCost = Object.values(cost).some(v => v !== 0);
  const hasEffects = Object.values(effects).some(v => v !== 0);
  const riskLabel = {low: '🟢 低风险', medium: '🟡 中等风险', high: '🔴 高风险'};
  const feasLabel = {high: '✅ 可行', medium: '⚠ 有一定难度', low: '⛔ 困难', impossible: '🚫 不可行'};

  let html = '';
  // 指令
  html += '<div style="color:var(--gold);font-size:0.85em;margin-bottom:6px;">📋 ' + escapeHtml(data.order || '自由行动') + '</div>';
  // 叙事
  html += '<div style="color:var(--text);line-height:1.8;margin-bottom:10px;font-size:0.95em;">' + escapeHtml(data.narrative || '') + '</div>';
  // 标签行
  html += '<div style="display:flex;flex-wrap:wrap;gap:6px;font-size:0.8em;">';
  html += '<span style="background:var(--panel2);padding:2px 8px;border-radius:3px;">' + (feasLabel[data.feasibility] || data.feasibility) + '</span>';
  html += '<span style="background:var(--panel2);padding:2px 8px;border-radius:3px;">' + (riskLabel[data.risk] || data.risk) + '</span>';
  if (data.fallback) html += '<span style="background:rgba(200,160,40,0.2);color:var(--yellow);padding:2px 8px;border-radius:3px;">⚡ 本地模板</span>';
  html += '</div>';
  // 消耗
  if (hasCost) {
    html += '<div style="margin-top:8px;font-size:0.8em;color:var(--text-dim);">💰 消耗: ';
    const costParts = [];
    for (const [k, v] of Object.entries(cost)) {
      if (v !== 0) costParts.push((v < 0 ? '' : '+') + v + ' ' + k);
    }
    html += costParts.join(' · ') + '</div>';
  }
  // 效果
  if (hasEffects) {
    html += '<div style="margin-top:4px;font-size:0.8em;color:var(--green);">📈 效果: ';
    const effParts = [];
    for (const [k, v] of Object.entries(effects)) {
      if (v !== 0) effParts.push((v > 0 ? '+' : '') + v + ' ' + k);
    }
    html += effParts.join(' · ') + '</div>';
  }
  // 特殊标记
  if (data.special) {
    html += '<div style="margin-top:6px;font-size:0.8em;color:var(--gold);">🏷 ' + escapeHtml(data.special) + '</div>';
  }
  // 资源不足警告
  if (data.warnings && data.warnings.length) {
    html += '<div style="margin-top:8px;font-size:0.8em;color:var(--red);line-height:1.6;">';
    data.warnings.forEach(w => { html += '⚠ ' + escapeHtml(w) + '<br>'; });
    html += '</div>';
  }

  // 根据操作类型调整弹窗标题
  let evtTitle = '📜 AI GM 裁决';
  const results = data.action_results || [];
  if (results.some(r => String(r).includes('吞并'))) {
    evtTitle = '💀 势力覆灭';
  } else if (results.some(r => String(r).includes('部署') || String(r).includes('创建'))) {
    evtTitle = '✨ 沙盒部署';
  } else if (results.some(r => String(r).includes('占领'))) {
    evtTitle = '🏴 领土变更';
  } else if (data.sandbox) {
    evtTitle = '🔧 沙盒操作';
  }
  showEventPopup(evtTitle, html, true);
}

function showCampaignPopup(cr) {
  const playerName = (gameState && gameState.faction) || '';
  const isPlayerInvolved = cr.is_player_attacker || cr.attacker_name === playerName || cr.defender_name === playerName;

  // AI间战斗 → 地图左下角轻量通知，自动消失
  if (!isPlayerInvolved) {
    showMapCornerNotice(cr);
    return;
  }

  const outcomeColor = {
    'annihilate':'var(--gold)','decisive_win':'var(--green)','costly_win':'var(--yellow)',
    'stalemate':'var(--text-dim)','setback':'var(--red)','rout':'var(--red)',
    'ceasefire':'var(--cyan)','attacker_occupied':'var(--green)','defender_held':'var(--red)',
    'stalemate_end':'var(--text-dim)',
  };
  const color = outcomeColor[cr.outcome] || 'var(--text)';
  let html = `<div style="color:${color};font-weight:bold;font-size:1.05em;margin-bottom:4px;">⚔ ${cr.outcome_cn} — ${cr.province_name}</div>`;
  html += `<div style="color:var(--text-dim);font-size:0.8em;margin-bottom:6px;">`;
  html += `⚡${cr.attacker_name||'攻方'} → 🛡${cr.defender_name||'守方'}</div>`;
  html += `<div style="color:var(--text-dim);font-size:0.85em;line-height:1.6;">`;
  html += `第${cr.round}轮 | 战力比 ${cr.ratio}:1<br>`;
  if (cr.is_player_attacker) {
    html += `我军损失 ${cr.atk_casualties} | 敌损 ${cr.def_casualties}`;
  } else {
    html += `${cr.attacker_name||'攻方'}损失 ${cr.atk_casualties} | ${cr.defender_name||'守方'}损失 ${cr.def_casualties}`;
  }
  if (cr.province_fell) html += `<br><span style="color:var(--gold);">🏴 ${cr.province_name} 已占领！</span>`;
  html += `</div>`;

  // 授勋按钮
  if (cr.honor_available) {
    html += `<div style="margin-top:10px;">`;
    html += `<button onclick="honorCampaign('${cr.id}', this)" `;
    html += `style="background:var(--panel2);border:1px solid var(--gold);color:var(--gold);padding:5px 14px;border-radius:4px;cursor:pointer;font-family:var(--font);font-size:0.85em;">`;
    html += `🏅 授勋参战部队（${cr.honor_cost}金）</button>`;
    html += `<span id="honor-msg-${cr.id}" style="color:var(--green);font-size:0.8em;margin-left:8px;"></span>`;
    html += `</div>`;
  }

  showEventPopup('⚔ 战役结算', html, true);
}

function showMapCornerNotice(cr) {
  const mapContainer = document.getElementById('map-container');
  if (!mapContainer) return;

  const outcomeColor = {
    'annihilate':'#d4a853','decisive_win':'#4caf50','costly_win':'#ffc107',
    'stalemate':'#888','setback':'#f44336','rout':'#f44336',
    'ceasefire':'#00bcd4','attacker_occupied':'#4caf50','defender_held':'#f44336',
    'stalemate_end':'#888',
  };
  const color = outcomeColor[cr.outcome] || '#aaa';
  const icon = cr.province_fell ? '🏴' : '⚔';

  const el = document.createElement('div');
  el.style.cssText = 'position:absolute;bottom:12px;left:12px;z-index:1500;background:rgba(20,22,28,0.94);border:1px solid rgba(255,255,255,0.15);border-radius:6px;padding:8px 14px;max-width:320px;font-size:0.8em;line-height:1.5;pointer-events:none;animation:fadeSlideIn 0.4s ease;';
  el.innerHTML = `<div style="color:${color};font-weight:bold;">${icon} ${cr.outcome_cn} · ${cr.province_name}</div>
    <div style="color:#999;font-size:0.9em;">${cr.attacker_name||'?'} → ${cr.defender_name||'?'} · 第${cr.round}轮 · 伤亡 ${cr.atk_casualties}/${cr.def_casualties}${cr.province_fell ? ' · <span style="color:#d4a853;">已占领</span>' : ''}</div>`;

  mapContainer.appendChild(el);

  // 6秒后自动消失
  setTimeout(() => {
    el.style.transition = 'opacity 0.5s';
    el.style.opacity = '0';
    setTimeout(() => { if (el.parentNode) el.remove(); }, 500);
  }, 6000);
}

async function honorCampaign(campaignId, btn) {
  btn.disabled = true;
  btn.textContent = '...';
  try {
    const resp = await fetch('/api/campaign/honor', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({campaign_id: campaignId})
    });
    const data = await resp.json();
    const msgEl = document.getElementById('honor-msg-' + campaignId);
    if (data.ok) {
      if (msgEl) { msgEl.textContent = '✅ ' + data.message; msgEl.style.color = 'var(--green)'; }
      btn.style.display = 'none';
    } else {
      if (msgEl) { msgEl.textContent = '❌ ' + (data.error || '失败'); msgEl.style.color = 'var(--red)'; }
      btn.disabled = false;
      btn.textContent = '🏅 授勋参战部队';
    }
  } catch(e) {
    btn.disabled = false;
    btn.textContent = '🏅 授勋参战部队';
  }
}

function showResupplyPopup(unitName, currentStr, maxStr) {
  const deficit = (maxStr || 100) - (currentStr || 0);
  if (deficit <= 0) {
    showToast('部队兵力已满，无需补给');
    return;
  }
  const maxCost = Math.max(1, Math.ceil(deficit / 10));
  let html = `<h3>🔧 补给 · ${escapeHtml(unitName)}</h3>`;
  html += `<p style="font-size:0.85em;color:var(--text-dim);">当前兵力: ${currentStr}/${maxStr} | 最多恢复: ${deficit}</p>`;
  html += `<p style="font-size:0.85em;color:var(--text-dim);">费率: 10兵力 = 1💰 (最低1💰)</p>`;
  html += `<label style="display:block;margin:8px 0;">恢复兵力: <input id="resupply-amount" type="range" min="1" max="${deficit}" value="${Math.min(deficit, 50)}" oninput="document.getElementById('resupply-cost').textContent=Math.max(1, Math.ceil(parseInt(this.value)/10))" style="width:200px;"> <span id="resupply-amount-val">${Math.min(deficit, 50)}</span></label>`;
  html += `<p style="font-size:0.9em;margin:8px 0;">费用: <span style="color:var(--gold);font-weight:bold;" id="resupply-cost">${Math.max(1, Math.ceil(Math.min(deficit, 50)/10))}</span>💰 (消耗1AP)</p>`;
  html += `<button class="btn-war" onclick="_doResupply('${escapeHtml(unitName).replace(/'/g, '&#39;')}')" style="width:100%;padding:8px;">确认补给</button>`;
  html += `<button onclick="dismissEventPopup()" style="width:100%;margin-top:4px;background:var(--panel);color:var(--text-dim);border:1px solid var(--border);padding:8px;cursor:pointer;border-radius:4px;">取消</button>`;
  showEventPopup('🔧 补给部队', html, true);
  document.getElementById('resupply-amount').addEventListener('input', function() {
    document.getElementById('resupply-amount-val').textContent = this.value;
    document.getElementById('resupply-cost').textContent = Math.max(1, Math.ceil(parseInt(this.value)/10));
  });
}

async function _doResupply(unitName) {
  const amount = parseInt(document.getElementById('resupply-amount').value) || 0;
  if (amount <= 0) return;
  dismissEventPopup();
  document.getElementById('status-text').textContent = '补给中...';
  const data = await apiPost('/api/action', {action: 'resupply', meta: {unit_name: unitName, amount}});
  if (data.ok) {
    showToast(data.message || '补给完成');
    renderAll(data);
  } else {
    alert(data.message || data.error || '补给失败');
  }
}

function showBattleInfoPopup(camp) {
  const remaining = Math.max(0, (camp.max_rounds || 4) - (camp.round || 0));
  const playerSide = camp.is_player_attacker ? 'attacker' : (camp.is_player_defender ? 'defender' : null);
  const playerUnits = playerSide === 'attacker' ? camp.attacker_units : (playerSide === 'defender' ? camp.defender_units : []);
  const enemyUnits = playerSide === 'attacker' ? camp.defender_units : (playerSide === 'defender' ? camp.attacker_units : []);

  let html = '';
  // 标题行
  html += `<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">`;
  html += `<div><span style="color:var(--gold);font-weight:bold;">⚔ ${escapeHtml(camp.province_name || camp.province)}</span>`;
  html += `<span style="color:var(--text-dim);font-size:0.8em;margin-left:8px;">${escapeHtml(camp.terrain || '?')}</span></div>`;
  html += `<span style="font-size:0.8em;color:var(--text-dim);">第${camp.round||0}/${camp.max_rounds||4}轮 · 预计剩${remaining}轮</span>`;
  html += `</div>`;

  // 双方信息卡片
  html += `<div style="display:flex;gap:12px;margin-bottom:10px;">`;
  html += `<div style="flex:1;background:rgba(200,80,60,0.1);border:1px solid rgba(200,80,60,0.3);padding:8px;border-radius:4px;">`;
  html += `<div style="color:#e05555;font-weight:bold;font-size:0.85em;">⚔ ${escapeHtml(camp.attacker_name)}（攻）</div>`;
  const atkUnits = camp.attacker_units || [];
  const defUnits = camp.defender_units || [];
  html += `<div style="font-size:0.75em;color:var(--text-dim);">${atkUnits.length}支部队</div>`;
  html += `</div>`;
  html += `<div style="flex:1;background:rgba(80,140,200,0.1);border:1px solid rgba(80,140,200,0.3);padding:8px;border-radius:4px;">`;
  html += `<div style="color:#5b9bd5;font-weight:bold;font-size:0.85em;">🛡 ${escapeHtml(camp.defender_name)}（守）</div>`;
  html += `<div style="font-size:0.75em;color:var(--text-dim);">${defUnits.length}支部队</div>`;
  html += `</div></div>`;

  // 部队战术列表
  html += `<div style="max-height:280px;overflow-y:auto;">`;

  // 玩家部队（可修改战术）
  if (playerSide && playerUnits.length) {
    html += `<div style="color:var(--green);font-weight:bold;font-size:0.85em;margin-bottom:6px;">🫵 我方部队（可修改战术）</div>`;
    for (let i = 0; i < playerUnits.length; i++) {
      const u = playerUnits[i];
      const currentTactic = u.tactic || 'assault';
      html += `<div style="display:flex;align-items:center;gap:8px;margin-bottom:6px;padding:4px 6px;background:var(--panel);border-radius:4px;">`;
      html += `<span style="flex:1;font-size:0.85em;color:var(--text);">${escapeHtml(u.name)}</span>`;
      html += `<span style="font-size:0.75em;color:var(--text-dim);width:50px;">${escapeHtml(u.type||'')}</span>`;
      html += `<span style="font-size:0.75em;color:var(--text-dim);width:40px;">兵${u.strength||0}</span>`;
      html += `<select id="btac-${i}" data-unit="${escapeHtml(u.name)}" style="background:var(--panel2);border:1px solid var(--border);color:var(--gold);padding:2px 4px;border-radius:3px;font-size:0.8em;font-family:var(--font);cursor:pointer;" onmousedown="event.stopPropagation()">`;
      html += buildTacOptionsHtml(currentTactic);
      html += `</select>`;
      html += `</div>`;
    }
  }

  // 敌方部队（只读显示）
  if (enemyUnits.length) {
    html += `<div style="color:var(--red);font-weight:bold;font-size:0.85em;margin:10px 0 6px;">👁 敌方部队（当前战术）</div>`;
    for (const u of enemyUnits) {
      const tName = (window._tacticDefs[u.tactic] || {}).name || u.tactic || '?';
      html += `<div style="display:flex;align-items:center;gap:8px;margin-bottom:4px;padding:4px 6px;background:var(--panel);border-radius:4px;opacity:0.75;">`;
      html += `<span style="flex:1;font-size:0.85em;color:var(--text);">${escapeHtml(u.name)}</span>`;
      html += `<span style="font-size:0.75em;color:var(--text-dim);width:50px;">${escapeHtml(u.type||'')}</span>`;
      html += `<span style="font-size:0.75em;color:var(--text-dim);width:40px;">兵${u.strength||0}</span>`;
      html += `<span style="font-size:0.8em;color:var(--gold-dim);width:60px;text-align:right;">${tName}</span>`;
      html += `</div>`;
    }
  }

  // 旁观战役
  if (!playerSide) {
    html += `<div style="color:var(--text-dim);text-align:center;padding:12px;">你未参与此战役，仅可观察</div>`;
  }

  // 增援队列
  const reinfQueue = camp.reinforcement_queue || [];
  if (reinfQueue.length) {
    html += `<div style="color:var(--cyan);font-weight:bold;font-size:0.85em;margin:10px 0 6px;">📨 增援途中</div>`;
    for (const r of reinfQueue) {
      const tName = (window._tacticDefs[r.tactic] || {}).name || r.tactic || '?';
      html += `<div style="padding:2px 6px;font-size:0.78em;color:var(--text-dim);">${escapeHtml(r.unit_name)} — ${tName} · ${r.arrives_in||'?'}回合后到达</div>`;
    }
  }
  html += `</div>`;

  // 操作按钮（仅玩家参与时显示）
  if (playerSide) {
    html += `<div style="margin-top:12px;display:flex;gap:8px;justify-content:flex-end;align-items:center;">`;
    html += `<span id="btac-msg" style="color:var(--green);font-size:0.8em;flex:1;"></span>`;
    if (playerUnits.length) {
      html += `<button onclick="submitBattleTactics('${camp.id}', this)" style="background:var(--gold);color:#000;border:none;padding:6px 14px;border-radius:4px;cursor:pointer;font-weight:bold;font-family:var(--font);font-size:0.85em;">⚙ 战术</button>`;
    }
    html += `<button onclick="submitReinforce('${camp.id}')" style="background:#1a3a2a;color:var(--green);border:1px solid var(--green);padding:6px 14px;border-radius:4px;cursor:pointer;font-family:var(--font);font-size:0.85em;">📨 增援</button>`;
    html += `<button onclick="submitRetreat('${camp.id}')" style="background:#3a1a1a;color:var(--red);border:1px solid var(--red);padding:6px 14px;border-radius:4px;cursor:pointer;font-family:var(--font);font-size:0.85em;">🏳 撤退</button>`;
    html += `</div>`;
  }

  showEventPopup('⚔ 战役详情', html, true);
}

// 战术选项构建器
function buildTacOptionsHtml(current) {
  const defs = window._tacticDefs || {};
  let opts = '';
  for (const [tid, tdef] of Object.entries(defs)) {
    const sel = tid === current ? ' selected' : '';
    opts += `<option value="${tid}"${sel}>${tdef.icon||''} ${tdef.name||tid}</option>`;
  }
  return opts;
}

// 战中战术调整提交
async function submitBattleTactics(campaignId, btn) {
  if (btn) { btn.disabled = true; btn.textContent = '调整中...'; }
  const msgEl = document.getElementById('btac-msg');
  if (msgEl) msgEl.textContent = '';

  // 收集所有下拉框的值（用 data-unit 属性映射到部队名）
  const unitTactics = {};
  const selects = document.querySelectorAll('[id^="btac-"]');
  selects.forEach(sel => {
    const unitName = sel.getAttribute('data-unit');
    if (unitName) unitTactics[unitName] = sel.value;
  });

  try {
    const resp = await fetch('/api/campaign/tactics', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({ campaign_id: campaignId, unit_tactics: unitTactics }),
    });
    const data = await resp.json();
    if (data.ok) {
      if (msgEl) { msgEl.textContent = '✅ ' + (data.message || '战术已调整'); msgEl.style.color = 'var(--green)'; }
      // 延迟关闭弹窗并刷新地图
      setTimeout(() => {
        dismissEventPopup();
        refreshMapOwnership();
      }, 1200);
    } else {
      if (msgEl) { msgEl.textContent = '❌ ' + (data.error || '调整失败'); msgEl.style.color = 'var(--red)'; }
      if (btn) { btn.disabled = false; btn.textContent = '应用战术调整'; }
    }
  } catch(e) {
    if (msgEl) { msgEl.textContent = '❌ 网络错误'; msgEl.style.color = 'var(--red)'; }
    if (btn) { btn.disabled = false; btn.textContent = '应用战术调整'; }
  }
}

// 战役撤退（地图弹窗用）
async function submitRetreat(campaignId) {
  if (!confirm('确定从该战役撤退？部队将撤回己方核心城市，士气-15。')) return;
  try {
    const resp = await fetch('/api/campaign/retreat', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({ campaign_id: campaignId }),
    });
    const data = await resp.json();
    if (data.ok) {
      dismissEventPopup();
      refreshMapOwnership();
      showEventPopup('🏳 撤退', data.message || '已撤退', false);
    } else {
      alert('撤退失败: ' + (data.error || '未知错误'));
    }
  } catch(e) {
    alert('撤退失败: 网络错误');
  }
}

// 战役增援
async function submitReinforce(campaignId) {
  try {
    const resp = await fetch('/api/campaign/reinforce', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({ campaign_id: campaignId }),
    });
    const data = await resp.json();
    if (data.ok) {
      dismissEventPopup();
      refreshState();
      showEventPopup('📨 增援', data.message || '增援已派出', false);
    } else {
      alert('增援失败: ' + (data.error || '未知错误'));
    }
  } catch(e) { alert('增援失败: 网络错误'); }
}

// ── 事件链抉择弹窗 ──
function showChainChoicePopup(ch) {
  let html = `<div style="color:var(--gold);font-weight:bold;margin-bottom:8px;">🔗 ${ch.name || '事件链抉择'}</div>`;
  html += `<div style="color:var(--text-dim);font-size:0.85em;margin-bottom:12px;">${ch.desc || '需要做出选择'}</div>`;
  html += `<div style="display:flex;gap:10px;">`;
  const choiceLabels = (ch.choices && ch.choices.length >= 2) ? ch.choices : [{label:'选项A'},{label:'选项B'}];
  const colors = ['var(--cyan)', 'var(--yellow)'];
  choiceLabels.forEach((c, i) => {
    html += `<button onclick="resolveChainChoice('${ch.chain_key}', ${i}, this)" `;
    html += `style="flex:1;background:var(--panel2);border:1px solid ${colors[i]};color:${colors[i]};padding:8px;border-radius:4px;cursor:pointer;font-family:var(--font);font-size:0.85em;">${c.label}</button>`;
  });
  html += `</div>`;
  html += `<div id="chain-msg-${ch.chain_key}" style="color:var(--green);font-size:0.8em;margin-top:8px;"></div>`;
  showEventPopup('🔗 事件链', html, true);
}

async function resolveChainChoice(chainKey, optionIndex, btn) {
  const buttons = btn.parentElement.querySelectorAll('button');
  buttons.forEach(b => b.disabled = true);
  try {
    const resp = await fetch('/api/event-chain/resolve', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({chain_key: chainKey, option_index: optionIndex})
    });
    const data = await resp.json();
    const msgEl = document.getElementById('chain-msg-' + chainKey);
    if (data.ok) {
      if (msgEl) { msgEl.textContent = data.message; msgEl.style.color = 'var(--green)'; }
      buttons.forEach(b => b.style.display = 'none');
    } else {
      if (msgEl) { msgEl.textContent = '❌ ' + (data.error || '失败'); msgEl.style.color = 'var(--red)'; }
      buttons.forEach(b => b.disabled = false);
    }
  } catch(e) {
    buttons.forEach(b => b.disabled = false);
  }
}
