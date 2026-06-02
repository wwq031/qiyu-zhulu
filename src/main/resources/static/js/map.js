// 七域逐鹿 · Web Client

// ── Leaflet 战略地图 ──────────────────────────────────────────
var leafletMap = null;
var leafletMarkers = {};
var leafletPolylines = [];
var mapProvinceData = {};
var provinceNameToPid = {};
var mapInitialized = false;
var _mapInitInProgress = {};
var _lastTerritorySig = '';
window._tacticDefs = {
  'assault':    {name:'强攻', icon:'⚔', pro:'突破力极强', con:'伤亡惨重'},
  'flanking':   {name:'迂回', icon:'🏃', pro:'绕过正面防线', con:'可能被截断后路'},
  'bombard':    {name:'炮击', icon:'💣', pro:'远程消耗、低伤亡', con:'无法占领、耗弹多'},
  'ambush':     {name:'设伏', icon:'🌲', pro:'打击致命、自身安全', con:'需要有利地形'},
  'fortify':    {name:'设防', icon:'🏰', pro:'防御极坚固', con:'难以主动进攻'},
  'night_raid': {name:'夜袭', icon:'🌙', pro:'出其不意', con:'组织混乱风险'},
  'probe':      {name:'试探', icon:'🔍', pro:'低风险侦察', con:'无法取得突破'},
  'all_out':    {name:'总攻', icon:'🔥', pro:'决死一战', con:'孤注一掷、无退路'},
};
var countryLayer, cityFillLayer, regionLineLayer, cityLinkLayer, markerLayer, factionLabelLayer, garrisonLayer, battleLayer, overseasProvinceLayer, overseasLabelLayer, districtBoundaryLayer, factionBoundaryLayer;
var capitalPids = {};
var capitalLabelLayer;
var ownedBy = {};
var cityFillLayers = {};
var cityStoreMap = {};
var staticFactionBoundaries = null;
var moveMode = null;
var moveHighlightLayer;
var movePathLayer;
var selectedUnitPids = new Set();
var selectedUnitData = [];
var selectedUnitIndices = new Set();
var unitCache = new Map();
var boxSelectRect = null;
var boxSelectStart = null;
var garrisonMarkers = {};
var REGION_COLORS = {
  northeast: '#e05555', huabei: '#5b9bd5', southeast: '#5cb88d',
  lingnan: '#e2c044', southwest: '#a78bfa', nanyang: '#4db8b8',
  xibei: '#d4a853', central: '#6b8299',
};

var TYPE_STYLES = {
  capital: { outer: 17, inner: 8 },
  city: { outer: 11, inner: 4.5 },
  port: { outer: 10, inner: 4 },
  pass:  { outer: 11, inner: 4.5 },
  rural: { outer: 9,  inner: 3.5 },
};

function closeMapModal() {
  document.getElementById('map-modal').classList.remove('show');
  if (leafletMap && leafletMap._container) {
    document.getElementById('game-map').appendChild(leafletMap._container);
    setTimeout(() => leafletMap.invalidateSize(), 100);
  }
}

function moveMapToGameView() {
  if (leafletMap && leafletMap._container) {
    var gm = document.getElementById('game-map');
    if (gm && !gm.contains(leafletMap._container)) {
      // 避免循环：container已含gm，或container就是gm
      try { gm.appendChild(leafletMap._container); } catch(e) {}
      setTimeout(function() { leafletMap.invalidateSize(); }, 100);
    }
  }
}

function showMapModal() {
  // 游戏中：地图已嵌入右侧栏，直接滚动到地图位置
  if (mapInitialized && leafletMap) {
    const gameMap = document.getElementById('game-map');
    if (gameMap && gameMap.offsetParent !== null) {
      gameMap.scrollIntoView({ behavior: 'smooth', block: 'center' });
      setTimeout(() => { leafletMap.invalidateSize(); }, 100);
      return;
    }
  }
  // 首页：打开模态框 — 始终初始化在 game-map，再移动到模态框
  document.getElementById('map-modal').classList.add('show');
  if (!mapInitialized) {
    initLeafletMap('game-map');
  }
  // 把 map DOM 移到模态框容器
  if (leafletMap && leafletMap._container) {
    document.getElementById('map-container').appendChild(leafletMap._container);
  }
  setTimeout(() => { if (leafletMap) leafletMap.invalidateSize(); }, 200);
  refreshMapOwnership();
}


function toggleMapLayer(layerName, checkbox) {
  const map = { cityFill: cityFillLayer, regionLine: regionLineLayer,
    cityLink: cityLinkLayer, marker: markerLayer, country: countryLayer,
    garrison: garrisonLayer, capital: capitalLabelLayer, factionLabel: factionLabelLayer,
    overseasProvince: overseasProvinceLayer, overseasLabel: overseasLabelLayer,
    districtBoundary: districtBoundaryLayer, factionBoundary: factionBoundaryLayer };
  const layer = map[layerName];
  if (!layer) return;
  if (checkbox.checked) { leafletMap.addLayer(layer); }
  else { leafletMap.removeLayer(layer); }
}

// ── 势力名称标签（基于cityStoreMap，覆盖全部386城）──
function rebuildFactionLabels() {
  if (!factionLabelLayer) { console.log('[rebuildFactionLabels] factionLabelLayer missing'); return; }
  factionLabelLayer.clearLayers();

  // 按势力分组全部城市
  const factions = {};
  for (const [name, cs] of Object.entries(cityStoreMap)) {
    if (!cs.owner_name) continue;
    const key = cs.owner_name;
    if (!factions[key]) factions[key] = { points: [], region: cs.region, isPlayer: cs.is_player, color: cs.owner_color };
    if (cs.lat != null && cs.lng != null) factions[key].points.push([cs.lat, cs.lng]);
  }
  for (const [fname, info] of Object.entries(factions)) {
    if (!info.points.length) continue;
    const n = info.points.length;
    const pts = info.points;

    // 质心
    let sumLat = 0, sumLng = 0;
    for (const [lat, lng] of pts) { sumLat += lat; sumLng += lng; }
    let cLat = sumLat / n;
    let cLng = sumLng / n;

    // 空间范围（经纬度跨度）
    let minLat = Infinity, maxLat = -Infinity, minLng = Infinity, maxLng = -Infinity;
    for (const [lat, lng] of pts) {
      if (lat < minLat) minLat = lat;
      if (lat > maxLat) maxLat = lat;
      if (lng < minLng) minLng = lng;
      if (lng > maxLng) maxLng = lng;
    }
    const latSpread = maxLat - minLat;
    const lngSpread = maxLng - minLng;

    // 跨越大范围（>5°纬或>8°经）→ 标签向最密集聚落偏移
    if (n >= 4 && (latSpread > 5 || lngSpread > 8)) {
      let bestIdx = 0, bestCount = 0;
      const radius = Math.max(latSpread, lngSpread) * 0.3;
      for (let i = 0; i < n; i++) {
        let count = 0;
        for (let j = 0; j < n; j++) {
          const dlat = pts[i][0] - pts[j][0];
          const dlng = pts[i][1] - pts[j][1];
          if (Math.sqrt(dlat * dlat + dlng * dlng) < radius) count++;
        }
        if (count > bestCount) { bestCount = count; bestIdx = i; }
      }
      cLat = cLat * 0.4 + pts[bestIdx][0] * 0.6;
      cLng = cLng * 0.4 + pts[bestIdx][1] * 0.6;
    }

    // 字号按城市数分级：1-2城→9px ... 35+城→18px
    let fontSize;
    if (n <= 2) fontSize = 9;
    else if (n <= 5) fontSize = 10;
    else if (n <= 10) fontSize = 12;
    else if (n <= 20) fontSize = 14;
    else if (n <= 35) fontSize = 16;
    else fontSize = 18;

    // 小势力略淡
    const opacity = n <= 2 ? 0.7 : 0.85;

    const color = info.isPlayer ? '#ffffff' : (info.color || REGION_COLORS[info.region] || '#aaa');
    const icon = L.divIcon({
      html: `<div style="font-size:${fontSize}px;font-weight:bold;color:${color};text-shadow:0 0 6px #000,0 0 3px #000,1px 1px 2px #000;white-space:nowrap;pointer-events:none;text-align:center;opacity:${opacity};">${fname}</div>`,
      className: '', iconSize: [0, 0], iconAnchor: [0, 0],
    });
    const marker = L.marker([cLat, cLng], { icon, interactive: false, keyboard: false });
    marker._cityCount = n;
    factionLabelLayer.addLayer(marker);
  }
  updateFactionLabelVisibility();
}

function updateFactionLabelVisibility() {
  const zoom = leafletMap.getZoom();
  if (factionLabelLayer) {
    const chk = document.querySelector('input[onchange*=\"factionLabel\"]');
    const userEnabled = !chk || chk.checked;
    const layerVisible = zoom >= 4;
    if (layerVisible && userEnabled) { if (!leafletMap.hasLayer(factionLabelLayer)) leafletMap.addLayer(factionLabelLayer); }
    else { if (leafletMap.hasLayer(factionLabelLayer)) leafletMap.removeLayer(factionLabelLayer); }

    // 按领土大小分级显示：大势力低缩放可见，小势力需放大
    if (layerVisible && userEnabled) {
      factionLabelLayer.eachLayer(function(marker) {
        if (!marker._cityCount) return;
        const n = marker._cityCount;
        let show;
        if (n <= 2) show = zoom >= 5;       // 1-2城：zoom ≥ 5
        else show = true;                    // 其余：始终显示
        marker.setOpacity(show ? 1 : 0);
      });
    }
  }
  // 海外标注：zoom ≥ 6 时显示（尊重手动开关）
  if (overseasLabelLayer) {
    const chk2 = document.querySelector('input[onchange*=\"overseasLabel\"]');
    const userEnabled2 = !chk2 || chk2.checked;
    if (zoom >= 6 && userEnabled2) { if (!leafletMap.hasLayer(overseasLabelLayer)) leafletMap.addLayer(overseasLabelLayer); }
    else { if (leafletMap.hasLayer(overseasLabelLayer)) leafletMap.removeLayer(overseasLabelLayer); }
  }
}

