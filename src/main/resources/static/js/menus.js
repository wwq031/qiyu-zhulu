// 七域逐鹿 · Web Client

// ── 子菜单渲染 ──────────────────────────────────────────────
function hideSubmenu() {
  var sm = document.getElementById('submenu-panel');
  if (sm) { sm.classList.remove('show'); sm.innerHTML = ''; }
  if (typeof closeDept === 'function') closeDept();
  currentMenuType = null;
  selectedDiploTarget = null;
  diploTargets = [];
}

function renderSubmenu(resultType, data) {
  const sm = document.getElementById('submenu-panel');
  sm.classList.add('show');
  currentMenuType = resultType;

  switch (resultType) {
    case 'military_menu':
      renderMilitaryMenu(data || {});
      break;
    case 'training_menu':
      renderTrainingMenu(data || {});
      break;
    case 'campaigns_menu':
      renderCampaignsMenu(data || {});
      break;
    case 'deployment_menu':
      renderDeploymentMenu(data || {});
      break;
    case 'operations_menu':
      renderOperationsMenu(data || {});
      break;
    case 'move_units_menu':
      renderMoveUnitsMenu(data || {});
      break;
    case 'move_destinations':
      renderMoveDestinations(data || {});
      break;
    case 'retreat_menu':
      renderRetreatMenu(data || {});
      break;
    case 'campaign_provinces':
      renderCampaignProvinces(data || {});
      break;
    case 'campaign_units':
      renderCampaignUnits(data || {});
      break;
    case 'reinforce_units':
      renderReinforceUnits(data || {});
      break;
    case 'design_bureau':
      renderDesignBureau(data || {});
      break;
    case 'design_result':
      handleDesignResult(data || {});
      break;
    case 'superpower_menu':
      renderSuperpowerMenu(data || {});
      break;
    case 'endgame_menu':
      renderEndgameMenu(data || {});
      break;
    case 'reso_menu':
      renderResoMenu(data.resolutions || []);
      break;
    case 'domestic_menu':
      renderDomesticMenu(data || {});
      break;
    case 'diplo_menu':
      renderDiploMenu(data.diplo_targets || []);
      break;
    case 'intel_menu':
      renderIntelMenu(data.intel_actions || []);
      break;
    case 'tech_menu':
      sm.innerHTML = `<h3>🔬 科技研发</h3>
        <p style="color:var(--text-dim);font-size:0.85em;">使用内政建设 [2.7] 科技研发 或 [2.11] 军事技术研究 来推进科技</p>`;
      break;
    default:
      hideSubmenu();
  }
}

// ── 发动战役（省份级）────────────────────────────────────────
function renderCampaignProvinces(data) {
  const sm = document.getElementById('submenu-panel');
  const provinces = data.provinces || [];
  if (!provinces.length) {
    sm.innerHTML = '<h3>⚔ 发动战役</h3><p style="color:var(--text-dim)">当前没有可攻击的敌方省份。请先调动部队到边境。</p>';
    return;
  }
  let html = `<h3>⚔ 发动战役 · 步骤1/3</h3>
    <div class="submenu-target-count">AP:${data.ap||0} · 可选目标省份: ${provinces.length} 个</div>
    <div style="margin:6px 0;font-size:0.82em;color:var(--text-dim);">选择要攻击的敌方省份：</div>`;
  for (let i = 0; i < provinces.length; i++) {
    const p = provinces[i];
    const icon = p.type === 'pass' ? '▲' : p.type === 'port' ? '◎' : '○';
    const campTag = p.in_campaign ? ' <span style="color:var(--yellow);font-size:0.75em;">[战役中]</span>' : '';
    html += `<div class="submenu-item" onclick="sendAction('1.4.1.P.${i+1}')" style="${p.in_campaign?'opacity:0.5':''}">
      <span class="sm-idx">[${i+1}]</span>
      <span class="sm-name">${icon} ${p.name} <span style="font-size:0.75em;color:var(--text-dim);">[${p.terrain||'?'}]</span></span>
      <span class="sm-info">🏴 ${p.owner||'?'} ${campTag}</span>
    </div>`;
  }
  html += '<div class="submenu-tactics"><span onclick="sendAction(\'1.4\')" style="cursor:pointer;color:var(--text-dim);">← 返回军事行动</span></div>';
  sm.innerHTML = html;
}

function renderCampaignUnits(data) {
  const sm = document.getElementById('submenu-panel');
  const target = data.target || {};
  const units = data.available_units || [];
  const pidx = data.province_index;
  const tactics = data.tactics || [];
  const defaultTac = data.default_tactic || 'assault';
  if (!units.length) {
    sm.innerHTML = '<h3>⚔ 发动战役</h3><p style="color:var(--red)">没有可用的攻击部队（需在3回合距离内且未在行军/战斗中）。</p>';
    return;
  }
  let html = `<h3>⚔ 发动战役 · 步骤2/3</h3>
    <div class="submenu-target-count" style="color:var(--red);">🎯 目标: ${target.name||'?'} [${target.terrain||'?'}] · 守方: ${target.owner||'?'}</div>
    <div style="margin:6px 0;font-size:0.82em;color:var(--text-dim);">选择部队和战术（每支部队独立选择），然后点击发动：</div>`;
  // 构建战术选项字符串
  let tacOptions = '';
  for (const t of tactics) {
    const sel = t.id === defaultTac ? ' selected' : '';
    tacOptions += `<option value="${t.id}"${sel} title="${t.pro} · ${t.con}">${t.icon} ${t.name}</option>`;
  }
  for (let i = 0; i < units.length; i++) {
    const u = units[i];
    html += `<div class="submenu-item" id="camp-unit-${i}" style="cursor:pointer;">
      <span class="sm-idx" onclick="toggleCampUnit(${i})"><input type="checkbox" id="camp-cb-${i}" style="pointer-events:none;" onchange="return false;"></span>
      <span class="sm-name" onclick="toggleCampUnit(${i})">${u.icon||''} ${u.name} <span style="font-size:0.75em;color:var(--text-dim);">[${u.type_name||''}]</span></span>
      <span class="sm-info" onclick="toggleCampUnit(${i})">攻${u.attack}防${u.defense} 兵${u.strength}/${u.max_strength}士${u.morale} 距${u.distance}回合</span>
      <select id="camp-tac-${i}" class="tactic-select"
        style="margin-left:6px;font-size:0.78em;background:var(--panel2);color:var(--text);border:1px solid var(--border);border-radius:3px;padding:2px 4px;cursor:pointer;"
        onclick="event.stopPropagation();">
        ${tacOptions}
      </select>
    </div>`;
  }
  html += `<div style="margin-top:10px;">
    <button class="btn-war" onclick="launchCampaign(${pidx})" style="width:100%;padding:10px;font-size:1em;">⚔ 发动战役</button>
  </div>
  <div class="submenu-tactics"><span onclick="sendAction('1.4.1')" style="cursor:pointer;color:var(--text-dim);">← 返回选择省份</span></div>`;
  sm.innerHTML = html;
  window._campSelectedUnits = [];
}

