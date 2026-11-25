/* StockSense dashboard. All data comes from the SOAP endpoint via SoapClient. */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const num = v => (v === undefined || v === null || v === '') ? null : Number(v);
const fmt = (v, d = 2) => { const x = num(v); return x === null || Number.isNaN(x) ? '—' : x.toLocaleString(undefined, { minimumFractionDigits: d, maximumFractionDigits: d }); };
const pct = (v, d = 2) => { const x = num(v); return x === null || Number.isNaN(x) ? '—' : `${x > 0 ? '+' : ''}${fmt(x, d)}%`; };
const compact = v => { const x = num(v); return x === null ? '—' : new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 2 }).format(x); };
const sign = v => num(v) > 0 ? 'up' : num(v) < 0 ? 'down' : '';
const call = (op, params) => SoapClient.call(op, params);

const state = { symbol: null, range: '1Y', interval: 'DAILY', chartType: 'candle', tab: 'overview', loaded: {}, tech: null, watch: new Set(), seq: 0 };
const charts = {};

function ownerId() {
  let id = localStorage.getItem('stocksense.owner');
  if (!id || id.length < 8) {
    const raw = (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}${Math.random().toString(36).slice(2)}`);
    id = raw.replace(/[^A-Za-z0-9_-]/g, '').padEnd(12, 'x');
    localStorage.setItem('stocksense.owner', id);
  }
  return id;
}
const OWNER = ownerId();

let toastTimer;
function toast(msg) {
  const t = $('#toast');
  t.textContent = msg; t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, 6000);
}
const fail = e => toast(e && e.message ? e.message : 'Something went wrong');

if (window.Chart) {
  Chart.defaults.color = '#8b97ab';
  Chart.defaults.borderColor = 'rgba(255,255,255,.06)';
  Chart.defaults.font.family = 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';
}
function draw(id, config) {
  if (charts[id]) charts[id].destroy();
  charts[id] = new Chart($('#' + id), config);
}
const baseOptions = (extra = {}) => ({
  responsive: true, maintainAspectRatio: false, animation: false,
  interaction: { mode: 'index', intersect: false },
  plugins: { legend: { display: false } },
  scales: { x: { ticks: { maxTicksLimit: 8, maxRotation: 0 }, grid: { display: false } }, y: {} },
  ...extra,
});

/* ---------------------------------------------------------------- search */
let searchTimer;
const sugg = $('#suggestions');
$('#search').addEventListener('input', e => {
  clearTimeout(searchTimer);
  const q = e.target.value.trim();
  if (q.length < 1) { sugg.hidden = true; return; }
  searchTimer = setTimeout(async () => {
    try {
      const d = await call('searchStocks', { query: q, limit: 8 });
      sugg.innerHTML = d.stocks.map(s =>
        `<li data-sym="${esc(s.symbol)}"><span><b>${esc(s.symbol)}</b> ${esc(s.name)}</span><span class="muted small">${esc(s.exchange || '')}</span></li>`).join('');
      sugg.hidden = d.stocks.length === 0;
    } catch (err) { sugg.hidden = true; fail(err); }
  }, 250);
});
$('#search').addEventListener('keydown', e => {
  if (e.key === 'Enter') {
    const first = $('li', sugg);
    const typed = e.target.value.trim();
    const sym = !sugg.hidden && first ? first.dataset.sym : typed;
    if (sym) pick(sym);
  } else if (e.key === 'Escape') sugg.hidden = true;
});
sugg.addEventListener('click', e => { const li = e.target.closest('li'); if (li) pick(li.dataset.sym); });
document.addEventListener('click', e => { if (!e.target.closest('.search')) sugg.hidden = true; });
$('#quick').addEventListener('click', e => { const b = e.target.closest('button'); if (b) pick(b.dataset.sym); });
/* ---------------------------------------------------------------- home tiles */
const TILES = [['AAPL', 'Apple'], ['MSFT', 'Microsoft'], ['NVDA', 'NVIDIA'], ['GOOGL', 'Alphabet'], ['AMZN', 'Amazon'], ['TSLA', 'Tesla'],
  ['META', 'Meta Platforms'], ['RELIANCE.NS', 'Reliance Industries'], ['TCS.NS', 'Tata Consultancy Services'], ['INFY.NS', 'Infosys'],
  ['HDFCBANK.NS', 'HDFC Bank'], ['ITC.NS', 'ITC']];
function tileHtml(sym, name, q) {
  const px = q ? `${fmt(q.price)}<span class="muted small">${q.currency ? ' ' + esc(q.currency) : ''}</span>` : '…';
  const chg = q ? `${num(q.change) > 0 ? '▲' : num(q.change) < 0 ? '▼' : ''} ${pct(q.changePercent)}` : '';
  const chgAbs = q ? `${num(q.change) > 0 ? '+' : ''}${fmt(q.change)}` : '';
  return `<button class="tile ${q ? sign(q.change) : 'loading'}" data-sym="${esc(sym)}"><span class="t-sym">${esc(sym)}</span><span class="t-name">${esc(q && q.name ? q.name : name)}</span><span class="t-price">${px}</span><span class="t-abs ${q ? sign(q.change) : ''}">${chgAbs}</span><span class="t-chg ${q ? sign(q.change) : ''}">${chg}</span></button>`;
}
function loadTiles() {
  const box = $('#quick');
  box.innerHTML = '<div class="tile-head"><span>Symbol</span><span>Company</span><span>Price</span><span>Change</span><span>Change %</span></div>' +
    TILES.map(([s, n]) => tileHtml(s, n)).join('');
  TILES.forEach(([s, n]) => {
    call('getStockQuote', { symbol: s }).then(d => {
      const el = $(`.tile[data-sym="${s}"]`, box);
      if (el) el.outerHTML = tileHtml(s, n, d.quote);
    }).catch(() => {
      const el = $(`.tile[data-sym="${s}"] .t-price`, box);
      if (el) el.textContent = 'n/a';
    });
  });
}
$('#home').addEventListener('click', e => {
  e.preventDefault();
  state.seq++; state.symbol = null;
  $('#stock').hidden = true; $('#welcome').hidden = false;
  loadTiles();
});

function pick(sym) { sugg.hidden = true; $('#search').value = ''; selectSymbol(sym); }

/* ---------------------------------------------------------------- symbol selection */
async function selectSymbol(raw) {
  const sym = raw.trim().toUpperCase();
  const seq = ++state.seq;
  state.symbol = sym; state.loaded = {}; state.tech = null;
  $('#welcome').hidden = true; $('#stock').hidden = false;
  $('#symTitle').textContent = sym; $('#symName').textContent = 'Loading…';
  $('#price').textContent = ''; $('#change').textContent = ''; $('#quoteMeta').textContent = ''; $('#badges').innerHTML = '';
  $('#simBanner').hidden = true;
  clearPanels();
  try {
    const d = await call('getStockQuote', { symbol: sym });
    if (seq !== state.seq) return;
    renderQuote(d.quote);
  } catch (err) {
    if (seq !== state.seq) return;
    $('#symName').textContent = 'Could not load this symbol';
    fail(err); return;
  }
  updateWatchButton();
  $('#cmpSymbols').value = $('#cmpSymbols').value || `${sym}, MSFT`;
  loadTab(state.tab);
}

function renderQuote(q) {
  $('#symTitle').textContent = q.symbol;
  $('#symName').textContent = q.name;
  const cur = q.currency ? ` ${esc(q.currency)}` : '';
  $('#price').innerHTML = `${fmt(q.price)}<span class="muted small">${cur}</span>`;
  const c = $('#change');
  c.className = 'change ' + sign(q.change);
  c.textContent = `${num(q.change) > 0 ? '+' : ''}${fmt(q.change)}  (${pct(q.changePercent)})`;
  $('#quoteMeta').textContent = `O ${fmt(q.open)}  H ${fmt(q.high)}  L ${fmt(q.low)}  Vol ${compact(q.volume)}`;
  $('#badges').innerHTML = [q.exchange, q.source].filter(Boolean).map(b => `<span class="badge">${esc(b)}</span>`).join('');
  $('#simBanner').hidden = q.source !== 'simulated';
}

function clearPanels() {
  ['summary', 'fundamentals', 'company', 'signals', 'cmpTable', 'ohlcTable', 'indicatorCards'].forEach(id => { $('#' + id).innerHTML = ''; });
  $('#about').textContent = '';
  $('#predResult').innerHTML = 'Press <b>Run prediction</b> to train and evaluate the models on this stock\'s history.';
  $('#predModels').textContent = 'Run a prediction to see this.';
  $('#predHistory').textContent = ''; $('#predPerf').textContent = '';
  $('#overall').textContent = ''; $('#overall').className = 'pill'; $('#signalCounts').textContent = '';
  Object.keys(charts).forEach(k => { charts[k].destroy(); delete charts[k]; });
}

/* ---------------------------------------------------------------- tabs / controls */
$('#tabs').addEventListener('click', e => {
  const b = e.target.closest('button'); if (!b) return;
  state.tab = b.dataset.tab;
  $$('#tabs button').forEach(x => x.classList.toggle('active', x === b));
  $$('.panel').forEach(p => { p.hidden = p.id !== 'tab-' + state.tab; });
  if (state.symbol) loadTab(state.tab);
});
$('#ranges').addEventListener('click', e => {
  const b = e.target.closest('button'); if (!b) return;
  state.range = b.dataset.range;
  $$('#ranges button').forEach(x => x.classList.toggle('active', x === b));
  delete state.loaded.overview; delete state.loaded.technical;
  if (state.symbol) loadTab(state.tab);
});
$('#interval').addEventListener('change', e => { state.interval = e.target.value; delete state.loaded.overview; loadTab('overview'); });
$('#chartType').addEventListener('change', e => { state.chartType = e.target.value; if (state.bars) renderPriceChart(); });
['ovSma20', 'ovSma50', 'ovEma20', 'ovBb'].forEach(id => $('#' + id).addEventListener('change', () => state.tech && renderTechPrice()));

function loadTab(tab) {
  if (state.loaded[tab] === state.symbol && tab !== 'compare') return;
  const sym = state.symbol;
  state.loaded[tab] = sym;
  const job = { overview: loadOverview, technical: loadTechnical, prediction: loadPredictionPanels, compare: () => Promise.resolve() }[tab];
  job().catch(err => { if (state.loaded[tab] === sym) delete state.loaded[tab]; fail(err); });
}

/* ---------------------------------------------------------------- overview */
async function loadOverview() {
  const sym = state.symbol, seq = state.seq;
  const [h, info] = await Promise.all([
    call('getHistoricalData', { symbol: sym, range: state.range, interval: state.interval }),
    call('getCompanyInfo', { symbol: sym }).catch(() => null),
  ]);
  if (seq !== state.seq) return;
  state.bars = h.bars.map(b => ({ date: b.date, o: +b.open, h: +b.high, l: +b.low, c: +b.close, v: +b.volume }));
  renderPriceChart();
  renderVolume();
  renderOhlcTable();
  renderSummary(h.summary);
  if (info) renderFundamentals(info);
}

function renderOhlcTable() {
  const bars = state.bars;
  const shown = bars.slice(-20).reverse();
  const unit = { DAILY: 'day', WEEKLY: 'week', MONTHLY: 'month', YEARLY: 'year' }[state.interval] || 'bar';
  $('#ohlcNote').textContent = `latest ${shown.length} of ${bars.length} ${unit}ly bars`.replace('dayly', 'daily');
  $('#ohlcTable').innerHTML = '<tr><th>Date</th><th class="num">Open</th><th class="num">High</th><th class="num">Low</th><th class="num">Close</th><th class="num">Change</th><th class="num">Volume</th></tr>' +
    shown.map(b => {
      const ch = (b.c - b.o) / b.o * 100;
      return `<tr><td>${esc(b.date)}</td><td class="num">${fmt(b.o)}</td><td class="num">${fmt(b.h)}</td><td class="num">${fmt(b.l)}</td><td class="num"><b>${fmt(b.c)}</b></td><td class="num ${sign(ch)}">${pct(ch)}</td><td class="num">${compact(b.v)}</td></tr>`;
    }).join('');
}

function renderPriceChart() {
  const bars = state.bars;
  const labels = bars.map(b => b.date);
  const tip = { callbacks: { label: c => { const b = bars[c.dataIndex]; return `O ${fmt(b.o)}  H ${fmt(b.h)}  L ${fmt(b.l)}  C ${fmt(b.c)}`; } }, filter: i => i.datasetIndex === (state.chartType === 'candle' ? 1 : 0) };
  if (state.chartType === 'candle') {
    const colors = bars.map(b => b.c >= b.o ? '#22c55e' : '#ef4444');
    draw('priceChart', {
      type: 'bar',
      data: {
        labels, datasets: [
          { label: 'Range', data: bars.map(b => [b.l, b.h]), backgroundColor: colors, barPercentage: 0.1, categoryPercentage: 1, grouped: false, order: 2 },
          { label: 'Body', data: bars.map(b => [Math.min(b.o, b.c), Math.max(b.o, b.c)]), backgroundColor: colors, barPercentage: 0.8, categoryPercentage: 1, grouped: false, order: 1 },
        ],
      },
      options: baseOptions({ plugins: { legend: { display: false }, tooltip: tip }, scales: { x: { ticks: { maxTicksLimit: 8, maxRotation: 0 }, grid: { display: false } }, y: { beginAtZero: false, grace: '3%' } } }),
    });
  } else {
    draw('priceChart', {
      type: 'line',
      data: { labels, datasets: [{ label: 'Close', data: bars.map(b => b.c), borderColor: '#4f8cff', backgroundColor: 'rgba(79,140,255,.12)', fill: true, pointRadius: 0, borderWidth: 2, tension: 0.1 }] },
      options: baseOptions({ plugins: { legend: { display: false }, tooltip: { callbacks: { label: c => `Close ${fmt(c.parsed.y)}` } } } }),
    });
  }
}

function renderVolume() {
  const bars = state.bars;
  draw('volChart', {
    type: 'bar',
    data: { labels: bars.map(b => b.date), datasets: [{ data: bars.map(b => b.v), backgroundColor: bars.map(b => b.c >= b.o ? 'rgba(34,197,94,.55)' : 'rgba(239,68,68,.55)') }] },
    options: baseOptions({ plugins: { legend: { display: false }, tooltip: { callbacks: { label: c => `Volume ${compact(c.parsed.y)}` } } }, scales: { x: { display: false }, y: { ticks: { callback: v => compact(v), maxTicksLimit: 3 } } } }),
  });
}

const NA = '<span class="muted" title="Not provided by the data source for this stock">n/a</span>';
const kv = (label, value, cls = '') => `<div><span>${esc(label)}</span><b class="${cls}">${value}</b></div>`;

function renderSummary(s) {
  $('#summary').innerHTML = [
    kv('Period return', pct(s.changePercent), sign(s.changePercent)),
    kv('Start → end', `${fmt(s.startPrice)} → ${fmt(s.endPrice)}`),
    kv('High', fmt(s.high)), kv('Low', fmt(s.low)),
    kv('Avg volume', compact(s.averageVolume)),
    kv('Volatility (ann.)', `${fmt(s.annualizedVolatility)}%`),
    kv('Max drawdown', `-${fmt(s.maxDrawdownPercent)}%`, 'down'),
    kv('Best day', pct(s.bestDayPercent), 'up'), kv('Worst day', pct(s.worstDayPercent), 'down'),
    kv('Up / down days', `${esc(s.upDays)} / ${esc(s.downDays)}`),
    kv('From → to', `${esc(s.startDate)} → ${esc(s.endDate)}`),
  ].join('');
}

function renderFundamentals(i) {
  $('#fundamentals').innerHTML = [
    kv('Market cap', compact(i.marketCap)), kv('P/E (TTM)', fmt(i.peRatio)),
    kv('EPS (TTM)', fmt(i.eps)), kv('ROE', i.roe === undefined ? NA : `${fmt(i.roe)}%`),
    kv('ROCE', i.roce === undefined ? NA : `${fmt(i.roce)}%`), kv('Dividend yield', i.dividendYield ? `${fmt(i.dividendYield)}%` : '—'),
    kv('Beta', fmt(i.beta)), kv('52w high', fmt(i.week52High)), kv('52w low', fmt(i.week52Low)),
  ].join('');
  $('#company').innerHTML = [
    kv('Sector', esc(i.sector || '—')), kv('Industry', esc(i.industry || '—')),
    kv('Exchange', esc(i.exchange || '—')), kv('Country', esc(i.country || '—')),
  ].join('');
  const about = $('#about');
  about.textContent = i.description || '';
  if (i.website && /^https?:\/\//i.test(i.website)) {
    const a = document.createElement('a');
    a.href = i.website; a.textContent = ' ' + i.website; a.target = '_blank'; a.rel = 'noopener noreferrer';
    about.appendChild(a);
  }
}

/* ---------------------------------------------------------------- technical */
async function loadTechnical() {
  const seq = state.seq;
  const d = await call('getTechnicalAnalysis', { symbol: state.symbol, range: state.range });
  if (seq !== state.seq) return;
  state.tech = d;
  renderTechPrice();
  const pts = d.series;
  const labels = pts.map(p => p.date);
  const col = k => pts.map(p => p[k] === undefined ? null : +p[k]);
  const flat = v => pts.map(() => v);
  draw('techRsi', {
    type: 'line',
    data: { labels, datasets: [
      { data: col('rsi14'), borderColor: '#a78bfa', pointRadius: 0, borderWidth: 1.6 },
      { data: flat(70), borderColor: 'rgba(239,68,68,.5)', borderDash: [4, 4], pointRadius: 0, borderWidth: 1 },
      { data: flat(30), borderColor: 'rgba(34,197,94,.5)', borderDash: [4, 4], pointRadius: 0, borderWidth: 1 }] },
    options: baseOptions({ scales: { x: { display: false }, y: { min: 0, max: 100, ticks: { stepSize: 30 } } } }),
  });
  const hist = col('macdHistogram');
  draw('techMacd', {
    type: 'bar',
    data: { labels, datasets: [
      { type: 'bar', data: hist, backgroundColor: hist.map(v => v >= 0 ? 'rgba(34,197,94,.6)' : 'rgba(239,68,68,.6)') },
      { type: 'line', data: col('macd'), borderColor: '#4f8cff', pointRadius: 0, borderWidth: 1.4 },
      { type: 'line', data: col('macdSignal'), borderColor: '#f59e0b', pointRadius: 0, borderWidth: 1.4 }] },
    options: baseOptions({ scales: { x: { display: false }, y: { ticks: { maxTicksLimit: 4 } } } }),
  });
  renderIndicatorCards(pts[pts.length - 1], d);
  const ov = $('#overall');
  ov.textContent = d.overall; ov.className = 'pill ' + d.overall;
  $('#signalCounts').textContent = `${d.buySignals} buy · ${d.sellSignals} sell · ${d.neutralSignals} neutral — as of ${d.asOf}`;
  $('#signals').innerHTML = '<tr><th>Indicator</th><th class="num">Value</th><th>Signal</th><th>Reading</th></tr>' +
    d.signals.map(s => `<tr><td>${esc(s.indicator)}</td><td class="num">${fmt(s.value)}</td><td><span class="badge ${esc(s.signal)}">${esc(s.signal)}</span></td><td>${esc(s.note)}</td></tr>`).join('');
}

function renderIndicatorCards(p, d) {
  if (!p) return;
  const price = num(p.close);
  const rel = (v, label) => num(v) === null ? NA : `${fmt(v)} <span class="small ${price > num(v) ? 'up' : 'down'}">${price > num(v) ? 'price above' : 'price below'}</span>`;
  const rsi = num(p.rsi14);
  const rsiTxt = rsi === null ? NA : `${fmt(rsi, 1)} <span class="small ${rsi > 70 ? 'down' : rsi < 30 ? 'up' : 'muted'}">${rsi > 70 ? 'overbought' : rsi < 30 ? 'oversold' : 'neutral'}</span>`;
  const hist = num(p.macdHistogram);
  $('#indDate').textContent = `as of ${d.asOf}`;
  $('#indicatorCards').innerHTML = [
    kv('Close', fmt(price)),
    kv('RSI (14)', rsiTxt),
    kv('MACD line', num(p.macd) === null ? NA : fmt(p.macd, 3)),
    kv('MACD signal', num(p.macdSignal) === null ? NA : fmt(p.macdSignal, 3)),
    kv('MACD histogram', hist === null ? NA : fmt(hist, 3), sign(hist)),
    kv('SMA 20', rel(p.sma20)), kv('SMA 50', rel(p.sma50)), kv('EMA 20', rel(p.ema20)),
    kv('Bollinger upper', num(p.bbUpper) === null ? NA : fmt(p.bbUpper)),
    kv('Bollinger middle', num(p.bbMiddle) === null ? NA : fmt(p.bbMiddle)),
    kv('Bollinger lower', num(p.bbLower) === null ? NA : fmt(p.bbLower)),
    kv('ATR (14)', num(p.atr14) === null ? NA : fmt(p.atr14)),
  ].join('');
}

function renderTechPrice() {
  const pts = state.tech.series;
  const col = k => pts.map(p => p[k] === undefined ? null : +p[k]);
  const line = (data, color, dash) => ({ data, borderColor: color, pointRadius: 0, borderWidth: 1.4, borderDash: dash || [], spanGaps: true });
  const sets = [{ label: 'Close', data: col('close'), borderColor: '#e6ebf3', pointRadius: 0, borderWidth: 1.8 }];
  if ($('#ovSma20').checked) sets.push({ label: 'SMA 20', ...line(col('sma20'), '#4f8cff') });
  if ($('#ovSma50').checked) sets.push({ label: 'SMA 50', ...line(col('sma50'), '#f59e0b') });
  if ($('#ovEma20').checked) sets.push({ label: 'EMA 20', ...line(col('ema20'), '#a78bfa') });
  if ($('#ovBb').checked) {
    sets.push({ label: 'BB upper', ...line(col('bbUpper'), 'rgba(148,163,184,.7)', [5, 4]) });
    sets.push({ label: 'BB lower', ...line(col('bbLower'), 'rgba(148,163,184,.7)', [5, 4]) });
  }
  draw('techPrice', {
    type: 'line', data: { labels: pts.map(p => p.date), datasets: sets },
    options: baseOptions({ plugins: { legend: { display: true, labels: { boxWidth: 12 } }, tooltip: { callbacks: { label: c => `${c.dataset.label} ${fmt(c.parsed.y)}` } } } }),
  });
}

/* ---------------------------------------------------------------- prediction */
async function loadPredictionPanels() {
  const sym = state.symbol, seq = state.seq;
  const [hist, perf] = await Promise.all([
    call('getPredictionHistory', { symbol: sym, limit: 15 }),
    call('getModelPerformance', { symbol: sym }),
  ]);
  if (seq !== state.seq) return;
  renderHistory(hist);
  renderPerformance(perf, hist.summary);
}

$('#runPredict').addEventListener('click', async () => {
  if (!state.symbol) return;
  const btn = $('#runPredict'), seq = state.seq;
  btn.disabled = true; btn.textContent = 'Training models…';
  $('#predResult').classList.add('loading');
  try {
    const d = await call('predictStock', {
      symbol: state.symbol, horizonDays: $('#horizon').value, forceRetrain: $('#forceRetrain').checked ? 'true' : undefined,
    });
    if (seq !== state.seq) return;
    renderPrediction(d.prediction);
    $('#forceRetrain').checked = false;
    loadPredictionPanels().catch(fail);
  } catch (err) { fail(err); }
  finally { btn.disabled = false; btn.textContent = 'Run prediction'; $('#predResult').classList.remove('loading'); }
});

function renderPrediction(p) {
  const up = p.direction === 'UP';
  const pUp = num(p.probabilityUp), pDown = num(p.probabilityDown);
  const models = p.models || [];
  const best = models.find(m => m.selected === 'true');
  const base = num(p.baselineAccuracy);
  const edge = best && base !== null ? (num(best.accuracy) - base) * 100 : null;
  const low = p.reliability === 'LOW';
  const drivers = (p.drivers || []).map(d =>
    `<div><span>${esc(d.name)}</span><i style="width:${Math.max(2, num(d.importance) * 100)}%"></i><span>${fmt(num(d.importance) * 100, 0)}%</span></div>`).join('');
  $('#predResult').innerHTML = `
    <div class="pred-head ${up ? 'up' : 'down'}">
      <div class="arrow">${up ? '▲' : '▼'}</div>
      <div><div class="dir">${esc(p.direction)}</div>
        <div class="muted small">Close ${p.horizonDays} trading day(s) after ${esc(p.baseDate)} vs ${fmt(p.basePrice)} · target ${esc(p.targetDate)}</div></div>
      <div style="margin-left:auto" class="muted small">Model: <b>${esc(p.model)}</b> · ${esc(p.modelVersion)}${p.cached === 'true' ? ' · cached' : ''}</div>
    </div>
    <div class="meter"><div style="width:${pUp * 100}%"></div></div>
    <div class="meter-labels"><span class="up">&#9650; Up ${fmt(pUp * 100, 1)}%</span><span class="down">Down ${fmt(pDown * 100, 1)}% &#9660;</span></div>
    <div class="note"><span class="badge ${low ? 'WARN' : 'BUY'}">Reliability: ${esc(p.reliability || 'n/a')}</span>
      ${edge !== null ? ` Hold-out accuracy ${fmt(num(best.accuracy) * 100, 1)}% vs ${fmt(base * 100, 1)}% for always guessing the majority class (${edge >= 0 ? '+' : ''}${fmt(edge, 1)} pts).` : ''}
      ${low ? ' The model shows little or no edge over a naive baseline on unseen data, so treat this as weak evidence.' : ''}
      Trained on ${esc(p.trainSamples)} samples, tested on ${esc(p.testSamples)}.</div>
    ${drivers ? `<h3 style="margin-top:14px">Main drivers</h3><div class="drivers">${drivers}</div>` : ''}`;
  $('#predModels').innerHTML = `<table class="table"><tr><th>Model</th><th class="num">Accuracy</th><th class="num">Precision</th><th class="num">Recall</th><th class="num">F1</th><th class="num">ROC-AUC</th><th></th></tr>` +
    models.map(m => `<tr><td>${esc(m.name)}</td><td class="num">${fmt(num(m.accuracy) * 100, 1)}%</td><td class="num">${fmt(num(m.precision) * 100, 1)}%</td><td class="num">${fmt(num(m.recall) * 100, 1)}%</td><td class="num">${fmt(num(m.f1), 3)}</td><td class="num">${m.rocAuc === undefined ? '—' : fmt(m.rocAuc, 3)}</td><td>${m.selected === 'true' ? '<span class="badge BUY">selected</span>' : ''}</td></tr>`).join('') +
    `<tr><td class="muted">Baseline (majority class)</td><td class="num muted">${fmt(base * 100, 1)}%</td><td colspan="5"></td></tr></table>`;
}

function renderHistory(h) {
  const rows = h.predictions || [];
  $('#predHistory').innerHTML = rows.length === 0 ? 'No predictions yet for this stock.' :
    `<table class="table"><tr><th>Made on (data date)</th><th>Horizon</th><th>Call</th><th class="num">P(up)</th><th>Target date</th><th>Status</th><th class="num">Actual close</th><th>Result</th></tr>` +
    rows.map(r => `<tr><td>${esc(r.baseDate)}</td><td>${esc(r.horizonDays)}d</td><td class="${r.direction === 'UP' ? 'up' : 'down'}">${esc(r.direction)}</td><td class="num">${fmt(num(r.probabilityUp) * 100, 1)}%</td><td>${esc(r.targetDate)}</td><td>${esc(r.status)}</td><td class="num">${r.actualClose === undefined ? '—' : fmt(r.actualClose)}</td><td>${r.correct === undefined ? '' : (r.correct === 'true' ? '<span class="up">✔ correct</span>' : '<span class="down">✘ wrong</span>')}</td></tr>`).join('') + '</table>';
}

function renderPerformance(perf, tally) {
  const l = perf.livePerformance || tally;
  const ev = num(l.evaluated);
  $('#predPerf').innerHTML = `<div class="kv">${kv('Predictions made', esc(l.total))}${kv('Scored so far', esc(l.evaluated))}${kv('Correct', esc(l.correct))}${kv('Hit rate', ev ? `${fmt(l.hitRatePercent, 1)}%` : '—')}</div>` +
    `<p class="muted small">A prediction is scored once its target trading day has data. Small samples say very little about skill.</p>`;
}

/* ---------------------------------------------------------------- compare */
$('#runCompare').addEventListener('click', async () => {
  const symbols = $('#cmpSymbols').value.split(/[\s,;]+/).map(s => s.trim().toUpperCase()).filter(Boolean);
  const btn = $('#runCompare');
  btn.disabled = true;
  try {
    const d = await call('compareStocks', { symbols, range: $('#cmpRange').value });
    const palette = ['#4f8cff', '#22c55e', '#f59e0b', '#ef4444', '#a78bfa'];
    const dates = [...new Set(d.items.flatMap(i => i.series.map(p => p.date)))].sort();
    draw('cmpChart', {
      type: 'line',
      data: { labels: dates, datasets: d.items.map((it, idx) => {
        const m = new Map(it.series.map(p => [p.date, +p.value]));
        return { label: it.symbol, data: dates.map(x => m.has(x) ? m.get(x) : null), borderColor: palette[idx % 5], pointRadius: 0, borderWidth: 1.8, spanGaps: true };
      }) },
      options: baseOptions({ plugins: { legend: { display: true, labels: { boxWidth: 12 } }, tooltip: { callbacks: { label: c => `${c.dataset.label} ${fmt(c.parsed.y)}` } } } }),
    });
    $('#cmpTable').innerHTML = '<tr><th>Symbol</th><th>Name</th><th class="num">Price</th><th class="num">Return</th><th class="num">Volatility</th><th class="num">Max drawdown</th><th class="num">RSI</th><th>Technical view</th></tr>' +
      d.items.map(i => `<tr><td><b>${esc(i.symbol)}</b></td><td>${esc(i.name)}</td><td class="num">${fmt(i.price)}</td><td class="num ${sign(i.periodReturnPercent)}">${pct(i.periodReturnPercent)}</td><td class="num">${fmt(i.annualizedVolatility)}%</td><td class="num">-${fmt(i.maxDrawdownPercent)}%</td><td class="num">${i.rsi14 === undefined ? '—' : fmt(i.rsi14, 1)}</td><td><span class="pill ${esc(i.overall)}">${esc(i.overall)}</span></td></tr>`).join('');
  } catch (err) { fail(err); }
  finally { btn.disabled = false; }
});

/* ---------------------------------------------------------------- watchlist */
async function loadWatchlist() {
  try {
    const d = await call('getWatchlist', { owner: OWNER });
    state.watch = new Set(d.items.map(i => i.symbol));
    $('#watchEmpty').hidden = d.items.length > 0;
    $('#watchlist').innerHTML = d.items.map(i => `
      <li data-sym="${esc(i.symbol)}"><span class="sym">${esc(i.symbol)}</span>
        <span class="px">${i.price === undefined ? '—' : fmt(i.price)}<br><span class="small ${sign(i.changePercent)}">${i.changePercent === undefined ? '' : pct(i.changePercent)}</span></span>
        <span class="nm">${esc(i.name)}</span><button class="rm" data-rm="${esc(i.symbol)}">remove</button></li>`).join('');
    updateWatchButton();
  } catch (err) { fail(err); }
}
$('#watchlist').addEventListener('click', async e => {
  const rm = e.target.closest('[data-rm]');
  if (rm) { e.stopPropagation(); try { await call('removeFromWatchlist', { owner: OWNER, symbol: rm.dataset.rm }); } catch (err) { fail(err); } loadWatchlist(); return; }
  const li = e.target.closest('li'); if (li) selectSymbol(li.dataset.sym);
});
$('#watchBtn').addEventListener('click', async () => {
  if (!state.symbol) return;
  try {
    await call(state.watch.has(state.symbol) ? 'removeFromWatchlist' : 'addToWatchlist', { owner: OWNER, symbol: state.symbol });
  } catch (err) { fail(err); }
  loadWatchlist();
});
function updateWatchButton() {
  const on = state.symbol && state.watch.has(state.symbol);
  const b = $('#watchBtn');
  b.classList.toggle('on', !!on); b.innerHTML = on ? '&#9733;' : '&#9734;';
  b.title = on ? 'Remove from watchlist' : 'Add to watchlist';
}

/* ---------------------------------------------------------------- system status */
async function loadStatus() {
  const pill = (ok, label) => `<span class="dot ${ok === null ? 'off' : ok ? 'ok' : 'bad'}">${esc(label)}</span>`;
  try {
    const s = await call('getSystemStatus');
    $('#status').innerHTML = pill(true, 'API') + pill(s.database === 'UP', 'DB') + pill(s.mlService.status === 'UP', 'ML') +
      s.providers.map(p => pill(p.available === 'true' ? true : null, p.name)).join('');
  } catch (e) { $('#status').innerHTML = pill(false, 'API offline'); }
}

loadStatus();
loadTiles();
setInterval(loadStatus, 60000);
loadWatchlist();