async function initLeafletMap(containerId) {
  const containerIdUse = containerId || 'game-map';
  const container = document.getElementById(containerIdUse);
  if (!container) return;

  // 防重入：如果该容器已有地图或正在初始化，跳过
  if (container.querySelector('.leaflet-container')) return;
  if (_mapInitInProgress[containerIdUse]) return;
  _mapInitInProgress[containerIdUse] = true;

  // 隐藏占位文字
  const placeholder = document.getElementById('game-map-placeholder');
  if (placeholder) placeholder.style.display = 'none';

  // 首页战略地图模态框使用旁观模式（显示全部57势力），游戏中嵌入的地图使用游戏数据
  const isSpectator = containerIdUse === 'map-container';
  const apiUrl = '/api/map' + (isSpectator ? '?spectator=1' : '');
  const data = await apiGet(apiUrl);

  if (data.error) {
    console.warn('initLeafletMap: API error', data.error);
    _mapInitInProgress[containerIdUse] = false;
    return;
  }

  // 构建省份索引
  provinceNameToPid = {};
  for (const region of (data.regions || [])) {
    for (const p of (region.provinces || [])) { mapProvinceData[p.id] = p; provinceNameToPid[p.name] = p.id; }
  }
  // 构建城市Store（386城统一数据源）
  cityStoreMap = {};
  for (const c of (data.city_store || [])) { cityStoreMap[c.name] = c; }

  // 安全处理已存在的地图实例：必须调用 .remove() 销毁，否则事件处理器残留
  if (leafletMap) {
    try { leafletMap.remove(); } catch(e) {}
    leafletMap = null;
  }
  // 额外清理（leafletMap.remove() 可能遗漏的残留 DOM）
  var residual = container.querySelector('.leaflet-container');
  if (residual) residual.remove();
  if (container._leaflet_id !== undefined) delete container._leaflet_id;
  leafletMap = L.map(container, {
    center: [32, 108], zoom: 5, minZoom: 3, maxZoom: 8,
    zoomControl: true, attributionControl: false, dragging: true,
  });
  // ── 地图图层设置按钮（Leaflet 自定义控件）──
  const LayerControl = L.Control.extend({
    options: { position: 'topright' },
    onAdd: function() {
      const div = L.DomUtil.create('div', 'leaflet-layer-control');
      div.innerHTML = `<div style="background:var(--panel);border:1px solid var(--border);border-radius:6px;padding:2px;box-shadow:0 2px 8px rgba(0,0,0,0.4);">
        <button id="layer-settings-btn" style="background:none;border:none;color:var(--gold);cursor:pointer;font-size:16px;padding:2px 5px;line-height:1;" title="图层设置">⚙</button>
        <div id="layer-settings-panel" style="display:none;padding:4px 8px 4px 4px;font-size:0.72em;min-width:60px;">
          <label style="display:block;cursor:pointer;color:var(--text-dim);white-space:nowrap;padding:1px 0;"><input type="checkbox" checked onchange="toggleMapLayer('cityFill',this)"> 填色</label>
          <label style="display:block;cursor:pointer;color:var(--text-dim);white-space:nowrap;padding:1px 0;"><input type="checkbox" checked onchange="toggleMapLayer('regionLine',this)"> 大区界</label>
          <label style="display:block;cursor:pointer;color:var(--text-dim);white-space:nowrap;padding:1px 0;"><input type="checkbox" checked onchange="toggleMapLayer('districtBoundary',this)"> 地区界</label>
          <label style="display:block;cursor:pointer;color:var(--text-dim);white-space:nowrap;padding:1px 0;"><input type="checkbox" checked onchange="toggleMapLayer('factionBoundary',this)"> 势力界</label>
          <label style="display:block;cursor:pointer;color:var(--text-dim);white-space:nowrap;padding:1px 0;"><input type="checkbox" checked onchange="toggleMapLayer('cityLink',this)"> 连线</label>
          <label style="display:block;cursor:pointer;color:var(--text-dim);white-space:nowrap;padding:1px 0;"><input type="checkbox" checked onchange="toggleMapLayer('marker',this)"> 标记</label>
          <label style="display:block;cursor:pointer;color:var(--text-dim);white-space:nowrap;padding:1px 0;"><input type="checkbox" checked onchange="toggleMapLayer('country',this)"> 邻国</label>
          <label style="display:block;cursor:pointer;color:var(--text-dim);white-space:nowrap;padding:1px 0;"><input type="checkbox" checked onchange="toggleMapLayer('garrison',this)"> 驻军</label>
          <label style="display:block;cursor:pointer;color:var(--text-dim);white-space:nowrap;padding:1px 0;"><input type="checkbox" checked onchange="toggleMapLayer('capital',this)"> 首都</label>
          <label style="display:block;cursor:pointer;color:var(--text-dim);white-space:nowrap;padding:1px 0;"><input type="checkbox" checked onchange="toggleMapLayer('factionLabel',this)"> 势力名称</label>
          <label style="display:block;cursor:pointer;color:var(--text-dim);white-space:nowrap;padding:1px 0;"><input type="checkbox" checked onchange="toggleMapLayer('overseasProvince',this)"> 海外省界</label>
          <label style="display:block;cursor:pointer;color:var(--text-dim);white-space:nowrap;padding:1px 0;"><input type="checkbox" checked onchange="toggleMapLayer('overseasLabel',this)"> 海外标注</label>
        </div>
      </div>`;
      L.DomEvent.disableClickPropagation(div);
      L.DomEvent.disableScrollPropagation(div);
      return div;
    },
  });
  leafletMap.addControl(new LayerControl());

  // Toggle settings panel on button click (delegated to document since button is created async)
  setTimeout(() => {
    const btn = document.getElementById('layer-settings-btn');
    const panel = document.getElementById('layer-settings-panel');
    if (btn && panel) {
      btn.onclick = function(e) {
        L.DomEvent.stopPropagation(e);
        panel.style.display = panel.style.display === 'none' ? 'block' : 'none';
      };
    }
  }, 500);

  // ── 图层组（菜单控制开关）──
  countryLayer = L.layerGroup().addTo(leafletMap);
  cityFillLayer = L.layerGroup().addTo(leafletMap);
  regionLineLayer = L.layerGroup().addTo(leafletMap);
  districtBoundaryLayer = L.layerGroup().addTo(leafletMap);
  factionBoundaryLayer = L.layerGroup().addTo(leafletMap);
  cityLinkLayer = L.layerGroup().addTo(leafletMap);
  markerLayer = L.layerGroup().addTo(leafletMap);
  factionLabelLayer = L.layerGroup().addTo(leafletMap);
  garrisonLayer = L.layerGroup().addTo(leafletMap);
  battleLayer = L.layerGroup().addTo(leafletMap);
  capitalLabelLayer = L.layerGroup().addTo(leafletMap);
  moveHighlightLayer = L.layerGroup().addTo(leafletMap);
  movePathLayer = L.layerGroup().addTo(leafletMap);
  overseasProvinceLayer = L.layerGroup().addTo(leafletMap);
  overseasLabelLayer = L.layerGroup().addTo(leafletMap);

  // ── 右键处理：取消命令 / 势力信息 ──
  let ctxMenuHandled = false;
  leafletMap.getContainer().addEventListener('contextmenu', function(e) {
    e.preventDefault();
  });
  leafletMap.on('contextmenu', function(e) {
    if (ctxMenuHandled) { ctxMenuHandled = false; return; }
    if (moveMode) {
      exitCommandMode();
      statusText('已取消命令模式');
    }
  });
  // expose for marker handlers
  window._mapCtxMenuDone = function() { ctxMenuHandled = true; };

  // 点击地图空白处取消部队选择
  leafletMap.on('click', function(e) {
    if (moveMode) return;
    if (ctxMenuHandled) { ctxMenuHandled = false; return; }
    // 如果点击的不是标记，清除选择
    if (selectedUnitPids.size > 0) {
      clearUnitSelection();
    }
  });

  // ── 亚洲国界 + 海外赛区着色 ──
  // 周边国家 → 游戏赛区映射（28国，精确名称匹配）
  const COUNTRY_REGION = {
    // 南洋 — 马来群岛（蓝色赛区）
    'Indonesia':'nanyang','Timor-Leste':'nanyang','Philippines':'nanyang',
    'Malaysia':'nanyang','Brunei':'nanyang',
    // 中南半岛 → 岭南延伸
    'Cambodia':'lingnan','Thailand':'lingnan','LaoPDR':'lingnan',
    'Myanmar':'lingnan','Vietnam':'lingnan',
    // 西北延伸
    'Mongolia':'xibei','Kazakhstan':'xibei','Kyrgyzstan':'xibei',
    'Tajikistan':'xibei','Uzbekistan':'xibei','Turkmenistan':'xibei',
    // 东北延伸
    'Dem.Rep.Korea':'northeast','Korea':'northeast','Japan':'northeast',
    // 西南延伸
    'India':'southwest','Nepal':'southwest','Bhutan':'southwest',
    'Bangladesh':'southwest','SriLanka':'southwest',
    // 邻国（中性灰）
    'Russia':'neutral','Pakistan':'neutral','Afghanistan':'neutral','Iran':'neutral',
  };
  const REGION_FILL_OPACITY_COUNTRY = 0.32;
  try {
    const resp = await fetch('/static/vendor/neighbor_countries.geojson');
    const geo = await resp.json();
    L.geoJSON(geo, {
      style: function(feature) {
        const nm = feature.properties.name || '';
        const r = COUNTRY_REGION[nm];
        if (r && r !== 'neutral') {
          const c = REGION_COLORS[r] || '#3a5068';
          return { color: c, weight: 1.2, opacity: 0.55, fillColor: c, fillOpacity: REGION_FILL_OPACITY_COUNTRY };
        }
        return { color: '#2a4055', weight: 0.8, opacity: 0.35, fillColor: '#141f2b', fillOpacity: 0.12 };
      },
      onEachFeature: function(feature, layer) {
        const nm = feature.properties.name || '';
        const r = COUNTRY_REGION[nm];
        if (r && r !== 'neutral') {
          layer.bindTooltip(nm + ' [' + r + ']', { sticky: true, className: 'province-tooltip', opacity: 0.85 });
        }
      },
    }).addTo(countryLayer);
  } catch(e) { console.log('Countries load failed:', e); }

  // ── 海外省份边界（东南亚8国 ADM1 省界）──
  let overseasLabels = [];
  try {
    const respOp = await fetch('/static/vendor/overseas_provinces.geojson?v=1');
    const geoOp = await respOp.json();
    L.geoJSON(geoOp, {
      style: function(feature) {
        const r = feature.properties.region || 'nanyang';
        const c = REGION_COLORS[r] || '#4db8b8';
        return { color: c, weight: 0.5, opacity: 0.30, fillColor: c, fillOpacity: 0.04 };
      },
      onEachFeature: function(feature, layer) {
        const nameZh = feature.properties.name_zh || feature.properties.name_en || '';
        const country = feature.properties.country_name || '';
        layer.bindTooltip(nameZh + ' <span style="font-size:0.75em;opacity:0.6">' + country + '</span>', { sticky: true, className: 'province-tooltip', opacity: 0.80 });
      },
      interactive: true,
    }).addTo(overseasProvinceLayer);
  } catch(e) { console.log('Overseas provinces load failed:', e); }

  // ── 海外省份名称标注（zoom≥6显示）──
  try {
    const respOl = await fetch('/static/vendor/overseas_labels.json?v=1');
    overseasLabels = await respOl.json();
    for (const lb of overseasLabels) {
      if (lb.lat == null || lb.lng == null) continue;
      const r = lb.region || 'nanyang';
      const c = REGION_COLORS[r] || '#4db8b8';
      const icon = L.divIcon({
        html: '<div style="font-size:8px;color:' + c + ';text-shadow:0 0 3px #000,0 0 2px #000;white-space:nowrap;pointer-events:none;text-align:center;opacity:0.50;">' + (lb.name_zh || lb.name_en || '') + '</div>',
        className: '', iconSize: [0, 0], iconAnchor: [0, 0],
      });
      const m = L.marker([lb.lat, lb.lng], { icon, interactive: false, keyboard: false }).addTo(overseasLabelLayer);
    }
  } catch(e) { console.log('Overseas labels load failed:', e); }

// ── 中国市级边界（leafletCN 高精度，数据源: cityStoreMap）──
  const darken = (hex, f) => { const r=parseInt(hex.slice(1,3),16),g=parseInt(hex.slice(3,5),16),b=parseInt(hex.slice(5,7),16); const dr=Math.round(r*f),dg=Math.round(g*f),db=Math.round(b*f); return '#'+[dr,dg,db].map(v=>v.toString(16).padStart(2,'0')).join(''); };
  const getCityFillStyle = (name) => {
    const cs = cityStoreMap[name];
    if (cs && cs.owner_name && cs.owner_color) {
      const c = cs.is_player ? '#ffffff' : cs.owner_color;
      return { color: darken(c, 0.55), weight: 2, opacity: 0.6, fillColor: c, fillOpacity: 0.22 };
    }
    if (cs && cs.owner_name) {
      const c = cs.is_player ? '#ffffff' : (REGION_COLORS[cs.region] || '#aaa');
      return { color: darken(c, 0.55), weight: 2, opacity: 0.6, fillColor: c, fillOpacity: 0.22 };
    }
    if (cs) {
      const c = REGION_COLORS[cs.region] || '#3a5068';
      return { color: darken(c, 0.55), weight: 2, opacity: 0.6, fillColor: c, fillOpacity: 0.10 };
    }
    return null;
  };
  try {
    const respCn = await fetch('/static/vendor/china_cities_region.geojson?v=4');
    const geoCn = await respCn.json();
    L.geoJSON(geoCn, {
      style: function(feature) {
        const s = getCityFillStyle(feature.properties.name || '');
        if (s) return s;
        const r = feature.properties.region || 'unknown';
        const c = REGION_COLORS[r] || '#3a5068';
        return { color: darken(c, 0.55), weight: 2, opacity: 0.6, fillColor: c, fillOpacity: 0.10 };
      },
      onEachFeature: function(feature, layer) {
        const name = feature.properties.name || '';
        cityFillLayers[name] = layer;
        const cs = cityStoreMap[name];
        const province = feature.properties.province || '';
        let tip = '<b>' + name + '</b> ' + province;
        if (cs && cs.district) {
          tip += '<br><span style="font-size:0.85em;opacity:0.7">📌 ' + cs.district + '</span>';
          if (cs.parent_city && cs.parent_city !== name) {
            tip += ' <span style="font-size:0.8em;opacity:0.55">· 治所 ' + cs.parent_city + '</span>';
          }
        }
        if (cs && cs.owner_name) {
          tip += '<br><b>' + cs.owner_name + '</b>' + (cs.is_player ? ' [你]' : '');
          if (cs.garrison_count > 0) tip += '<br>🗡 ' + cs.garrison_count + '支部队';
        }
        layer.bindTooltip(tip, { sticky: true, className: 'province-tooltip', opacity: 0.85 });
        // 左键点击：同标记逻辑
        layer.on('click', function(e) {
          L.DomEvent.stopPropagation(e);
          const pid = provinceNameToPid[name];
          if (!pid) return;
          const p = mapProvinceData[pid];
          if (moveMode) {
            const dest = moveMode.reachable.find(d => d.pid === pid || d.name === p.name);
            if (dest) {
              if (dest.is_enemy) { showAttackDest(dest); }
              else { showMoveDest(dest); }
              return;
            }
          }
          const owner = ownedBy[pid];
          const garr = (gameState && gameState.garrisons && gameState.garrisons[pid]) || [];
          if (garr.length && owner && owner.isPlayer) {
            showUnitSelectionPopup(pid, garr, layer);
          } else if (garr.length) {
            showGarrisonPopup(pid, garr, owner && owner.isPlayer);
          }
        });
        layer.on('contextmenu', function(e) {
          L.DomEvent.stopPropagation(e);
          window._mapCtxMenuDone();
          const pid = provinceNameToPid[name];
          if (!pid) return;
          if (moveMode) { exitCommandMode(); statusText('已取消命令模式'); return; }
          showProvinceDetail(pid);
        });
      },
    }).addTo(cityFillLayer);
  } catch(e) { console.log('China cities load failed:', e); }

// ── 大区边界（白实线）──
  try {
    const respRb = await fetch('/static/vendor/region_boundary_lines.json?v=3');
    const rbData = await respRb.json();
    L.geoJSON(rbData, {
      style: { color: '#ffffff', weight: 1.5, opacity: 0.55 },
      interactive: false,
    }).addTo(regionLineLayer);
  } catch(e) { console.log('Region boundaries load failed:', e); }

// ── 地区边界（district_boundaries.json）──
  try {
    const respDb = await fetch('/static/vendor/district_boundaries.json?v=1');
    const dbData = await respDb.json();
    L.geoJSON(dbData, {
      style: { color: '#8090a0', weight: 0.8, opacity: 0.35, dashArray: '2,6' },
      interactive: false,
    }).addTo(districtBoundaryLayer);
  } catch(e) { console.log('District boundaries load failed:', e); }

  // ── 势力边界（静态回退，动态边界在applyOwnership中更新）──
  try {
    const respFb = await fetch('/static/vendor/faction_boundaries.json?v=1');
    const fbData = await respFb.json();
    staticFactionBoundaries = fbData;
  } catch(e) { console.log('Faction boundaries load failed:', e); }

// ── 市际连线（city_connections.json）──
  try {
    const respConn = await fetch('/static/vendor/city_connections.json?v=2');
    const connData = await respConn.json();
    const drawnEdges = new Set();
    function edgeKey(a, b) { return a < b ? `${a}|${b}` : `${b}|${a}`; }
    for (const conn of connData) {
      const key = edgeKey(conn.from, conn.to);
      if (drawnEdges.has(key)) continue;
      drawnEdges.add(key);
      const style = conn.cross_region
        ? { color: '#4a6080', weight: 1.8, opacity: 0.50 }
        : { color: '#3a5568', weight: 1.0, opacity: 0.45, dashArray: '4,8' };
      leafletPolylines.push(L.polyline([conn.from_coord, conn.to_coord], style).addTo(cityLinkLayer));
    }
  } catch(e) { console.log('City connections load failed:', e); }

  // ── 创建所有标记（一次创建，后续只改样式）──

  // ── 图层控制菜单 ──
  const overlayLayers = {
    '城市填色': cityFillLayer,
    '大区边界': regionLineLayer,
    '地区边界': districtBoundaryLayer,
    '势力边界': factionBoundaryLayer,
    '市际连线': cityLinkLayer,
    '城市标记': markerLayer,
    '势力名称': factionLabelLayer,
    '周边国家': countryLayer,
  };
  // Layer control moved to map modal header

  for (const [pid, p] of Object.entries(mapProvinceData)) {
    if (p.lat == null || p.lng == null) continue;
    const icon = buildMarkerIcon(pid, null);
    const marker = L.marker([p.lat, p.lng], { icon, interactive: true }).addTo(markerLayer);
    // 左键点击：命令模式下查找是否为目标省份，否则查看详情
    marker.on('click', function(e) {
      L.DomEvent.stopPropagation(e);
      if (moveMode) {
        // 查找该省份是否在可达列表中
        const dest = moveMode.reachable.find(d => d.pid === pid || d.name === p.name);
        if (dest) {
          if (dest.is_enemy) { showAttackDest(dest); }
          else { showMoveDest(dest); }
          return;
        }
      }
      // 非命令模式：点击查看驻军/势力信息
      const owner = ownedBy[pid];
      const garr = (gameState && gameState.garrisons && gameState.garrisons[pid]) || [];
      if (garr.length && owner && owner.isPlayer) {
        showUnitSelectionPopup(pid, garr, marker);
      } else if (garr.length) {
        showGarrisonPopup(pid, garr, owner && owner.isPlayer);
      }
    });
    marker.on('contextmenu', function() {
      window._mapCtxMenuDone();
      if (moveMode) { exitCommandMode(); statusText('已取消命令模式'); return; }
      showProvinceDetail(pid);
    });
    leafletMarkers[pid] = marker;
  }

  // ── 图例 ──
  const legend = container.querySelector('.map-legend');
  if (legend) legend.innerHTML = `
    <div style="font-weight:bold;color:var(--gold);margin-bottom:4px;">图例</div>
    <div style="margin-bottom:2px;line-height:1.8;">
      <span style="display:inline-block;width:8px;height:8px;border:1.5px solid #d4a853;border-radius:50%;vertical-align:middle;margin-right:2px;position:relative;"><span style="position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);width:3px;height:3px;background:#d4a853;border-radius:50%;"></span></span> 城镇/关卡
      <span style="display:inline-block;width:8px;height:8px;border:1.5px solid #4db8b8;border-radius:50%;vertical-align:middle;margin:0 2px 0 8px;position:relative;"><span style="position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);width:3px;height:3px;background:#4db8b8;border-radius:50%;"></span></span> 港口
      <span style="display:inline-block;width:11px;height:11px;background:rgba(85,255,85,0.3);border:1.5px solid #5f5;border-radius:50%;vertical-align:middle;margin:0 2px 0 8px;text-align:center;line-height:11px;font-size:7px;color:#5f5;">3</span> 驻军
    </div>
    <div style="font-size:0.78em;line-height:1.6;">
    <span style="color:#e05555;">■</span>东北 <span style="color:#5b9bd5;">■</span>华北 <span style="color:#5cb88d;">■</span>东南 <span style="color:#e2c044;">■</span>岭南<br>
    <span style="color:#a78bfa;">■</span>西南 <span style="color:#4db8b8;">■</span>南洋 <span style="color:#d4a853;">■</span>西北 <span style="color:#6b8299;">■</span>中枢
    </div>
    <div style="margin-top:3px;font-size:0.73em;color:var(--text-dim);line-height:1.5;">
    市填色=势力归属 · 同势力相邻市无缝拼接
    </div>
    <div style="font-size:0.75em;color:var(--text-dim);">🖱 缩放 · 拖拽 · 点击查看详情 · 右上角菜单开关图层</div>
  `;

  // 首次加载所有权
  await applyOwnership(data);
  mapInitialized = true;
  _lastTerritorySig = '';  // 新地图初始化，强制下次 renderAll 全量刷新
  _mapInitInProgress[containerIdUse] = false;
  leafletMap.on('zoomend', updateFactionLabelVisibility);
  updateFactionLabelVisibility(); // 初始状态
  setTimeout(() => { if (leafletMap) leafletMap.invalidateSize(); }, 200);
  // 初始化框选系统
  initBoxSelect(container);
}