function toggleCampUnit(idx) {
  if (!window._campSelectedUnits) window._campSelectedUnits = [];
  const i = window._campSelectedUnits.indexOf(idx);
  if (i >= 0) {
    window._campSelectedUnits.splice(i, 1);
    document.getElementById('camp-cb-' + idx).checked = false;
    document.getElementById('camp-unit-' + idx).style.background = '';
  } else {
    window._campSelectedUnits.push(idx);
    document.getElementById('camp-cb-' + idx).checked = true;
    document.getElementById('camp-unit-' + idx).style.background = 'var(--panel2)';
  }
}

async function launchCampaign(pidx) {
  const selected = window._campSelectedUnits || [];
  if (!selected.length) {
    alert('请至少选择一支部队');
    return;
  }
  // 收集每支部队的战术选择
  const tactics = {};
  for (const idx of selected) {
    const sel = document.getElementById('camp-tac-' + idx);
    tactics[idx] = sel ? sel.value : 'assault';
  }
  const uidxs = selected.join(',');
  const action = `1.4.1.U.${pidx+1}.${uidxs}`;
  document.getElementById('status-text').textContent = '发动战役...';
  const data = await apiPost('/api/action', {action, meta: {tactics}});
  if (!data.error) {
    renderAll(data);
    hideSubmenu();
  } else {
    alert('战役发动失败: ' + (data.error || '未知错误'));
  }
}

function renderResoMenu(resolutions) {
  const sm = document.getElementById('submenu-panel');
  if (!resolutions.length) {
    sm.innerHTML = '<h3>📜 国策决议</h3><p style="color:var(--text-dim)">暂无可选决议</p>';
    return;
  }

  let html = '<h3>📜 国策决议</h3>';
  html += `<div class="submenu-target-count">可选: ${resolutions.filter(r=>r.available).length} / 总计: ${resolutions.length}</div>`;

  for (let i = 0; i < resolutions.length; i++) {
    const r = resolutions[i];
    const cls = r.available ? '' : ' locked';
    const effStr = r.effects ? Object.entries(r.effects).map(([k,v]) => {
      const vn = Number(v); return (vn>=0?'+':'') + vn + ' ' + k;
    }).join(' ') : '';
    const onclick = r.available ? `onclick="enactResolution(${i})"` : '';

    html += `<div class="submenu-item${cls}" ${onclick}>
      <span class="sm-idx">[${i+1}]</span>
      <span class="sm-name">${r.available ? '' : '🔒 '}${r.name}</span>
      <span class="sm-info">
        ${r.executed ? '<span style="color:var(--green)">✓已执行</span>' : ''}
        ${r.available ? `<span class="sm-effect">${effStr}</span>` : ''}
        ${r.missing && r.missing.length ? `<span class="sm-missing">需: ${r.missing.join(', ')}</span>` : ''}
      </span>
    </div>`;
  }

  sm.innerHTML = html;
}

async function enactResolution(idx) {
  const action = `7.${idx + 1}`;
  document.getElementById('status-text').textContent = `颁布决议...`;
  const data = await apiPost('/api/action', {action});
  if (!data.error) renderAll(data);
  else alert('决议失败: ' + (data.error || '未知错误'));
}

function renderDomesticMenu(data) {
  const sm = document.getElementById('submenu-panel');
  let builds;
  if (data && data.items && data.items.length) {
    // 使用服务器提供的建设项目列表
    builds = data.items.map(it => ({
      id: it.id,
      name: it.name,
      cost: it.cost ? it.cost + '金' : '—',
      effect: '',
      turns: it.turns ? it.turns + '回合' : '—',
      desc: '',
      icon: it.icon || '',
      needs_province: it.needs_province || false,
    }));
  } else {
    builds = [
      {id:'2.1', name:'兴建工厂', cost:'10金', effect:'+3~8 工业', turns:'6回合', desc:'提升工业产能', needs_province:true},
      {id:'2.2', name:'农田水利', cost:'5金', effect:'+2~5 农业', turns:'3回合', desc:'提升粮食产出', needs_province:true},
      {id:'2.3', name:'开办军校', cost:'8金', effect:'+2~6 军事', turns:'4回合', desc:'提升军事素养', needs_province:true},
      {id:'2.4', name:'统一货币', cost:'15金', effect:'+3~8 经济', turns:'8回合', desc:'稳定财政体系'},
      {id:'2.5', name:'宣传教育', cost:'5金', effect:'+2~6 思想', turns:'3回合', desc:'凝聚意识形态'},
      {id:'2.6', name:'遣使修好', cost:'8金', effect:'+2~5 外交', turns:'2回合', desc:'改善对外关系'},
      {id:'2.7', name:'科技研发', cost:'—', effect:'解锁科技', turns:'—', desc:'研究新技术'},
      {id:'2.8', name:'招募步兵团', cost:'8金', effect:'+3~6军事 +1步兵', turns:'2回合', desc:'扩充陆军'},
      {id:'2.9', name:'组建骑兵队', cost:'12金', effect:'+5~8军事 +1骑兵', turns:'3回合', desc:'组建机动部队'},
      {id:'2.10', name:'炮兵工厂', cost:'18金', effect:'+8~12军事 +1炮兵', turns:'5回合', desc:'发展重火力'},
      {id:'2.11', name:'军事技术研究', cost:'15金', effect:'+2~4军事 科技+1', turns:'4回合', desc:'提升军事科技等级'},
    ];
  }

  let html = '<h3>🏗 内政建设</h3>';
  html += `<div class="submenu-target-count">${builds.length} 个建设项目</div>`;

  for (let i = 0; i < builds.length; i++) {
    const b = builds[i];
    const icon = b.icon || '';
    const tagHtml = b.needs_province ? ' <span style="color:var(--gold);font-size:0.7em;">📍选址</span>' : '';
    html += `<div class="submenu-item" onclick="buildItem('${b.id}', ${b.needs_province})">
      <span class="sm-idx">[${i+1}]</span>
      <span class="sm-name">${icon} ${b.name}${tagHtml}</span>
      <span class="sm-info">
        <span class="sm-cost">💰${b.cost}</span>
        ${b.effect ? `<span class="sm-effect">${b.effect}</span>` : ''}
        <span class="sm-turns">⏱${b.turns}</span>
      </span>
    </div>`;
  }

  sm.innerHTML = html;
}

function selectDiploTarget(idx) {
  selectedDiploTarget = idx;
  renderDiploMenu(diploTargets);
}

