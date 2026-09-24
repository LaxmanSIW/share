/* ==========================================================================
   AURORA REDESIGN · Editorial SVG charts (no dependencies, offline-safe)
   Vermilion = signal series; black/grey = support. Hairline grid.
   Usage: <div class="chart" data-chart="line" data-rows='[...labels]'
               data-series='[{"name":"Revenue","values":[..],"color":"signal"}]'>
   Also: window.AuroraCharts.line(el|id, opts) etc. for programmatic use.
   ========================================================================== */
(function () {
  const C = {
    signal: '#FF4713', black: '#000000', grey: '#8A8A8A', gold: '#FFCE00',
    muted: '#B8B2A6', strong: '#C93400', ok: '#1E7B4F'
  };
  const grid = '#E7E4DD';
  const NS = 'http://www.w3.org/2000/svg';

  function svgEl(tag, attrs) {
    const e = document.createElementNS(NS, tag);
    for (const k in attrs) e.setAttribute(k, attrs[k]);
    return e;
  }

  function col(name) { return C[name] || name || C.signal; }

  function frame(el, w, h, pad) {
    const svg = svgEl('svg', { viewBox: '0 0 ' + w + ' ' + h, width: '100%', height: 'auto', role: 'img' });
    svg.style.display = 'block';
    return svg;
  }

  function yTicks(svg, min, max, w, h, pad, fmt) {
    const rows = 4;
    for (let i = 0; i <= rows; i++) {
      const v = min + (max - min) * i / rows;
      const y = h - pad.b - (h - pad.t - pad.b) * i / rows;
      svg.appendChild(svgEl('line', { x1: pad.l, x2: w - pad.r, y1: y, y2: y, stroke: grid, 'stroke-width': 1 }));
      const t = svgEl('text', { x: pad.l - 6, y: y + 3.5, 'text-anchor': 'end', 'font-size': 9.5, fill: '#8A8A8A', 'font-family': 'ui-monospace, Menlo, monospace' });
      t.textContent = fmt ? fmt(v) : Math.round(v);
      svg.appendChild(t);
    }
  }

  function fmtK(v) { return v >= 100000 ? (v / 100000).toFixed(1) + 'L' : v >= 1000 ? (v / 1000).toFixed(0) + 'k' : Math.round(v); }

  /* ----- line / area chart ----- */
  function line(target, opt) {
    const el = typeof target === 'string' ? document.getElementById(target) : target;
    const w = opt.w || 560, h = opt.h || 240;
    const pad = { l: 44, r: 12, t: 12, b: 26 };
    const svg = frame(el, w, h);
    const vals = opt.series.flatMap(s => s.values);
    let min = Math.min(0, ...vals), max = Math.max(...vals);
    if (max === min) max = min + 1;
    max *= 1.08;
    const iw = w - pad.l - pad.r, ih = h - pad.t - pad.b;
    yTicks(svg, min, max, w, h, pad, opt.fmt || fmtK);
    const n = opt.labels.length;
    const X = i => pad.l + iw * (n === 1 ? .5 : i / (n - 1));
    const Y = v => h - pad.b - ih * (v - min) / (max - min);
    // x labels
    opt.labels.forEach((lb, i) => {
      const t = svgEl('text', { x: X(i), y: h - 8, 'text-anchor': 'middle', 'font-size': 9.5, fill: '#8A8A8A' });
      t.textContent = lb; svg.appendChild(t);
    });
    opt.series.forEach(s => {
      const c = col(s.color);
      if (s.area) {
        let d = 'M' + X(0) + ' ' + Y(s.values[0]);
        s.values.forEach((v, i) => d += ' L' + X(i) + ' ' + Y(v));
        d += ' L' + X(n - 1) + ' ' + (h - pad.b) + ' L' + X(0) + ' ' + (h - pad.b) + ' Z';
        svg.appendChild(svgEl('path', { d, fill: c, opacity: .09 }));
      }
      let p = '';
      s.values.forEach((v, i) => p += (i ? ' L' : 'M') + X(i) + ' ' + Y(v));
      svg.appendChild(svgEl('path', { d: p, fill: 'none', stroke: c, 'stroke-width': 2.2, 'stroke-linejoin': 'round', 'stroke-linecap': 'round' }));
      s.values.forEach((v, i) => {
        svg.appendChild(svgEl('circle', { cx: X(i), cy: Y(v), r: 3, fill: '#fff', stroke: c, 'stroke-width': 2 }));
      });
    });
    el.innerHTML = ''; el.appendChild(svg);
    return svg;
  }

  /* ----- grouped bars ----- */
  function bars(target, opt) {
    const el = typeof target === 'string' ? document.getElementById(target) : target;
    const w = opt.w || 560, h = opt.h || 240;
    const pad = { l: 44, r: 12, t: 12, b: 26 };
    const svg = frame(el, w, h);
    const vals = opt.series.flatMap(s => s.values);
    let max = Math.max(...vals) * 1.1 || 1;
    const iw = w - pad.l - pad.r, ih = h - pad.t - pad.b;
    yTicks(svg, 0, max, w, h, pad, opt.fmt || fmtK);
    const n = opt.labels.length, gw = iw / n;
    const bw = Math.min(26, (gw - 10) / opt.series.length);
    opt.labels.forEach((lb, i) => {
      const t = svgEl('text', { x: pad.l + gw * i + gw / 2, y: h - 8, 'text-anchor': 'middle', 'font-size': 9.5, fill: '#8A8A8A' });
      t.textContent = lb; svg.appendChild(t);
      opt.series.forEach((s, si) => {
        const v = s.values[i];
        const bh = ih * v / max;
        const x = pad.l + gw * i + gw / 2 - (bw * opt.series.length + 4 * (opt.series.length - 1)) / 2 + si * (bw + 4);
        svg.appendChild(svgEl('rect', { x, y: h - pad.b - bh, width: bw, height: bh, fill: col(s.color), opacity: si === 0 ? 1 : .85 }));
      });
    });
    el.innerHTML = ''; el.appendChild(svg);
    return svg;
  }

  /* ----- horizontal bars (rankings) — simple divs instead of svg ----- */
  function hbars(target, opt) {
    const el = typeof target === 'string' ? document.getElementById(target) : target;
    const max = Math.max(...opt.values) || 1;
    el.innerHTML = opt.labels.map((lb, i) =>
      '<div class="rank"><span class="no">' + String(i + 1).padStart(2, '0') + '</span>' +
      '<span><span>' + lb + '</span><span class="bar-track"><i style="width:' + (opt.values[i] / max * 100) + '%;background:' + (i === 0 ? 'var(--vermilion)' : 'var(--black)') + '"></i></span></span>' +
      '<span class="amt">' + (opt.fmt ? opt.fmt(opt.values[i]) : opt.values[i]) + '</span></div>').join('');
  }

  /* ----- donut ----- */
  function donut(target, opt) {
    const el = typeof target === 'string' ? document.getElementById(target) : target;
    const size = opt.size || 190, r = size / 2 - 14, cx = size / 2, cy = size / 2;
    const svg = frame(el, size, size);
    const total = opt.values.reduce((a, b) => a + b, 0) || 1;
    let a0 = -Math.PI / 2;
    opt.values.forEach((v, i) => {
      const a1 = a0 + Math.PI * 2 * v / total;
      const large = (a1 - a0) > Math.PI ? 1 : 0;
      const x0 = cx + r * Math.cos(a0), y0 = cy + r * Math.sin(a0);
      const x1 = cx + r * Math.cos(a1), y1 = cy + r * Math.sin(a1);
      const d = 'M' + cx + ' ' + cy + ' L' + x0 + ' ' + y0 + ' A' + r + ' ' + r + ' 0 ' + large + ' 1 ' + x1 + ' ' + y1 + ' Z';
      svg.appendChild(svgEl('path', { d, fill: col(opt.colors[i]), opacity: .92 }));
      a0 = a1;
    });
    if (opt.center) {
      const t1 = svgEl('text', { x: cx, y: cy - 2, 'text-anchor': 'middle', 'font-size': 19, 'font-weight': 800, fill: '#000', 'font-family': 'ui-monospace, Menlo, monospace' });
      t1.textContent = opt.center; svg.appendChild(t1);
      if (opt.sub) {
        const t2 = svgEl('text', { x: cx, y: cy + 15, 'text-anchor': 'middle', 'font-size': 9, fill: '#8A8A8A', 'letter-spacing': '.08em' });
        t2.textContent = opt.sub.toUpperCase(); svg.appendChild(t2);
      }
    }
    el.innerHTML = ''; el.appendChild(svg);
    return svg;
  }

  /* ----- sparkline (inside KPI cards) ----- */
  function spark(target, opt) {
    const el = typeof target === 'string' ? document.getElementById(target) : target;
    const w = opt.w || 220, h = opt.h || 44;
    const svg = frame(el, w, h);
    const vs = opt.values, min = Math.min(...vs), max = Math.max(...vs) || min + 1;
    const X = i => w * i / (vs.length - 1), Y = v => h - 4 - (h - 10) * (v - min) / (max - min);
    let d = ''; vs.forEach((v, i) => d += (i ? ' L' : 'M') + X(i) + ' ' + Y(v));
    const c = col(opt.color || 'signal');
    svg.appendChild(svgEl('path', { d: d + ' L' + w + ' ' + h + ' L0 ' + h + ' Z', fill: c, opacity: .08 }));
    svg.appendChild(svgEl('path', { d, fill: 'none', stroke: c, 'stroke-width': 1.8 }));
    el.innerHTML = ''; el.appendChild(svg);
    return svg;
  }

  /* ----- declarative <div data-chart> mounting ----- */
  function mountAll(root) {
    (root || document).querySelectorAll('[data-chart]:not([data-mounted])').forEach(el => {
      el.setAttribute('data-mounted', '1');
      const type = el.getAttribute('data-chart');
      const labels = JSON.parse(el.getAttribute('data-labels') || '[]');
      const series = JSON.parse(el.getAttribute('data-series') || '[]');
      const w = +el.getAttribute('data-w') || 560, h = +el.getAttribute('data-h') || 240;
      if (type === 'line') line(el, { labels, series, w, h, area: true });
      else if (type === 'bars') bars(el, { labels, series, w, h });
      else if (type === 'donut') {
        donut(el, {
          values: series[0].values, colors: series[0].colors || ['signal', 'black', 'grey', 'gold', 'muted'],
          center: el.getAttribute('data-center') || '', sub: el.getAttribute('data-sub') || '', size: h
        });
      } else if (type === 'spark') spark(el, { values: series[0].values, color: series[0].color, w, h: 44 });
    });
  }

  window.AuroraCharts = { line, bars, hbars, donut, spark, mountAll };
  document.addEventListener('DOMContentLoaded', () => mountAll(document));
})();