// ── 轻量增量刷新：仅更新驻军和战役标记（不重绘领土填色）──
var _capitalLabels = {}; // pid → marker

function _updateCapitalLabels(capitalPids) {
  if (!capitalLabelLayer) return;
  var newPids = new Set(Object.keys(capitalPids));
  // 删旧的
  for (var pid in _capitalLabels) {
    if (!newPids.has(pid)) {
      capitalLabelLayer.removeLayer(_capitalLabels[pid]);
      delete _capitalLabels[pid];
    }
  }
  // 新增
  for (var pid in capitalPids) {
    if (_capitalLabels[pid]) continue;
    var cap = capitalPids[pid];
    var p = mapProvinceData[pid];
    if (!p || p.lat == null || p.lng == null) continue;
    var labelColor = cap.isPlayer ? '#ffffff' : '#f0d060';
    var labelName = p.name || cap.name;
    var icon = L.divIcon({
      className: 'capital-label-icon',
      html: `<div style="white-space:nowrap;font-size:11px;font-weight:bold;color:${labelColor};text-shadow:0 0 5px rgba(0,0,0,0.95),0 0 2px #000,0 1px 2px rgba(0,0,0,0.9);font-family:var(--font);letter-spacing:1.5px;pointer-events:none;">★${labelName}</div>`,
      iconSize: [1, 1], iconAnchor: [0, -8],
    });
    _capitalLabels[pid] = L.marker([p.lat, p.lng], { icon:icon, interactive:false, keyboard:false, zIndexOffset:1000 }).addTo(capitalLabelLayer);
  }
}