function renderDiploMenu(targets) {
  diploTargets = targets;
  const sm = document.getElementById('submenu-panel');
  const diploActions = [
    {sub:'1', name:'互不侵犯条约', desc:'外交≥40 · 消耗15💰 · 维持12回合', icon:'🕊', cls:'btn-diplo'},
    {sub:'6', name:'贸易协定', desc:'外交≥35 · 消耗8💰 · 每回合+3💰', icon:'📈', cls:'btn-diplo'},
    {sub:'2', name:'军事同盟', desc:'外交≥60 · 消耗25💰 · 维持16回合', icon:'🤝', cls:'btn-diplo'},
    {sub:'5', name:'列强援助', desc:'外交≥50 · 消耗5💰 · 获得物资', icon:'🏦', cls:'btn-diplo'},
    {sub:'3', name:'宣战', desc:'对目标势力正式宣战', icon:'⚔', cls:'btn-war'},
    {sub:'4', name:'和谈', desc:'需处于战争 · 外交≥35 · 消耗10💰', icon:'☮', cls:'btn-green'},
    {sub:'7', name:'暂息干戈', desc:'提议休战3回合 · 消耗1AP · 互不侵犯', icon:'🏳', cls:'btn-diplo'},
  ];

  let html = '<h3>🌐 外交斡旋</h3>';
  html += `<div class="submenu-target-count">可选外交目标: ${targets.length} 个</div>`;

  if (targets.length === 0) {
    html += '<p style="color:var(--text-dim)">暂无可用外交目标</p>';
  } else {
    for (let i = 0; i < targets.length; i++) {
      const t = targets[i];
      const warTag = t.at_war ? ' <span style="color:var(--red)">[交战中]</span>' : '';
      const pactTag = t.pact ? ` <span style="color:var(--green)">[${t.pact}]</span>` : '';
      const truceTag = t.has_truce ? ' <span style="color:var(--yellow)">[休战中]</span>' : '';
      const selected = selectedDiploTarget === i;
      html += `<div class="submenu-item diplo-target${selected ? ' selected' : ''}" onclick="selectDiploTarget(${i})" style="cursor:pointer;">
        <span class="sm-idx">[${i+1}]</span>
        <span class="sm-name">${t.name}${warTag}${pactTag}${truceTag}</span>
        <span class="sm-info">
          关系<span style="color:${t.relation>=0?'var(--green)':'var(--red)'}">${t.relation>0?'+':''}${t.relation}</span>
          军<span style="color:var(--red)">${t.military}</span>
          <span style="color:var(--gold);margin-left:4px;">▶</span>
        </span>
      </div>`;

      // 选中后显示行动按钮
      if (selected) {
        html += `<div class="diplo-actions-bar">`;
        for (const a of diploActions) {
          html += `<button class="btn ${a.cls}" onclick="event.stopPropagation();sendAction('3.${a.sub}.${i+1}')" title="${a.desc}" style="font-size:0.82em;padding:5px 10px;margin:2px;">${a.icon} ${a.name}</button>`;
        }
        html += `</div>`;
      }
    }
  }

  html += `<div style="margin-top:10px;font-size:0.78em;color:var(--text-dim);">
    点击目标势力 → 选择外交行动类型。也可通过自定义指令输入 <code style="color:var(--yellow)">*向【势力名】提议互不侵犯</code> 由AI GM裁决。</div>`;

  sm.innerHTML = html;
}

function renderIntelMenu(actions) {
  const sm = document.getElementById('submenu-panel');
  let html = '<h3>🔍 情报总局</h3>';

  if (!actions.length) {
    // Fallback actions
    actions = [
      {id:'4.1', name:'侦察敌情', desc:'对同区敌对势力详细侦察', cost:'1AP'},
      {id:'4.2', name:'内部维稳', desc:'消耗5💰 降低叛乱风险 思想+民心↑', cost:'1AP+5💰'},
      {id:'4.3', name:'邻区侦察', desc:'侦察相邻区域势力概况', cost:'1AP'},
      {id:'4.4', name:'反间谍行动', desc:'消耗6💰 清除敌方间谍网络', cost:'1AP+6💰'},
    ];
  }

  for (let i = 0; i < actions.length; i++) {
    const a = actions[i];
    html += `<div class="submenu-item" onclick="sendAction('${a.id}')">
      <span class="sm-idx">[${i+1}]</span>
      <span class="sm-name">${a.name}</span>
      <span class="sm-info">
        <span class="sm-desc">${a.desc}</span>
        <span class="sm-cost">${a.cost}</span>
      </span>
    </div>`;
  }
  html += `<div style="margin-top:8px;font-size:0.78em;color:var(--text-dim);">
    也可使用自由指令 <code style="color:var(--yellow)">*情报行动</code> 触发AI GM裁决</div>`;

  sm.innerHTML = html;
}

let _pendingBuildId = null;

async function buildItem(buildId, needsProvince) {
  if (needsProvince) {
    // 地块级建设：先选省份
    _pendingBuildId = buildId;
    const provs = getPlayerProvinceOptions();
    if (!provs.length) { alert('没有可用的建设省份'); return; }
    let html = '<h3>📍 选择建设省份</h3>';
    html += `<div class="submenu-target-count">为「${buildId}」选择目标省份</div>`;
    for (let i = 0; i < provs.length; i++) {
      const pv = provs[i];
      const names = {'2.1':'🏭','2.2':'🌾','2.3':'🎖'};
      const tag = names[buildId] || '';
      html += `<div class="submenu-item" onclick="confirmBuildProvince('${pv.id}')">
        <span class="sm-idx">[${i+1}]</span>
        <span class="sm-name">${pv.name}</span>
        <span class="sm-info" style="font-size:0.75em;color:var(--text-dim);">
          ${tag}工${pv.industry||0} 农${pv.agriculture||0} 商${pv.commerce||0}
          ${pv.railway>0?' 🚂Lv'+pv.railway:''}
          ${pv.port>0?' ⚓Lv'+pv.port:''}
        </span>
      </div>`;
    }
    html += `<div class="submenu-item" onclick="_pendingBuildId=null;hideSubmenu();"><span class="sm-idx">[B]</span><span class="sm-name"> 返回</span></div>`;
    document.getElementById('submenu-panel').innerHTML = html;
    return;
  }

  // 全局建设：直接执行
  await doBuild(buildId, null);
}

function getPlayerProvinceOptions() {
  // 从 ownedBy 中收集玩家控制的所有省份
  const pids = [];
  for (const [pid, o] of Object.entries(ownedBy)) {
    if (o.isPlayer) pids.push(pid);
  }
  const provs = [];
  for (const pid of pids) {
    const p = mapProvinceData[pid];
    if (p) provs.push({id: pid, name: p.name || pid, district: p.district||'', industry: p.industry||0, agriculture: p.agriculture||0, commerce: p.commerce||0, railway: p.railway||0, port: p.port||0});
  }
  provs.sort((a,b) => (b.industry||0) - (a.industry||0));
  return provs;
}