// ── 增量标记更新工具 ──────────────────────────────────────────
function _garrisonIcon(count, isPlayer) {
  const w = count >= 100 ? 32 : count >= 10 ? 26 : count >= 5 ? 22 : 18;
  const h = count >= 100 ? 18 : count >= 10 ? 16 : count >= 5 ? 14 : 12;
  const fs = count >= 100 ? 9 : count >= 10 ? 8 : 7;
  const bc = isPlayer ? '#5f5' : '#f55';
  const tc = isPlayer ? '#afa' : '#faa';
  const bg = isPlayer ? 'rgba(30,80,30,0.85)' : 'rgba(80,20,20,0.85)';
  return L.divIcon({
    html: `<div style="width:${w}px;height:${h}px;background:${bg};border:1.5px solid ${bc};border-radius:4px;text-align:center;line-height:${h}px;font-size:${fs}px;font-weight:bold;color:${tc};cursor:pointer;font-family:var(--font);letter-spacing:0.5px;">⚔${count}</div>`,
    className: 'garr-icon', iconSize: [w, h], iconAnchor: [w/2, h/2],
  });
}

function _updateGarrisonLayer(garrisons) {
  if (!garrisonLayer) return;
  if (!garrisons || !Object.keys(garrisons).length) return;
  var newPids = new Set(Object.keys(garrisons));
  for (var pid in garrisonMarkers) {
    if (!newPids.has(pid)) {
      garrisonLayer.removeLayer(garrisonMarkers[pid]);
      delete garrisonMarkers[pid];
    }
  }
  // 新增或更新
  Object.keys(garrisons).forEach(function(pid) {
    var units = garrisons[pid];
    var p = mapProvinceData[pid];
    if (!p || p.lat == null || p.lng == null || !units || !units.length) return;
    var isPlayer = units.some(function(u) { return u.is_player; });
    var count = units.length;
    var existing = garrisonMarkers[pid];
    if (existing && existing._data && existing._data.count === count && existing._data.isPlayer === isPlayer) return;
    if (existing) garrisonLayer.removeLayer(existing);
    var icon = _garrisonIcon(count, isPlayer);
    var m = L.marker([p.lat, p.lng], { icon:icon, interactive:true, keyboard:false }).addTo(garrisonLayer);
    garrisonMarkers[pid] = m;
    m._data = { pid:pid, units:units, isPlayer:isPlayer, count:count };
    m._icon.style.pointerEvents = 'auto';
    var gDistrict = p.district ? p.district + ' · ' : '';
    var factionLabel = (units[0] && units[0].faction_name) ? units[0].faction_name : (isPlayer ? '我方' : '敌军');
    m.bindTooltip('<b>' + gDistrict + p.name + '</b> · <span style="color:' + (isPlayer?'var(--green)':'var(--red)') + '">' + factionLabel + '</span> · ' + count + '支部队<br>' + units.slice(0,5).map(function(u){return u.name;}).join('<br>') + (count>5?'<br>+'+ (count-5):''), { direction:'top', offset:[0, -Math.max(12, count>=100?9:count>=10?8:7)/2-4] });
    m.on('click', function(e) {
      L.DomEvent.stopPropagation(e);
      var d = this._data;
      if (!d) return;
      if (e.originalEvent.shiftKey) {
        if (moveMode) exitCommandMode();
        toggleUnitSelection(d.pid, d.units, this);
        updateSelectionUI();
      } else if (d.isPlayer) {
        if (moveMode && moveMode.unitPid === d.pid) exitCommandMode();
        else if (!moveMode) showUnitSelectionPopup(d.pid, d.units, this);
      } else {
        showGarrisonPopup(d.pid, d.units, d.isPlayer);
      }
    });
    m.on('contextmenu', function(e) {
      L.DomEvent.stopPropagation(e);
      window._mapCtxMenuDone();
      if (moveMode) { exitCommandMode(); statusText('已取消命令模式'); return; }
      var d = this._data;
      if (d) showProvinceDetail(d.pid);
    });
  });
}

async function refreshDynamicMarkers(data) {
  if (!mapInitialized || !leafletMap) return;
  try {
    // 增量更新驻军标记
    _updateGarrisonLayer(data.garrisons || {});
    // 更新战役标记
    const activeCampaigns = data.active_campaigns || [];
    if (battleLayer) {
      battleLayer.clearLayers();
      for (const camp of activeCampaigns) {
        const p = mapProvinceData[camp.province];
        if (!p || p.lat == null || p.lng == null) continue;
        const remaining = (camp.max_rounds || 4) - (camp.round || 0);
        const color = camp.is_player_attacker || camp.is_player_defender ? '#fff' : '#f80';
        const icon = L.divIcon({
          html: `<div style="width:20px;height:20px;background:rgba(200,100,0,0.85);border:2px solid ${color};border-radius:50%;text-align:center;line-height:20px;font-size:10px;animation:battle-pulse 1.5s ease infinite;">⚔</div>`,
          className: '', iconSize: [20, 20], iconAnchor: [10, 10],
        });
        const bMarker = L.marker([p.lat, p.lng], { icon, interactive: true, keyboard: false, zIndexOffset: 500 }).addTo(battleLayer);
        bMarker.bindTooltip(`<b>⚔ ${p.name} · 战役中</b><br>${camp.attacker_name} → ${camp.defender_name}<br>第${camp.round}轮 · 预计剩${remaining}轮`, {
          direction: 'top', offset: [0, -16],
        });
        bMarker.on('click', (e) => {
          L.DomEvent.stopPropagation(e);
          showBattleInfoPopup(camp);
        });
      }
    }
  } catch(e) { console.error('refreshDynamicMarkers:', e); }
}

// ── 实时更新所有权（不改标记位置，只换图标+弹窗）──
async function refreshMapOwnership() {
  if (!mapInitialized || !leafletMap) return;
  try {
    const data = await apiGet('/api/map');
    if (!data.error) await applyOwnership(data);
  } catch(e) {}
}