async function confirmBuildProvince(pid) {
  const bid = _pendingBuildId;
  _pendingBuildId = null;
  if (!bid) return;
  await doBuild(bid, pid);
}

async function doBuild(buildId, provincePid) {
  document.getElementById('status-text').textContent = `建设中...`;
  const body = {action: buildId};
  if (provincePid) body.meta = {province: provincePid};
  const data = await apiPost('/api/action', body);
  if (!data.error) {
    renderAll(data);
    if (data.result_type === 'ok') hideSubmenu();
  } else {
    alert('建设失败: ' + (data.error || '未知错误'));
  }
}

// ── 军事统帅部（4大板块）────────────────────────────────────
function renderMilitaryMenu(data) {
  const sm = document.getElementById('submenu-panel');
  const sections = data.sections || [];
  let html = `<h3>⚔ 军事统帅部</h3>
    <div class="submenu-target-count">AP:${data.ap||0} · 部队:${data.unit_count||0}支 · 战役:${data.campaign_count||0}进行中 · 训练:${data.training_count||0}队列</div>`;

  for (const sec of sections) {
    html += `<div class="submenu-item" onclick="sendAction('${sec.id}')" style="padding:14px 12px;">
      <span style="font-size:1.3em;margin-right:10px;">${sec.icon}</span>
      <span class="sm-name" style="font-size:1em;font-weight:bold;">${sec.name}</span>
      <span class="sm-info" style="font-size:0.8em;">${sec.desc}</span>
    </div>`;
  }
  sm.innerHTML = html;
}

// ── 军队训练 ────────────────────────────────────────────────
function renderTrainingMenu(data) {
  const sm = document.getElementById('submenu-panel');
  const queue = data.queue || [];
  const unitTypes = data.unit_types || [];
  const locations = data.locations || [];
  const selectingLoc = data.selecting_location;
  const selUnitType = data.unit_type;
  const selUnitName = data.unit_name || '';

  const typeLetters = {infantry:'I', cavalry:'C', artillery:'A', engineer:'E', naval:'N'};

  let html = `<h3>🎖 军队训练</h3>
    <div class="submenu-target-count">国库:${data.treasury||0}💰 · AP:${data.ap||0}</div>`;

  // 训练队列
  if (queue.length > 0) {
    html += '<div style="margin:8px 0;font-size:0.82em;color:var(--text-dim);">训练队列:</div>';
    for (const t of queue) {
      const pct = Math.round((1 - t.turns_left / t.total_turns) * 100);
      html += `<div style="padding:4px 12px;font-size:0.8em;color:var(--text-dim);">
        ${t.icon||''}${t.name} @${t.location} [${'█'.repeat(Math.round(pct/10))}${'░'.repeat(10-Math.round(pct/10))}] 剩余${t.turns_left}回合</div>`;
    }
  }

  // 兵种选择
  if (!selectingLoc) {
    html += '<div style="margin:8px 0;font-size:0.82em;color:var(--text-dim);">训练新部队（消耗1AP）：</div>';
    for (const ut of unitTypes) {
      html += `<div class="submenu-item" onclick="sendAction('1.1.${ut.letter}')">
        <span class="sm-idx">${ut.icon||''}</span>
        <span class="sm-name">${ut.name}</span>
        <span class="sm-info">
          <span class="sm-cost">💰${ut.cost}</span>
          <span class="sm-cost" style="color:var(--text-dim);">🔧${ut.maintenance_cost||0}金/回合</span>
          <span class="sm-effect">攻${ut.atk}防${ut.def}士${ut.morale}经${ut.exp}</span>
          <span class="sm-turns">⏱${ut.turns}回合</span>
        </span>
      </div>`;
    }
  }

  // 部署地点选择
  if (selectingLoc && locations.length > 0) {
    html += `<div style="margin:8px 0;color:var(--gold);">选择「${selUnitName}」部署地点：</div>`;
    for (let i = 0; i < locations.length; i++) {
      const l = locations[i];
      html += `<div class="submenu-item" onclick="sendAction('1.1.${typeLetters[selUnitType] || selUnitType}.${i+1}')">
        <span class="sm-idx">[${i+1}]</span>
        <span class="sm-name">${l.name}</span>
        <span class="sm-info">${l.terrain||''} ${l.garrison_count ? '[驻'+l.garrison_count+'支]' : ''}</span>
      </div>`;
    }
  }

  if (!selectingLoc && locations.length === 0) {
    html += '<p style="color:var(--red);font-size:0.85em;">⚠ 没有可用的部署地点（需要控制至少一个省份）</p>';
  }

  html += `<div class="submenu-tactics"><span onclick="sendAction('1')" style="cursor:pointer;color:var(--text-dim);">← 返回军事统帅部</span></div>`;
  sm.innerHTML = html;
}

// ── 当前战争 ────────────────────────────────────────────────
function renderCampaignsMenu(data) {
  const sm = document.getElementById('submenu-panel');
  const ongoing = data.ongoing || [];
  const completed = data.completed || [];

  let html = '<h3>⚡ 当前战争</h3>';

  if (!data.has_ongoing && completed.length === 0) {
    html += '<p style="color:var(--text-dim);">暂无战役记录。通过 军事行动→发动战役 开战。</p>';
  }

  if (ongoing.length > 0) {
    html += `<div class="submenu-target-count">进行中的战役（${ongoing.length}场）：</div>`;
    for (let i = 0; i < ongoing.length; i++) {
      const c = ongoing[i];
      html += `<div style="background:var(--panel2);border:1px solid var(--border);border-radius:4px;padding:10px;margin:6px 0;font-size:0.82em;">
        <div style="color:var(--yellow);font-weight:bold;">[${i+1}] ${c.province_name} <span style="color:var(--text-dim);">[${c.terrain}] 第${c.round}轮</span></div>
        <div>攻方：${c.attacker_units}支部队 总兵力${c.attacker_strength}% 战术：${fmtTactics(c.attacker_tactics)||'?'}</div>
        <div>守方：${c.defender_name} ${c.defender_units}支部队 总兵力${c.defender_strength}% 战术：${fmtTactics(c.defender_tactics)||'?'}</div>
        ${(c.atk_casualties||c.def_casualties) ? `<div style="color:var(--red);">累计损失：我军${c.atk_casualties} 敌军${c.def_casualties}</div>` : ''}
        <div style="margin-top:6px;display:flex;gap:6px;">
          <button onclick="sendAction('1.2.R.${i+1}')" style="background:#3a1a1a;color:var(--red);border:1px solid var(--red);border-radius:3px;padding:3px 8px;cursor:pointer;font-size:0.8em;">🏳 撤退</button>
          <button onclick="sendAction('1.2.S.${i+1}')" style="background:#1a2a1a;color:var(--green);border:1px solid var(--green);border-radius:3px;padding:3px 8px;cursor:pointer;font-size:0.8em;">📨 增援</button>
        </div>
      </div>`;
    }
  }

  if (completed.length > 0) {
    html += '<div style="margin-top:8px;font-size:0.8em;color:var(--text-dim);">已结束的战役：</div>';
    for (const c of completed) {
      html += `<div style="font-size:0.78em;color:var(--text-dim);padding:2px 12px;">${c.province_name} ${c.status} 共${c.round}轮</div>`;
    }
  }

  html += `<div class="submenu-tactics"><span onclick="sendAction('1')" style="cursor:pointer;color:var(--text-dim);">← 返回军事统帅部</span></div>`;
  sm.innerHTML = html;
}

// ── 兵力部署 ────────────────────────────────────────────────
function renderDeploymentMenu(data) {
  const sm = document.getElementById('submenu-panel');
  const regions = data.region_units || [];

  let html = '<h3>📋 兵力部署图</h3>';

  for (const region of regions) {
    html += `<div style="color:var(--gold);margin:10px 0 4px;font-weight:bold;">【${region.name}】</div>`;
    for (const prov of (region.provinces || [])) {
      const ownTag = prov.is_owned ? '<span style="color:var(--green);">[领地]</span>' : '<span style="color:var(--red);">[境外]</span>';
      html += `<div style="padding:2px 12px;font-size:0.82em;">
        <span style="color:var(--yellow);">${prov.name}</span> ${ownTag} <span style="color:var(--text-dim);">[${prov.terrain}]</span>`;
      for (const u of (prov.units || [])) {
        const movingStr = u.moving ? ` <span style="color:var(--yellow);">→移动中(${u.arrives_in}回合)</span>` : '';
        html += `<div style="padding:1px 0 1px 16px;font-size:0.78em;color:var(--text-dim);">
          ${u.icon||''}${escapeHtml(u.name)} ${u.exp_tag||''} [${u.type_name||''}] 攻${u.attack}防${u.defense}士${u.morale}兵${u.strength}${u.supply_tag||''}${movingStr}
          <span style="color:var(--text-dim);">🔧${u.maintenance_cost||0}金</span>
          <button class="btn-resupply" data-unit-name="${escapeHtml(u.name)}" data-unit-str="${u.strength||0}" data-unit-max="${u.max_strength||100}" style="margin-left:6px;font-size:0.85em;background:var(--panel2);color:var(--gold);border:1px solid var(--border);border-radius:3px;padding:1px 6px;cursor:pointer;">补给</button></div>`;
      }
      html += '</div>';
    }
  }

  if (data.ungarrisoned && data.ungarrisoned.length > 0) {
    html += `<div style="margin-top:8px;font-size:0.8em;color:var(--red);">⚠ 以下领地无驻军：${data.ungarrisoned.join(', ')}</div>`;
  }

  html += `<div style="margin-top:8px;font-size:0.8em;color:var(--text-dim);">
    总计：${data.total_units}支部队 · 总攻击力${data.total_atk} · 总防御力${data.total_def} · 平均士气${data.avg_morale} · 🔧维持${data.total_maintenance||0}金/回合</div>`;
  html += `<div class="submenu-tactics"><span onclick="sendAction('1')" style="cursor:pointer;color:var(--text-dim);">← 返回军事统帅部</span></div>`;
  sm.innerHTML = html;
}

// ── 军事行动子菜单 ──────────────────────────────────────────
function renderOperationsMenu(data) {
  const sm = document.getElementById('submenu-panel');
  const options = data.options || [];
  const atWar = data.at_war || [];

  let html = `<h3>🗡 军事行动</h3>
    <div class="submenu-target-count">AP:${data.ap||0}</div>`;

  for (const opt of options) {
    html += `<div class="submenu-item" onclick="sendAction('${opt.id}')" style="padding:12px;">
      <span style="font-size:1.2em;margin-right:8px;">${opt.icon}</span>
      <span class="sm-name" style="font-weight:bold;">${opt.name}</span>
      <span class="sm-info" style="font-size:0.8em;">${opt.desc}</span>
    </div>`;
  }

  if (atWar.length > 0) {
    html += `<div style="margin-top:8px;font-size:0.82em;color:var(--red);">交战中：${atWar.join(', ')}</div>`;
  }

  html += `<div class="submenu-tactics"><span onclick="sendAction('1')" style="cursor:pointer;color:var(--text-dim);">← 返回军事统帅部</span></div>`;
  sm.innerHTML = html;
}

// ── 调动部队：选择部队 ──────────────────────────────────────
function renderMoveUnitsMenu(data) {
  const sm = document.getElementById('submenu-panel');
  const units = data.units || [];

  let html = '<h3>🚚 调动部队</h3>';
  html += '<div class="submenu-target-count">每回合每支部队可移动1格（骑兵2格）。铁路省份速度翻倍。</div>';

  for (let i = 0; i < units.length; i++) {
    const u = units[i];
    html += `<div class="submenu-item" onclick="sendAction('1.4.2.${i+1}')">
      <span class="sm-idx">[${i+1}]</span>
      <span class="sm-name">${u.icon||''}${u.name} <span style="color:var(--text-dim);">[${u.type_name}]</span></span>
      <span class="sm-info">@${u.position} 攻${u.attack}防${u.defense}士${u.morale}兵${u.strength}/${u.max_strength}</span>
    </div>`;
  }

  if (units.length === 0) {
    html += '<p style="color:var(--text-dim);">没有可调动的部队。</p>';
  }

  html += `<div class="submenu-tactics"><span onclick="sendAction('1.4')" style="cursor:pointer;color:var(--text-dim);">← 返回军事行动</span></div>`;
  sm.innerHTML = html;
}