async function applyOwnership(data) {
  console.log('[applyOwnership] called, data keys:', Object.keys(data));
  // 构建所有权映射
  ownedBy = {};
  const ownership = data.ownership;
  console.log('[applyOwnership] ownership:', ownership ? 'present' : 'MISSING', 'player:', ownership?.player ? 'yes' : 'no', 'ai count:', ownership?.ai?.length || 0);
  if (ownership && ownership.player) {
    if (ownership.player) {
      for (const pid of (ownership.player.territory_pids || [])) {
        ownedBy[pid] = { name: ownership.player.name, region: ownership.player.region, color: ownership.player.color, isPlayer: true };
      }
    }
    for (const ai of (ownership.ai || [])) {
      for (const pid of (ai.territory_pids || [])) {
        if (!ownedBy[pid]) ownedBy[pid] = { name: ai.name, region: ai.region, color: ai.color, isPlayer: false };
      }
    }
  }

  // 更新cityStoreMap（来自服务端统一数据源）
  if (data.city_store && data.city_store.length > 0) {
    cityStoreMap = {};
    let ownedCount = 0;
    for (const c of data.city_store) { cityStoreMap[c.name] = c; if (c.owner_name) ownedCount++; }
    console.log('[applyOwnership] cityStoreMap rebuilt:', Object.keys(cityStoreMap).length, 'cities,', ownedCount, 'with owner');
  } else {
    // ── 旁观模式回退：调用专用旁观API获取完整数据 ──
    try {
      const specResp = await fetch('/api/spectator/map');
      const specData = await specResp.json();
      if (specData.city_store && specData.city_store.length > 0) {
        cityStoreMap = {};
        for (const c of specData.city_store) { cityStoreMap[c.name] = c; }
        // Merge ownership
        if (specData.ownership) {
          const so = specData.ownership;
          if (so.player) {
            for (const pid of (so.player.territory_pids || [])) {
              if (!ownedBy[pid]) ownedBy[pid] = { name: so.player.name, region: so.player.region, color: so.player.color, isPlayer: true };
            }
          }
          for (const ai of (so.ai || [])) {
            for (const pid of (ai.territory_pids || [])) {
              if (!ownedBy[pid]) ownedBy[pid] = { name: ai.name, region: ai.region, color: ai.color, isPlayer: false };
            }
          }
        }
        if (specData.capitals) {
          capitalPids = {};
          for (const [fid, cap] of Object.entries(specData.capitals)) {
            capitalPids[cap.pid] = { name: cap.name, isPlayer: cap.is_player };
          }
        }
      }
    } catch(e) { console.error('Spectator API fallback failed:', e); }
  }
  // 动态更新全部386市填色（cityStore→势力专属色）
  console.log('[applyOwnership] cityFillLayers size:', Object.keys(cityFillLayers).length, 'cityStoreMap size:', Object.keys(cityStoreMap).length);
  let fillUpdated = 0;
  try {
    for (const [name, layer] of Object.entries(cityFillLayers)) {
      const cs = cityStoreMap[name];
      let fillColor, fillOpacity;
      if (cs && cs.owner_name) {
        fillColor = cs.is_player ? '#ffffff' : (cs.owner_color || REGION_COLORS[cs.region] || '#aaa');
        fillOpacity = 0.22;
      } else if (cs) {
        fillColor = REGION_COLORS[cs.region] || '#3a5068';
        fillOpacity = 0.10;
      } else {
        continue;
      }
      const ri = parseInt(fillColor.slice(1,3),16), gi = parseInt(fillColor.slice(3,5),16), bi = parseInt(fillColor.slice(5,7),16);
      const dc = '#' + [Math.round(ri*0.30), Math.round(gi*0.30), Math.round(bi*0.30)].map(v => v.toString(16).padStart(2,'0')).join('');
      layer.setStyle({ color: dc, weight: 2, opacity: 0.5, fillColor: fillColor, fillOpacity: Math.min(0.22, fillOpacity) });
      fillUpdated++;
    }
    console.log('[applyOwnership] fill colors updated:', fillUpdated);
  } catch(e) { console.error('City fill update failed:', e); }

  const garrisons = data.garrisons || {};

  // 更新首都数据（仅在游戏模式下有数据时覆盖，不覆盖旁观模式已填充的数据）
  if (data.capitals && Object.keys(data.capitals).length > 0) {
    capitalPids = {};
    for (const [fid, cap] of Object.entries(data.capitals)) {
      capitalPids[cap.pid] = { name: cap.name, isPlayer: cap.is_player };
    }
  }

  // 更新每个标记的图标和弹窗
  for (const [pid, marker] of Object.entries(leafletMarkers)) {
    const owner = ownedBy[pid];
    const isCapital = !!capitalPids[pid];
    const icon = buildMarkerIcon(pid, owner, isCapital);
    marker.setIcon(icon);

    // 更新弹窗
    const p = mapProvinceData[pid];
    if (!p) continue;
    const garr = garrisons[pid];
    let garrHtml = '';
    if (garr && garr.length) {
      garrHtml = '<div style="margin-top:4px;font-size:0.85em;color:var(--red);">🗡 ' + garr.slice(0, 4).map(u => u.name).join(', ') + (garr.length > 4 ? ' +' + (garr.length - 4) : '') + '</div>';
    }
    // 首都 ★ 标记加入 tooltip
    const districtPrefix = p.district ? p.district + ' · ' : '';
    if (isCapital) {
      const capData = capitalPids[pid];
      const capLabel = capData.isPlayer ? '★ 首都' : '★ ' + (capData.name||'') + ' 都城';
      marker.unbindTooltip();
      marker.bindTooltip(districtPrefix + (p.name||pid) + ' ' + capLabel, { sticky: true, className: 'province-tooltip', opacity: 0.85 });
    } else {
      // Non-capital: reset tooltip to just the name
      marker.unbindTooltip();
      marker.bindTooltip(districtPrefix + (p.name||pid), { sticky: true, className: 'province-tooltip', opacity: 0.85 });
    }

    const ownerHtml = owner ? `<div style="margin-top:2px;">📍 <span style="color:${owner.isPlayer?'var(--green)':'var(--text)'};">${owner.name}</span></div>` : '';
    marker.unbindPopup();
    const ind = p.industry||0, agr = p.agriculture||0, com = p.commerce||0;
    const rw = p.railway||0, pt = p.port||0, pop = p.population||1;
    const res = (p.resources||[]).length ? ' · ' + p.resources.join(' ') : '';
    const rwText = rw>0 ? ' · 🚂Lv'+rw : '';
    const ptText = pt>0 ? ' · ⚓Lv'+pt : '';
    const indBar = '▮'.repeat(Math.ceil(ind/2)) + '▯'.repeat(5-Math.ceil(ind/2));
    const agrBar = '▮'.repeat(Math.ceil(agr/2)) + '▯'.repeat(5-Math.ceil(agr/2));
    const comBar = '▮'.repeat(Math.ceil(com/2)) + '▯'.repeat(5-Math.ceil(com/2));
    marker.bindPopup(`
      <div style="font-family:var(--font);min-width:180px;">
        <h4 style="color:var(--gold);margin:0 0 4px;">${districtPrefix}${p.name}</h4>
        <div style="font-size:0.85em;line-height:1.6;color:var(--text-dim);">
          地形: ${p.terrain||'?'} · 类型: ${p.type||'?'} · 👥${pop}
        </div>
        <div style="font-size:0.78em;line-height:1.6;color:var(--text);margin-top:3px;">
          <span style="color:var(--gold);">🏭</span>${indBar} ${ind} &nbsp;
          <span style="color:var(--gold);">🌾</span>${agrBar} ${agr} &nbsp;
          <span style="color:var(--gold);">🧧</span>${comBar} ${com}
        </div>
        <div style="font-size:0.75em;line-height:1.4;color:var(--text-dim);margin-top:1px;">
          ${rwText}${ptText}${res}
        </div>${ownerHtml}${garrHtml}
      </div>`);
  }
  // ── 首都名称标签（增量更新）──
  _updateCapitalLabels(capitalPids);
  // ── 驻军标记（增量更新）──
  _updateGarrisonLayer(garrisons);

  // ── 战役战斗标记 ──
  if (battleLayer) battleLayer.clearLayers();
  const activeCampaigns = data.active_campaigns || [];
  for (const camp of activeCampaigns) {
    const pid = camp.province;
    const p = mapProvinceData[pid];
    if (!p || p.lat == null || p.lng == null) continue;
    const remaining = Math.max(0, (camp.max_rounds || 4) - (camp.round || 0));
    const isPlayerInvolved = camp.is_player_attacker || camp.is_player_defender;
    const borderColor = isPlayerInvolved ? '#f80' : '#a60';
    const bgColor = isPlayerInvolved ? 'rgba(100,50,10,0.85)' : 'rgba(70,30,5,0.85)';
    const icon = L.divIcon({
      html: `<div style="width:22px;height:22px;background:${bgColor};border:2px solid ${borderColor};border-radius:50%;text-align:center;line-height:18px;font-size:11px;font-weight:bold;color:#f90;cursor:pointer;font-family:var(--font);animation:battle-pulse 1.5s ease-in-out infinite;">⚔</div>`,
      className: '', iconSize: [22, 22], iconAnchor: [11, 11],
    });
    const bMarker = L.marker([p.lat, p.lng], { icon, interactive: true, keyboard: false, zIndexOffset: 500 }).addTo(battleLayer);
    bMarker.bindTooltip(`<b>⚔ ${p.name} · 战役中</b><br>${camp.attacker_name} → ${camp.defender_name}<br>第${camp.round}轮 · 预计剩${remaining}轮`, {
      direction: 'top', offset: [0, -16],
    });
    bMarker.on('click', (e) => {
      L.DomEvent.stopPropagation(e);
      showBattleInfoPopup(camp);
    });
  }

  // ── 势力名称标签 ──
  console.log('[applyOwnership] calling rebuildFactionLabels, cityStoreMap size:', Object.keys(cityStoreMap).length, 'capitalPids:', Object.keys(capitalPids).length, 'factionLabelLayer:', !!factionLabelLayer, 'capitalLabelLayer:', !!capitalLabelLayer);
  rebuildFactionLabels();
  console.log('[applyOwnership] done');

  // ── 途中部队路径可视化 ──
  if (movePathLayer) movePathLayer.clearLayers();
  if (data.moving_units) {
    for (const mu of data.moving_units) {
      const posP = mapProvinceData[mu.position];
      const tgtP = mapProvinceData[mu.move_target];
      if (posP && tgtP && posP.lat != null && tgtP.lat != null) {
        // 虚线箭头从当前位置到目标
        const midLat = (posP.lat + tgtP.lat) / 2;
        const midLng = (posP.lng + tgtP.lng) / 2;
        L.polyline([[posP.lat, posP.lng], [tgtP.lat, tgtP.lng]], {
          color: '#f0a030', weight: 2, opacity: 0.7, dashArray: '6,6',
        }).addTo(movePathLayer);
        // 中点标注
        const icon = L.divIcon({
          html: `<div style="font-size:9px;color:#f0a030;text-shadow:0 0 3px #000;white-space:nowrap;">◎${mu.name}</div>`,
          className: '', iconSize: [0,0], iconAnchor: [0,0],
        });
        L.marker([midLat, midLng], { icon, interactive: false }).addTo(movePathLayer);
      }
    }
  }

  // 动态势力边界
  updateFactionBoundaries(data);
}