// ── 调动部队：选择目的地 ────────────────────────────────────
function renderMoveDestinations(data) {
  const sm = document.getElementById('submenu-panel');
  const dests = data.destinations || [];
  const unitIdx = data.unit_idx;

  let html = `<h3>🚚 ${data.unit_icon||''}${data.unit_name} <span style="color:var(--text-dim);">[${data.unit_type}]</span></h3>`;
  html += `<div class="submenu-target-count">@${data.current_pos} · 移动力=${data.effective_speed}格/回合（基础${data.base_speed}格）${data.rail_bonus||''}</div>`;

  for (let i = 0; i < dests.length; i++) {
    const d = dests[i];
    html += `<div class="submenu-item" onclick="sendAction('1.4.2.${unitIdx+1}.${i+1}')">
      <span class="sm-idx">[${i+1}]</span>
      <span class="sm-name">${d.name}</span>
      <span class="sm-info">
        <span style="color:var(--text-dim);">[${d.terrain}]</span>
        <span style="color:var(--yellow);">距离${d.distance}回合</span>
        ${d.garrison_count ? '<span style="color:var(--red);">[驻'+d.garrison_count+'支]</span>' : ''}
      </span>
    </div>`;
  }

  if (dests.length === 0) {
    html += '<p style="color:var(--text-dim);">没有可到达的相邻省份。</p>';
  }

  html += `<div class="submenu-tactics"><span onclick="sendAction('1.4.2')" style="cursor:pointer;color:var(--text-dim);">← 重新选部队</span></div>`;
  sm.innerHTML = html;
}

// ── 增援 ────────────────────────────────────────────────────
function renderReinforceUnits(data) {
  const sm = document.getElementById('submenu-panel');
  const camp = data.campaign || {};
  const units = data.available_units || [];
  const tactics = data.tactics || [];
  const defaultTac = data.default_tactic || 'assault';
  if (!units.length) {
    sm.innerHTML = '<h3>📨 战役增援</h3><p style="color:var(--red)">没有可用的增援部队（需在3回合距离内且未在行军/战斗中）。</p>';
    return;
  }
  let html = `<h3>📨 战役增援 · ${escapeHtml(camp.province_name||'?')}</h3>
    <div class="submenu-target-count">🎯 目标: ${escapeHtml(camp.province_name||'?')} [${escapeHtml(camp.terrain||'?')}] · 第${camp.round||0}轮</div>
    <div style="margin:6px 0;font-size:0.82em;color:var(--text-dim);">选择增援部队和战术，预计行军回合后到达战场：</div>`;
  let tacOptions = '';
  for (const t of tactics) {
    const sel = t.id === defaultTac ? ' selected' : '';
    tacOptions += `<option value="${t.id}"${sel} title="${t.pro} · ${t.con}">${t.icon} ${t.name}</option>`;
  }
  for (let i = 0; i < units.length; i++) {
    const u = units[i];
    html += `<div class="submenu-item" id="reinf-unit-${i}" style="cursor:pointer;">
      <span class="sm-idx" onclick="toggleReinfUnit(${i})"><input type="checkbox" id="reinf-cb-${i}" style="pointer-events:none;" onchange="return false;"></span>
      <span class="sm-name" onclick="toggleReinfUnit(${i})">${u.icon||''} ${escapeHtml(u.name)} <span style="font-size:0.75em;color:var(--text-dim);">[${u.type_name||''}]</span></span>
      <span class="sm-info" onclick="toggleReinfUnit(${i})">攻${u.attack}防${u.defense} 兵${u.strength}/${u.max_strength}士${u.morale} 距${u.distance}回合→${u.arrives_in}回合后到</span>
      <select id="reinf-tac-${i}" class="tactic-select"
        style="margin-left:6px;font-size:0.78em;background:var(--panel2);color:var(--text);border:1px solid var(--border);border-radius:3px;padding:2px 4px;cursor:pointer;"
        onclick="event.stopPropagation();">
        ${tacOptions}
      </select>
    </div>`;
  }
  html += `<div style="margin-top:10px;">
    <button class="btn-war" onclick="submitReinforce(${data.campaign_index})" style="width:100%;padding:10px;font-size:1em;">📨 派遣增援</button>
  </div>
  <div class="submenu-tactics"><span onclick="sendAction('1.2')" style="cursor:pointer;color:var(--text-dim);">← 返回当前战争</span></div>`;
  sm.innerHTML = html;
  window._reinfSelectedUnits = [];
}

function toggleReinfUnit(idx) {
  if (!window._reinfSelectedUnits) window._reinfSelectedUnits = [];
  const i = window._reinfSelectedUnits.indexOf(idx);
  if (i >= 0) {
    window._reinfSelectedUnits.splice(i, 1);
    document.getElementById('reinf-cb-' + idx).checked = false;
    document.getElementById('reinf-unit-' + idx).style.background = '';
  } else {
    window._reinfSelectedUnits.push(idx);
    document.getElementById('reinf-cb-' + idx).checked = true;
    document.getElementById('reinf-unit-' + idx).style.background = 'var(--panel2)';
  }
}

async function submitReinforce(campaignIndex) {
  const selected = window._reinfSelectedUnits || [];
  if (!selected.length) {
    alert('请至少选择一支部队');
    return;
  }
  const tactics = {};
  for (const idx of selected) {
    const sel = document.getElementById('reinf-tac-' + idx);
    tactics[idx] = sel ? sel.value : 'assault';
  }
  const uidxs = selected.join(',');
  const action = `1.2.S.${campaignIndex+1}.${uidxs}`;
  document.getElementById('status-text').textContent = '派遣增援...';
  const data = await apiPost('/api/action', {action, meta: {tactics}});
  if (!data.error) {
    renderAll(data);
    hideSubmenu();
  } else {
    alert('增援派遣失败: ' + (data.error || '未知错误'));
  }
}

// ── 军事设计局 (1.5) ─────────────────────────────────────────
function renderDesignBureau(data) {
  const sm = document.getElementById('submenu-panel');
  const options = data.options || [];
  let html = `<h3>🏗 军事设计局</h3>
    <div class="submenu-target-count">设计费从国库扣除 | 设计完成后持久保存</div>`;
  for (const opt of options) {
    html += `<div class="submenu-item" onclick="handleDesignAction('${opt.id}')">
      <span class="sm-idx">${opt.icon||'✦'}</span>
      <span class="sm-name">${escapeHtml(opt.name)}</span>
      <span class="sm-info">${escapeHtml(opt.desc)}</span>
    </div>`;
  }
  html += '<div class="submenu-tactics"><span onclick="sendAction(\'1\')" style="cursor:pointer;color:var(--text-dim);">← 返回军事统帅部</span></div>';
  sm.innerHTML = html;
}

function handleDesignAction(choice) {
  if (choice === '1.5.1') {
    showDesignTacticForm();
  } else if (choice === '1.5.2') {
    showDesignUnitTypeForm();
  }
}