// ── 动态势力边界 ──
function updateFactionBoundaries(data) {
  if (!factionBoundaryLayer || !factionBoundaryLayer.clearLayers) return;
  factionBoundaryLayer.clearLayers();

  const fbData = data.faction_boundaries;
  if (fbData && fbData.features && fbData.features.length > 0) {
    L.geoJSON(fbData, {
      style: function(feature) {
        const c = (feature.properties && feature.properties.color) || '#888';
        return { color: c, weight: 1.5, opacity: 0.45, dashArray: '4,4' };
      },
      interactive: false,
    }).addTo(factionBoundaryLayer);
  } else if (staticFactionBoundaries) {
    // 回退到静态边界
    L.geoJSON(staticFactionBoundaries, {
      style: function(feature) {
        const c = (feature.properties && feature.properties.color) || '#888';
        return { color: c, weight: 1.5, opacity: 0.45, dashArray: '4,4' };
      },
      interactive: false,
    }).addTo(factionBoundaryLayer);
  }
}

// ── 驻军弹窗（含归属、兵力详情、命令按钮）──
function showGarrisonPopup(pid, units, isPlayer) {
  const p = mapProvinceData[pid];
  if (!p || p.lat == null || p.lng == null) return;
  var factionName = (units[0] && units[0].faction_name) ? units[0].faction_name : (isPlayer ? '我方' : '敌军');
  var totalAtk = 0, totalDef = 0, totalStr = 0;
  units.forEach(function(u) { totalAtk += (u.attack||0); totalDef += (u.defense||0); totalStr += (u.strength||0); });
  let html = `<div style="font-family:var(--font);min-width:200px;max-width:280px;">`;
  html += `<h4 style="color:var(--gold);margin:0 0 2px;">${p.name}</h4>`;
  html += `<div style="font-size:0.8em;color:${isPlayer?'var(--green)':'var(--red)'};margin-bottom:4px;">`;
  html += (isPlayer ? '🟢 ' : '🔴 ') + factionName + ' · ' + units.length + '支部队</div>';
  html += `<div style="font-size:0.75em;color:var(--text-dim);margin-bottom:4px;">合计 ⚔${totalAtk} 🛡${totalDef} ❤${totalStr}</div>`;
  html += `<div style="border-top:1px solid var(--border);margin:4px 0;padding-top:4px;">`;
  units.slice(0, 6).forEach(function(u) {
    html += `<div style="font-size:0.78em;padding:1px 0;color:var(--text);display:flex;justify-content:space-between;">`;
    html += `<span>${u.name || u.type || '?'}</span>`;
    html += `<span style="color:var(--text-dim);font-size:0.9em;">⚔${u.attack||0} 🛡${u.defense||0} ❤${u.strength||0}%</span>`;
    html += `</div>`;
  });
  if (units.length > 6) html += `<div style="font-size:0.72em;color:var(--text-dim);">+${units.length-6} 更多...</div>`;
  html += `</div>`;

  if (isPlayer) {
    html += `<div style="margin-top:6px;border-top:1px solid var(--border);padding-top:4px;">`;
    html += `<button onclick="enterCommandMode('${pid}')" style="background:var(--gold-bg);border:1px solid var(--gold);color:var(--gold);padding:2px 10px;border-radius:3px;cursor:pointer;font-family:var(--font);font-size:0.8em;">⚔ 命令</button>`;
    html += `</div>`;
  }
  html += `</div>`;

  L.popup({ closeButton: true, maxWidth: 280 })
    .setLatLng([p.lat, p.lng])
    .setContent(html)
    .openOn(leafletMap);
}


// ── 部队选择系统 ──────────────────────────────────
function toggleUnitSelection(pid, units, marker) {
  if (selectedUnitPids.has(pid)) {
    // 取消选择
    selectedUnitPids.delete(pid);
    selectedUnitData = selectedUnitData.filter(d => d.pid !== pid);
    if (marker && marker._icon) {
      marker._icon.classList.remove('garrison-marker-selected');
    }
  } else {
    // 添加选择
    selectedUnitPids.add(pid);
    selectedUnitData.push({ pid, units: units.slice(), marker });
    if (marker && marker._icon) {
      marker._icon.classList.add('garrison-marker-selected');
    }
  }
  updateSelectionUI();
}

function clearUnitSelection() {
  // 清除所有选中标记的视觉样式
  selectedUnitData.forEach(d => {
    if (d.marker && d.marker._icon) {
      d.marker._icon.classList.remove('garrison-marker-selected');
    }
  });
  selectedUnitPids.clear();
  selectedUnitData = [];
  selectedUnitIndices.clear();
  updateSelectionUI();
}

function updateSelectionUI() {
  const bar = document.getElementById('sel-info');
  const countEl = document.getElementById('sel-count-text');
  if (!bar || !countEl) return;

  const totalProvinceUnits = selectedUnitData.length;
  const totalIndexUnits = selectedUnitIndices.size;

  if (totalProvinceUnits === 0 && totalIndexUnits === 0) {
    bar.classList.remove('show');
    return;
  }

  // 优先显示按部队索引的选择（更精细）
  if (totalIndexUnits > 0) {
    const names = [];
    for (const gIdx of selectedUnitIndices) {
      const u = unitCache.get(gIdx);
      if (u) names.push(u.name);
    }
    countEl.textContent = '已选 ' + totalIndexUnits + '支部队' + (names.length <= 8 ? ': ' + names.join(', ') : '');
  } else {
    let totalUnits = 0;
    const pNames = [];
    selectedUnitData.forEach(d => {
      totalUnits += d.units.length;
      const p = mapProvinceData[d.pid];
      pNames.push((p ? p.name : d.pid) + ' ' + d.units.length + '支');
    });
    countEl.textContent = '已选 ' + pNames.join(', ') + ' 共' + totalUnits + '支部队';
  }
  bar.classList.add('show');
}

async function batchMoveSelected() {
  if (selectedUnitData.length === 0 && selectedUnitIndices.size === 0) { statusText('未选中任何部队'); return; }
  // 优先使用按部队索引的选择
  if (selectedUnitIndices.size > 0) {
    // 找第一支部队的位置
    let firstPid = '';
    for (const gIdx of selectedUnitIndices) {
      const u = unitCache.get(gIdx);
      if (u && u.position) { firstPid = u.position; break; }
    }
    if (firstPid) {
      enterCommandMode(firstPid, [...selectedUnitIndices]);
      return;
    }
  }
  // 回退：按省份选择
  const allPids = [];
  selectedUnitData.forEach(d => { allPids.push(d.pid); });
  statusText('已选中 ' + allPids.join(',') + ' 的部队，请在地图上点击目标位置（右键取消）');
  enterCommandMode(allPids[0]);
}

async function batchAttackSelected() {
  if (selectedUnitData.length === 0 && selectedUnitIndices.size === 0) { statusText('未选中任何部队'); return; }
  if (selectedUnitIndices.size > 0) {
    let firstPid = '';
    for (const gIdx of selectedUnitIndices) {
      const u = unitCache.get(gIdx);
      if (u && u.position) { firstPid = u.position; break; }
    }
    if (firstPid) {
      enterCommandMode(firstPid, [...selectedUnitIndices], 'attack');
      return;
    }
  }
  const allPids = [];
  selectedUnitData.forEach(d => { allPids.push(d.pid); });
  enterCommandMode(allPids[0], null, 'attack');
}

// ── 部队选择弹窗（点击驻军标记）──────────────────
function showUnitSelectionPopup(pid, units, marker) {
  try { if (leafletMap) leafletMap.closePopup(); } catch(e) {}
  const p = mapProvinceData[pid];
  const pname = p ? p.name : pid;

  // 构建部队缓存（全局索引 → 部队数据）
  let rows = '';
  const allUnitIndices = [];
  units.forEach((u, fallbackIdx) => {
    const gIdx = u.index != null ? u.index : fallbackIdx;
    allUnitIndices.push(gIdx);
    unitCache.set(gIdx, { name: u.name, position: pid, attack: u.attack, defense: u.defense, strength: u.strength, morale: u.morale, type: u.type, icon: u.icon });
    const checked = (gIdx != null && selectedUnitIndices.has(gIdx)) ? 'checked' : '';
    const icon = u.icon || '⚔';
    rows += `<label style="display:flex;align-items:center;gap:6px;padding:3px 0;cursor:pointer;font-size:0.85em;color:var(--text);">
      <input type="checkbox" value="${gIdx}" ${checked} onchange="onPopupUnitToggle(this,${gIdx})">
      <span>${icon} ${u.name}</span>
      <span style="color:var(--text-dim);font-size:0.8em;">⚔${u.attack||0} 🛡${u.defense||0} ❤${u.strength||0}%</span>
    </label>`;
  });

  const html = `<div style="font-family:var(--font);min-width:220px;max-width:300px;">
    <h4 style="color:var(--gold);margin:0 0 4px;">⚔ ${pname} · ${units.length}支部队</h4>
    <div style="max-height:200px;overflow-y:auto;margin:4px 0;">${rows}</div>
    <div style="display:flex;gap:4px;flex-wrap:wrap;margin-top:6px;">
      <button onclick="popupSelectAll(${JSON.stringify(allUnitIndices)},true)" style="background:var(--panel2);border:1px solid var(--border);color:var(--text);padding:3px 8px;border-radius:3px;cursor:pointer;font-family:var(--font);font-size:0.8em;">全选</button>
      <button onclick="popupSelectAll(${JSON.stringify(allUnitIndices)},false)" style="background:var(--panel2);border:1px solid var(--border);color:var(--text-dim);padding:3px 8px;border-radius:3px;cursor:pointer;font-family:var(--font);font-size:0.8em;">取消</button>
      <button onclick="popupMoveSelected('${pid}',${JSON.stringify(allUnitIndices)})" style="background:var(--gold-bg);border:1px solid var(--gold);color:var(--gold);padding:3px 10px;border-radius:3px;cursor:pointer;font-family:var(--font);font-size:0.8em;">🚚 移动所选</button>
      <button onclick="popupAttackSelected('${pid}',${JSON.stringify(allUnitIndices)})" style="background:rgba(200,60,40,0.2);border:1px solid var(--red);color:var(--red);padding:3px 10px;border-radius:3px;cursor:pointer;font-family:var(--font);font-size:0.8em;">⚔ 攻击</button>
    </div>
  </div>`;

  const _popupData = { pid, allUnitIndices };
  if (p && p.lat != null) {
    L.popup({ closeButton: true, autoClose: false, maxWidth: 320 })
      .setLatLng([p.lat, p.lng])
      .setContent(html)
      .openOn(leafletMap);
    // 存储弹窗数据供回调使用
    window._unitPopupData = _popupData;
  }
}