function showDesignTacticForm() {
  const sm = document.getElementById('submenu-panel');
  sm.innerHTML = `<h3>📐 设计自定义战术</h3>
    <div class="submenu-target-count">设定攻防倍率，损耗自动计算 | 设计费5💰</div>
    <div style="margin:8px 0;">
      <label style="display:block;margin:6px 0;font-size:0.85em;">战术名称 <input id="dt-name" style="width:200px;background:var(--panel2);color:var(--text);border:1px solid var(--border);padding:4px;" placeholder="如：空降作战"></label>
      <label style="display:block;margin:6px 0;font-size:0.85em;">英文ID <input id="dt-id" style="width:200px;background:var(--panel2);color:var(--text);border:1px solid var(--border);padding:4px;" placeholder="如：airborne"></label>
      <label style="display:block;margin:6px 0;font-size:0.85em;">图标 <input id="dt-icon" style="width:60px;background:var(--panel2);color:var(--text);border:1px solid var(--border);padding:4px;" placeholder="🪂" value="✦"></label>
      <label style="display:block;margin:6px 0;font-size:0.85em;">攻击倍率 <input id="dt-atk" type="range" min="0.1" max="5" step="0.1" value="1.0" oninput="updateTacticPreview()" style="width:200px;"> <span id="dt-atk-val">1.0</span></label>
      <label style="display:block;margin:6px 0;font-size:0.85em;">防御倍率 <input id="dt-def" type="range" min="0" max="5" step="0.1" value="1.0" oninput="updateTacticPreview()" style="width:200px;"> <span id="dt-def-val">1.0</span></label>
      <label style="display:block;margin:6px 0;font-size:0.85em;">优点 <input id="dt-pro" style="width:300px;background:var(--panel2);color:var(--text);border:1px solid var(--border);padding:4px;" placeholder="如：从天而降，出其不意"></label>
      <label style="display:block;margin:6px 0;font-size:0.85em;">缺点 <input id="dt-con" style="width:300px;background:var(--panel2);color:var(--text);border:1px solid var(--border);padding:4px;" placeholder="如：需要制空权"></label>
    </div>
    <div id="dt-preview" style="margin:8px 0;padding:8px;background:var(--panel2);border-radius:4px;font-size:0.85em;">
      自动计算损耗倍率：<span style="color:var(--gold);font-weight:bold;" id="dt-loss-preview">0.8</span>
    </div>
    <button class="btn-war" onclick="submitDesignTactic()" style="width:100%;padding:10px;font-size:1em;">📐 创建战术 (5💰)</button>
    <div class="submenu-tactics"><span onclick="sendAction('1.5')" style="cursor:pointer;color:var(--text-dim);">← 返回设计局</span></div>`;
  updateTacticPreview();
}

function updateTacticPreview() {
  const atk = parseFloat(document.getElementById('dt-atk')?.value || 1);
  const def = parseFloat(document.getElementById('dt-def')?.value || 1);
  document.getElementById('dt-atk-val').textContent = atk.toFixed(1);
  document.getElementById('dt-def-val').textContent = def.toFixed(1);
  const loss = Math.max(0.2, Math.min(3.0, Math.round((atk * 0.8 + Math.max(0, 1.0 - def) * 0.4) * 10) / 10));
  const el = document.getElementById('dt-loss-preview');
  if (el) el.textContent = loss;
}

async function submitDesignTactic() {
  const name = document.getElementById('dt-name').value.trim();
  const tactic_id = document.getElementById('dt-id').value.trim();
  if (!name || !tactic_id) { alert('请填写名称和英文ID'); return; }
  const meta = {
    tactic_id, name,
    icon: document.getElementById('dt-icon').value.trim() || '✦',
    atk_mult: parseFloat(document.getElementById('dt-atk').value),
    def_mult: parseFloat(document.getElementById('dt-def').value),
    pro: document.getElementById('dt-pro').value.trim() || '灵活应变',
    con: document.getElementById('dt-con').value.trim() || '无专精',
  };
  document.getElementById('status-text').textContent = '创建战术...';
  const data = await apiPost('/api/action', {action: '1.5.1', meta});
  if (!data.error) renderAll(data);
  else alert('创建失败: ' + data.error);
}

function showDesignUnitTypeForm() {
  const sm = document.getElementById('submenu-panel');
  sm.innerHTML = `<h3>🔧 设计自定义兵种</h3>
    <div class="submenu-target-count">设定战斗属性，造价/回合自动计算 | 设计费10💰</div>
    <div style="margin:8px 0;">
      <label style="display:block;margin:6px 0;font-size:0.85em;">兵种名称 <input id="dut-name" style="width:200px;background:var(--panel2);color:var(--text);border:1px solid var(--border);padding:4px;" placeholder="如：装甲师"></label>
      <label style="display:block;margin:6px 0;font-size:0.85em;">英文ID <input id="dut-id" style="width:200px;background:var(--panel2);color:var(--text);border:1px solid var(--border);padding:4px;" placeholder="如：armored"></label>
      <label style="display:block;margin:6px 0;font-size:0.85em;">图标 <input id="dut-icon" style="width:60px;background:var(--panel2);color:var(--text);border:1px solid var(--border);padding:4px;" placeholder="🔰" value="✦"></label>
      <label style="display:block;margin:6px 0;font-size:0.85em;">攻击力 <input id="dut-atk" type="range" min="5" max="50" step="1" value="14" oninput="updateUnitTypePreview()" style="width:200px;"> <span id="dut-atk-val">14</span></label>
      <label style="display:block;margin:6px 0;font-size:0.85em;">防御力 <input id="dut-def" type="range" min="3" max="50" step="1" value="8" oninput="updateUnitTypePreview()" style="width:200px;"> <span id="dut-def-val">8</span></label>
      <label style="display:block;margin:6px 0;font-size:0.85em;">士气 <input id="dut-morale" type="range" min="20" max="100" step="1" value="55" oninput="updateUnitTypePreview()" style="width:200px;"> <span id="dut-morale-val">55</span></label>
      <label style="display:block;margin:6px 0;font-size:0.85em;">经验 <input id="dut-exp" type="range" min="10" max="80" step="1" value="25" oninput="updateUnitTypePreview()" style="width:200px;"> <span id="dut-exp-val">25</span></label>
      <label style="display:block;margin:6px 0;font-size:0.85em;">番号后缀
        <select id="dut-suffix" style="background:var(--panel2);color:var(--text);border:1px solid var(--border);padding:4px;">
          <option value="号">号</option><option value="师">师</option><option value="旅">旅</option>
          <option value="团">团</option><option value="营">营</option><option value="连">连</option>
        </select>
      </label>
    </div>
    <div id="dut-preview" style="margin:8px 0;padding:8px;background:var(--panel2);border-radius:4px;font-size:0.85em;">
      自动计算：训练费<span style="color:var(--gold);font-weight:bold;" id="dut-cost-preview">?</span>💰 / <span style="color:var(--gold);" id="dut-turns-preview">?</span>回合 / 军事+<span style="color:var(--gold);" id="dut-mil-preview">?</span> / 攻加成+<span style="color:var(--gold);" id="dut-atkb-preview">?</span> / 防加成+<span style="color:var(--gold);" id="dut-defb-preview">?</span>
    </div>
    <button class="btn-war" onclick="submitDesignUnitType()" style="width:100%;padding:10px;font-size:1em;">🔧 创建兵种 (10💰)</button>
    <div class="submenu-tactics"><span onclick="sendAction('1.5')" style="cursor:pointer;color:var(--text-dim);">← 返回设计局</span></div>`;
  updateUnitTypePreview();
}

function updateUnitTypePreview() {
  const atk = parseInt(document.getElementById('dut-atk')?.value || 14);
  const def = parseInt(document.getElementById('dut-def')?.value || 8);
  const morale = parseInt(document.getElementById('dut-morale')?.value || 55);
  const exp = parseInt(document.getElementById('dut-exp')?.value || 25);
  document.getElementById('dut-atk-val').textContent = atk;
  document.getElementById('dut-def-val').textContent = def;
  document.getElementById('dut-morale-val').textContent = morale;
  document.getElementById('dut-exp-val').textContent = exp;
  const cost = Math.max(5, Math.round(atk * 0.55 + def * 0.40 + morale * 0.04 + exp * 0.03));
  const turns = Math.max(2, Math.round(cost / 4.5));
  const mil = Math.max(3, Math.round(cost * 0.55));
  const atkb = Math.max(2, Math.round(atk * 0.65));
  const defb = Math.max(2, Math.round(def * 0.55));
  document.getElementById('dut-cost-preview').textContent = cost;
  document.getElementById('dut-turns-preview').textContent = turns;
  document.getElementById('dut-mil-preview').textContent = mil;
  document.getElementById('dut-atkb-preview').textContent = atkb;
  document.getElementById('dut-defb-preview').textContent = defb;
}

async function submitDesignUnitType() {
  const name = document.getElementById('dut-name').value.trim();
  const type_id = document.getElementById('dut-id').value.trim();
  if (!name || !type_id) { alert('请填写名称和英文ID'); return; }
  const meta = {
    type_id, name,
    icon: document.getElementById('dut-icon').value.trim() || '✦',
    atk: parseInt(document.getElementById('dut-atk').value),
    def: parseInt(document.getElementById('dut-def').value),
    morale: parseInt(document.getElementById('dut-morale').value),
    exp: parseInt(document.getElementById('dut-exp').value),
    suffix: document.getElementById('dut-suffix').value,
  };
  document.getElementById('status-text').textContent = '创建兵种...';
  const data = await apiPost('/api/action', {action: '1.5.2', meta});
  if (!data.error) renderAll(data);
  else alert('创建失败: ' + data.error);
}

function handleDesignResult(data) {
  if (data.ok) {
    showToast(data.message || '设计完成');
    // 刷新设计局面板
    sendAction('1.5');
  } else {
    showToast('设计失败: ' + (data.message || '未知'));
  }
}

// ── 撤退 ────────────────────────────────────────────────────
function renderRetreatMenu(data) {
  const sm = document.getElementById('submenu-panel');
  const camps = data.campaigns || [];

  let html = '<h3>🏳 撤退/停战</h3>';
  html += '<div class="submenu-target-count">选择要撤退的战役：</div>';

  for (let i = 0; i < camps.length; i++) {
    const c = camps[i];
    html += `<div class="submenu-item" onclick="sendAction('1.4.3.${i+1}')">
      <span class="sm-idx">[${i+1}]</span>
      <span class="sm-name">${c.province_name}</span>
      <span class="sm-info">${c.terrain} 第${c.round}轮 vs ${c.defender_name}</span>
    </div>`;
  }

  if (camps.length === 0) {
    html += '<p style="color:var(--text-dim);">没有进行中的战役。</p>';
  }

  html += `<div class="submenu-tactics"><span onclick="sendAction('1.4')" style="cursor:pointer;color:var(--text-dim);">← 返回军事行动</span></div>`;
  sm.innerHTML = html;
}

// ── 大国博弈（阶段四）───────────────────────────────────────
function renderSuperpowerMenu(data) {
  const sm = document.getElementById('submenu-panel');
  const options = data.options || [];

  let html = '<h3>🌍 大国博弈</h3>';
  for (const opt of options) {
    html += `<div class="submenu-item" onclick="sendAction('${opt.id}')">
      <span class="sm-idx">[${opt.id}]</span>
      <span class="sm-name">${opt.name}</span>
      <span class="sm-info">${opt.desc}</span>
    </div>`;
  }
  sm.innerHTML = html;
}

// ── 终局决战（阶段五）───────────────────────────────────────
// ── 撤退 ────────────────────────────────────────────────────
function renderRetreatMenu(data) {
  const sm = document.getElementById('submenu-panel');
  const camps = data.campaigns || [];

  let html = '<h3>🏳 撤退/停战</h3>';
  html += '<div class="submenu-target-count">选择要撤退的战役：</div>';

  for (let i = 0; i < camps.length; i++) {
    const c = camps[i];
    html += `<div class="submenu-item" onclick="sendAction('1.4.3.${i+1}')">
      <span class="sm-idx">[${i+1}]</span>
      <span class="sm-name">${c.province_name}</span>
      <span class="sm-info">${c.terrain} 第${c.round}轮 vs ${c.defender_name}</span>
    </div>`;
  }

  if (camps.length === 0) {
    html += '<p style="color:var(--text-dim);">没有进行中的战役。</p>';
  }

  html += `<div class="submenu-tactics"><span onclick="sendAction('1.4')" style="cursor:pointer;color:var(--text-dim);">← 返回军事行动</span></div>`;
  sm.innerHTML = html;
}

// ── 大国博弈（阶段四）───────────────────────────────────────
function renderSuperpowerMenu(data) {
  const sm = document.getElementById('submenu-panel');
  const options = data.options || [];

  let html = '<h3>🌍 大国博弈</h3>';
  for (const opt of options) {
    html += `<div class="submenu-item" onclick="sendAction('${opt.id}')">
      <span class="sm-idx">[${opt.id}]</span>
      <span class="sm-name">${opt.name}</span>
      <span class="sm-info">${opt.desc}</span>
    </div>`;
  }
  sm.innerHTML = html;
}

// ── 终局决战（阶段五）───────────────────────────────────────
function renderEndgameMenu(data) {
  const sm = document.getElementById('submenu-panel');
  const options = data.options || [];

  let html = '<h3>⚡ 终局决战</h3>';
  for (const opt of options) {
    html += `<div class="submenu-item" onclick="sendAction('${opt.id}')">
      <span class="sm-idx">[${opt.id}]</span>
      <span class="sm-name">${opt.name}</span>
      <span class="sm-info">${opt.desc}</span>
    </div>`;
  }
  sm.innerHTML = html;
}