function onPopupUnitToggle(cb, gIdx) {
  if (cb.checked) {
    selectedUnitIndices.add(gIdx);
  } else {
    selectedUnitIndices.delete(gIdx);
  }
  updateSelectionUI();
}

function popupSelectAll(indices, select) {
  indices.forEach(i => {
    if (select) selectedUnitIndices.add(i);
    else selectedUnitIndices.delete(i);
  });
  // 更新弹窗内复选框
  const popupEl = document.querySelector('.leaflet-popup-content');
  if (popupEl) {
    popupEl.querySelectorAll('input[type="checkbox"]').forEach(cb => {
      cb.checked = select;
    });
  }
  updateSelectionUI();
}

function popupMoveSelected(pid, allIndices) {
  try { if (leafletMap) leafletMap.closePopup(); } catch(e) {}
  allIndices.forEach(function(i) { selectedUnitIndices.add(i); });
  updateSelectionUI();
  enterCommandMode(pid, [...selectedUnitIndices]);
}

function popupAttackSelected(pid, allIndices) {
  try { if (leafletMap) leafletMap.closePopup(); } catch(e) {}
  allIndices.forEach(function(i) { selectedUnitIndices.add(i); });
  updateSelectionUI();
  enterCommandMode(pid, [...selectedUnitIndices], 'attack');
}

// ── 框选系统 ──────────────────────────────────
function initBoxSelect(container) {
  const mapEl = container;
  let rectEl = null;

  function onDocMove(e) {
    if (!boxSelectRect || !boxSelectStart) return;
    const mapRect = mapEl.getBoundingClientRect();
    const cx = e.clientX - mapRect.left;
    const cy = e.clientY - mapRect.top;
    const left = Math.min(boxSelectStart.x, cx);
    const top = Math.min(boxSelectStart.y, cy);
    const w = Math.abs(cx - boxSelectStart.x);
    const h = Math.abs(cy - boxSelectStart.y);
    boxSelectRect.style.left = left + 'px';
    boxSelectRect.style.top = top + 'px';
    boxSelectRect.style.width = w + 'px';
    boxSelectRect.style.height = h + 'px';
  }

  function onDocUp(e) {
    document.removeEventListener('mousemove', onDocMove);
    document.removeEventListener('mouseup', onDocUp);
    if (leafletMap.dragging) leafletMap.dragging.enable();
    if (!boxSelectRect || !boxSelectStart) return;
    const mapRect = mapEl.getBoundingClientRect();
    const ex = e.clientX - mapRect.left;
    const ey = e.clientY - mapRect.top;
    const minX = Math.min(boxSelectStart.x, ex);
    const maxX = Math.max(boxSelectStart.x, ex);
    const minY = Math.min(boxSelectStart.y, ey);
    const maxY = Math.max(boxSelectStart.y, ey);

    if (boxSelectRect.parentNode) boxSelectRect.parentNode.removeChild(boxSelectRect);
    boxSelectRect = null;
    boxSelectStart = null;

    if (maxX - minX < 10 && maxY - minY < 10) return;

    if (!e.shiftKey) clearUnitSelection();
    for (const [pid, marker] of Object.entries(garrisonMarkers)) {
      if (!marker._icon) continue;
      const iconRect = marker._icon.getBoundingClientRect();
      const cx = iconRect.left + iconRect.width / 2 - mapRect.left;
      const cy = iconRect.top + iconRect.height / 2 - mapRect.top;
      if (cx >= minX && cx <= maxX && cy >= minY && cy <= maxY) {
        const data = marker._data;
        if (data && data.isPlayer) {
          toggleUnitSelection(pid, data.units, marker);
        }
      }
    }
    updateSelectionUI();
  }

  leafletMap.on('mousedown', function(e) {
    if (!e.originalEvent.shiftKey || e.originalEvent.button !== 0) return;
    if (moveMode) return;
    // 禁用地图拖拽，防止Shift+拖拽时地图移动
    if (leafletMap.dragging) leafletMap.dragging.disable();
    const mapRect = mapEl.getBoundingClientRect();
    const sx = e.originalEvent.clientX - mapRect.left;
    const sy = e.originalEvent.clientY - mapRect.top;
    boxSelectStart = { x: sx, y: sy };

    rectEl = document.createElement('div');
    rectEl.className = 'box-select-rect';
    rectEl.style.left = sx + 'px';
    rectEl.style.top = sy + 'px';
    rectEl.style.width = '0px';
    rectEl.style.height = '0px';
    mapEl.appendChild(rectEl);
    boxSelectRect = rectEl;

    document.addEventListener('mousemove', onDocMove);
    document.addEventListener('mouseup', onDocUp);
  });
}

// ── 势力信息弹窗（右键省份）──
var REGION_NAMES = {
  'northeast':'东北','huabei':'华北','southwest':'西南','southeast':'东南',
  'xibei':'西北','lingnan':'岭南','nanyang':'南洋'
};

const RESOURCE_NAMES = {coal:'煤矿',iron:'铁矿',oil:'油田',timber:'木材',arsenal:'兵工厂',horses:'马场',silk:'丝绸',tea:'茶叶',salt:'盐场',rubber:'橡胶',rice:'稻米',textile:'纺织'};

async function showProvinceDetail(pid) {
  const p = mapProvinceData[pid];
  if (!p || p.lat == null) {
    // 无标记数据的省份，用 API 数据单独弹窗
    try {
      const resp = await fetch('/api/map/province-detail?pid=' + encodeURIComponent(pid));
      const d = await resp.json();
      if (d.error) { alert('省份数据错误: ' + d.error); return; }
      // 尝试从 city_store 找坐标
      const cs = cityStoreMap[d.name];
      const lat = (p && p.lat != null) ? p.lat : (cs ? cs.lat : null);
      const lng = (p && p.lng != null) ? p.lng : (cs ? cs.lng : null);
      if (lat == null) { alert('无法定位省份: ' + d.name); return; }
      showDetailPopup(pid, d, lat, lng);
      return;
    } catch(e) {
      console.error('Fallback fetch failed:', e);
      alert('加载省份详情失败，请检查网络');
      return;
    }
  }

  try {
    const resp = await fetch('/api/map/province-detail?pid=' + encodeURIComponent(pid));
    const d = await resp.json();
    if (d.error) { alert(d.error); return; }
    showDetailPopup(pid, d, p.lat, p.lng);
  } catch(e) {
    console.error('Province detail failed:', e);
    // 即使 API 失败，也展示已知信息
    showBasicProvincePopup(pid, p);
  }
}

// 从 API 数据渲染完整弹窗
function showDetailPopup(pid, d, lat, lng) {
  const p = mapProvinceData[pid] || {};
  const bar = (v) => {
    const n = Math.max(0, Math.min(10, Math.round(v || 0)));
    const blocks = ['▯','▯','▯','▯','▯','▯','▯','▯','▯','▯'];
    for (let i = 0; i < n; i++) blocks[i] = '▮';
    return blocks.join('');
  };

  const ind = d.industry || 0;
  const agr = d.agriculture || 0;
  const com = d.commerce || 0;
  const rw = d.railway || 0;
  const pt = d.port || 0;
  const pop = d.population || 1;
  const resources = d.resources || [];
  const buildings = d.buildings || {};
  // 优先用 API 返回值，回退到本地 ownedBy（来自 /api/map 的实时归属数据）
  const localOwner = ownedBy[pid];
  const isMine = d.is_owned_by_player || (localOwner && localOwner.isPlayer) || false;
  const ownerName = d.owner_faction_name || (localOwner ? localOwner.name : null) || '无主';

  const effInd = d.effective_industry || ind;
  const effAgr = d.effective_agriculture || agr;
  const facLv = buildings.factory || 0;
  const irrLv = buildings.irrigation || 0;
  const acadLv = buildings.academy || 0;

  let resHtml = '';
  if (resources.length) {
    resHtml = '<div style="font-size:0.78em;color:var(--gold);margin-top:2px;">' +
      resources.map(r => RESOURCE_NAMES[r] || r).join(' · ') + '</div>';
  }

  const rwLabels = ['无','支线','干线','枢纽'];
  const ptLabels = ['无','小港','中港','大港'];
  let infraHtml = '';
  if (rw > 0 || pt > 0) {
    infraHtml = '<div style="font-size:0.78em;color:var(--text-dim);margin-top:2px;">';
    if (rw > 0) infraHtml += '🚂 ' + rwLabels[rw] + '铁路 ';
    if (pt > 0) infraHtml += '⚓ ' + ptLabels[pt];
    infraHtml += '</div>';
  }

  let bldHtml = '';
  if (isMine && (facLv > 0 || irrLv > 0 || acadLv > 0)) {
    bldHtml = '<div style="margin-top:6px;border-top:1px solid var(--border);padding-top:4px;">' +
      '<div style="font-size:0.75em;color:var(--text-dim);margin-bottom:3px;">已有建筑</div>';
    if (facLv > 0) bldHtml += '<div style="font-size:0.78em;color:var(--text);">🏭 工厂 Lv' + facLv + ' <span style="color:var(--text-dim);">(+' + (facLv*2) + '工业)</span></div>';
    if (irrLv > 0) bldHtml += '<div style="font-size:0.78em;color:var(--text);">🌾 水利 Lv' + irrLv + ' <span style="color:var(--text-dim);">(+' + (irrLv*2) + '农业)</span></div>';
    if (acadLv > 0) bldHtml += '<div style="font-size:0.78em;color:var(--text);">🎖 军校 Lv' + acadLv + '</div>';
    bldHtml += '</div>';
  }

  let buildHtml = '';
  if (isMine) {
    buildHtml = '<div style="margin-top:6px;border-top:1px solid var(--border);padding-top:4px;">' +
      '<div style="font-size:0.75em;color:var(--text-dim);margin-bottom:3px;">快速建设</div>' +
      '<div style="display:flex;gap:4px;flex-wrap:wrap;">' +
      '<button class="btn-sm" onclick="doBuildInPopup(\'2.1\',\'' + pid + '\')" title="10💰 · 6回合">🏭 工厂</button>' +
      '<button class="btn-sm" onclick="doBuildInPopup(\'2.2\',\'' + pid + '\')" title="5💰 · 3回合">🌾 水利</button>' +
      '<button class="btn-sm" onclick="doBuildInPopup(\'2.3\',\'' + pid + '\')" title="8💰 · 4回合">🎖 军校</button>' +
      '</div></div>';
  }

  let effHtml = '';
  if (isMine && (facLv > 0 || irrLv > 0)) {
    effHtml = '<div style="font-size:0.7em;color:var(--cyan);margin-top:1px;">有效: 🏭' + effInd + ' 🌾' + effAgr + '</div>';
  }

  const ownerColor = isMine ? 'var(--green)' : 'var(--text-dim)';
  const ownerTag = isMine ? ' [你的领地]' : '';
  const desc = d.desc ? '<div style="font-size:0.75em;color:var(--gold);margin:3px 0;font-style:italic;">' + d.desc + '</div>' : '';

  const html = '<div style="font-family:var(--font);min-width:240px;max-width:320px;">' +
    '<h4 style="color:var(--gold);margin:0 0 2px;">' + d.name + '</h4>' +
    '<div style="font-size:0.8em;color:var(--text-dim);margin-bottom:3px;">' +
      (d.terrain||'?') + ' · ' + (d.type||'?') + ' · ' + (d.district||'') + ' · 👥' + pop + '万' +
    '</div>' +
    desc +
    '<div style="font-size:0.82em;line-height:1.7;color:var(--text);">' +
      '🏭 <span style="color:var(--text-dim);">工业</span> ' + ind + ' <span style="font-size:0.7em;color:var(--text-dim);">' + bar(ind) + '</span><br>' +
      '🌾 <span style="color:var(--text-dim);">农业</span> ' + agr + ' <span style="font-size:0.7em;color:var(--text-dim);">' + bar(agr) + '</span><br>' +
      '🧧 <span style="color:var(--text-dim);">商业</span> ' + com + ' <span style="font-size:0.7em;color:var(--text-dim);">' + bar(com) + '</span>' +
    '</div>' +
    effHtml +
    infraHtml +
    resHtml +
    bldHtml +
    buildHtml +
    '<div style="margin-top:6px;border-top:1px solid var(--border);padding-top:3px;font-size:0.78em;color:' + ownerColor + ';">' +
      '归属: ' + ownerName + ownerTag +
    '</div>' +
    '<div style="margin-top:2px;font-size:0.72em;">' +
      '<a href="javascript:void(0)" onclick="leafletMap.closePopup();showFactionInfo(\'' + pid + '\')" style="color:var(--cyan);">查看势力详情 →</a>' +
    '</div>' +
  '</div>';

  leafletMap.closePopup();
  L.popup({ closeButton: true, maxWidth: 340 })
    .setLatLng([lat, lng])
    .setContent(html)
    .openOn(leafletMap);
}

// API 失败时的回退弹窗（仅用 mapProvinceData 的数据）
function showBasicProvincePopup(pid, p) {
  const ind = p.industry || 0, agr = p.agriculture || 0, com = p.commerce || 0;
  const bar = (v) => {
    const n = Math.max(0, Math.min(10, Math.round(v || 0)));
    const blocks = ['▯','▯','▯','▯','▯','▯','▯','▯','▯','▯'];
    for (let i = 0; i < n; i++) blocks[i] = '▮';
    return blocks.join('');
  };
  const owner = ownedBy[pid];
  const ownerName = owner ? owner.name : '无主';
  const isMine = owner && owner.isPlayer;

  const html = '<div style="font-family:var(--font);min-width:200px;max-width:280px;">' +
    '<h4 style="color:var(--gold);margin:0 0 4px;">' + (p.name || pid) + '</h4>' +
    '<div style="font-size:0.85em;line-height:1.6;color:var(--text-dim);">' +
      '地形: ' + (p.terrain||'?') + ' · 类型: ' + (p.type||'?') + ' · 👥' + (p.population||1) +
    '</div>' +
    '<div style="font-size:0.78em;line-height:1.6;color:var(--text);margin-top:3px;">' +
      '🏭' + bar(ind) + ' ' + ind + ' · 🌾' + bar(agr) + ' ' + agr + ' · 🧧' + bar(com) + ' ' + com +
    '</div>' +
    '<div style="margin-top:4px;font-size:0.78em;color:var(--text-dim);">归属: ' + ownerName + '</div>' +
    (isMine ? '<div style="margin-top:4px;font-size:0.72em;color:var(--gold);">(服务器未连接，仅显示基本信息)</div>' : '') +
  '</div>';

  leafletMap.closePopup();
  L.popup({ closeButton: true, maxWidth: 300 })
    .setLatLng([p.lat, p.lng])
    .setContent(html)
    .openOn(leafletMap);
}

// 从弹窗内直接建设
async function doBuildInPopup(buildId, pid) {
  leafletMap.closePopup();
  await doBuild(buildId, pid);
}

async function showFactionInfo(pid) {
  try {
    const resp = await fetch('/api/map/faction-info?pid=' + encodeURIComponent(pid));
    const info = await resp.json();
    if (info.error) {
      statusText(info.error);
      return;
    }

    const p = mapProvinceData[pid];
    const statNames = {'military':'⚔军事','economy':'💰经济','agriculture':'🌾农业','industry':'🏭工业','ideology':'📖思想','diplomacy':'🌐外交','navy':'⚓海军'};
    let statLines = '';
    for (const [k, v] of Object.entries(info.stats || {})) {
      const label = statNames[k] || k;
      statLines += `<div style="margin:1px 0;">${label}: <b>${v}</b></div>`;
    }

    const regionName = REGION_NAMES[info.region] || info.region || '?';
    const ownerTag = info.is_player ? ' <span style="color:var(--green);">[你]</span>' : '';
    let spiritHtml = '';
    if (info.national_spirit && typeof info.national_spirit === 'object') {
      const sp = info.national_spirit;
      spiritHtml = `<div style="margin-top:4px;font-size:0.75em;color:var(--gold);border-top:1px solid var(--border);padding-top:3px;">精神: ${sp.name||''}</div>`;
    }

    // 领土经济摘要
    let econHtml = '';
    if (info.territory_economy) {
      const e = info.territory_economy;
      econHtml = `<div style="margin-top:3px;font-size:0.75em;color:var(--cyan);border-top:1px solid var(--border);padding-top:3px;">
        🏭${e.industry||0} 🌾${e.agriculture||0} 🧧${e.commerce||0} · 🚂${e.railway_provinces||0}省 ⚓${e.port_provinces||0}港 · 👥${e.population||0}万
      </div>`;
    }

    const html = `<div style="font-family:var(--font);min-width:220px;max-width:300px;">
      <h4 style="color:var(--gold);margin:0 0 4px;">${info.name}${ownerTag}</h4>
      <div style="font-size:0.82em;color:var(--text-dim);margin-bottom:4px;">
        ${info.ideology||'?'} · ${regionName} · 领地 ${info.territory_count}
      </div>
      <div style="font-size:0.78em;color:var(--text);line-height:1.5;">${statLines}</div>
      ${econHtml}
      <div style="margin-top:4px;font-size:0.78em;color:var(--text-dim);">
        兵力: ${info.unit_count}支部队 · 总兵力 ${info.total_strength}
      </div>
      ${spiritHtml}
    </div>`;

    if (p && p.lat != null) {
      leafletMap.closePopup();
      L.popup({ closeButton: true, maxWidth: 320 })
        .setLatLng([p.lat, p.lng])
        .setContent(html)
        .openOn(leafletMap);
    } else {
      statusText('无法定位该省份坐标');
    }
  } catch(e) {
    statusText('网络错误，请重试');
    console.error('Faction info failed:', e);
  }
}

function buildMarkerIcon(pid, owner, isCapital) {
  const p = mapProvinceData[pid];
  if (!p) return L.divIcon({className:'map-marker',html:'',iconSize:[0,0]});
  const ts = TYPE_STYLES[isCapital ? 'capital' : p.type] || TYPE_STYLES.rural;
  const isPort = p.type === 'port';
  const baseColor = isPort ? '#4db8b8' : '#d4a853';
  const capColor = '#f0d060';
  const c = isCapital ? capColor : (owner ? (owner.color || REGION_COLORS[owner.region] || baseColor) : baseColor);
  const outer = ts.outer, inner = ts.inner;
  const ringW = isCapital ? 2.5 : 1.8;
  return L.divIcon({
    className: 'map-marker',
    html: `<div style="width:${outer}px;height:${outer}px;border:${ringW}px solid ${c};border-radius:50%;display:flex;align-items:center;justify-content:center;opacity:0.9;box-shadow:${isCapital?'0 0 6px 2px '+c:'none'};">
      <div style="width:${inner}px;height:${inner}px;background:${c};border-radius:50%;"></div>
    </div>`,
    iconSize: [outer, outer], iconAnchor: [outer/2, outer/2],
  });
}

