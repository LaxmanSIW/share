/* ==========================================================================
   AURORA REDESIGN · Icon library
   Monoline 24×24 stroke glyphs (bbroc.md §6: "simple monoline/solid glyphs,
   24px, colour = current text colour"). Usage: <i data-icon="name"></i>
   Optional size class via data-size="sm|lg".
   ========================================================================== */
(function () {
  const P = {
    dashboard:  '<rect x="3" y="3" width="8" height="10"/><rect x="13" y="3" width="8" height="6"/><rect x="13" y="11" width="8" height="10"/><rect x="3" y="15" width="8" height="6"/>',
    invoices:   '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M8 13h8M8 17h5M8 9h2"/>',
    plus:       '<path d="M12 5v14M5 12h14"/>',
    pluscircle: '<circle cx="12" cy="12" r="9"/><path d="M12 8v8M8 12h8"/>',
    swap:       '<path d="M7 10h13M17 6l4 4-4 4"/><path d="M17 18H4M7 14l-4 4 4 4"/>',
    reports:    '<path d="M21 12A9 9 0 1 1 12 3"/><path d="M12 3a9 9 0 0 1 9 9h-9z"/>',
    purchases:  '<circle cx="9" cy="20" r="1.6"/><circle cx="17" cy="20" r="1.6"/><path d="M2 3h3l2.6 12.4a2 2 0 0 0 2 1.6h8.7a2 2 0 0 0 2-1.6L22 7H6"/>',
    expenses:   '<path d="M20.6 13.4 12 22 2 12V2h10l8.6 8.6a2 2 0 0 1 0 2.8z"/><circle cx="7.5" cy="7.5" r="1.5"/>',
    financials: '<path d="M3 3v18h18"/><path d="M7 15l4-5 3 3 5-7"/>',
    stock:      '<path d="M21 8 12 3 3 8v8l9 5 9-5z"/><path d="M3 8l9 5 9-5M12 13v8"/>',
    trendup:    '<path d="M3 17l6-6 4 4 8-8"/><path d="M15 7h6v6"/>',
    users:      '<circle cx="9" cy="8" r="3.4"/><path d="M2.5 20c.8-3.4 3.4-5 6.5-5s5.7 1.6 6.5 5"/><circle cx="17.5" cy="9" r="2.6"/><path d="M16.4 15.2c2.6.3 4.5 1.8 5.1 4.8"/>',
    truck:      '<path d="M1 5h13v11H1zM14 9h4l3 3v4h-7z"/><circle cx="6" cy="18.5" r="1.8"/><circle cx="17" cy="18.5" r="1.8"/>',
    folder:     '<path d="M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
    templates:  '<rect x="3" y="3" width="18" height="18"/><path d="M3 9h18M9 9v12"/>',
    code:       '<path d="M8 6 2 12l6 6M16 6l6 6-6 6"/>',
    labels:     '<path d="M12 2H3v9l9.6 9.6a2 2 0 0 0 2.8 0l6.2-6.2a2 2 0 0 0 0-2.8z"/><circle cx="7.5" cy="7.5" r="1.4"/>',
    clock:      '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.5 2"/>',
    settings:   '<circle cx="12" cy="12" r="3.2"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.87-.34 1.7 1.7 0 0 0-1 1.55V21a2 2 0 1 1-4 0v-.09a1.7 1.7 0 0 0-1.11-1.55 1.7 1.7 0 0 0-1.87.34l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.7 1.7 0 0 0 .34-1.87 1.7 1.7 0 0 0-1.55-1H3a2 2 0 1 1 0-4h.09A1.7 1.7 0 0 0 4.64 8.9a1.7 1.7 0 0 0-.34-1.87l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.7 1.7 0 0 0 1.87.34H9a1.7 1.7 0 0 0 1-1.55V3a2 2 0 1 1 4 0v.09a1.7 1.7 0 0 0 1 1.55 1.7 1.7 0 0 0 1.87-.34l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.7 1.7 0 0 0-.34 1.87V9a1.7 1.7 0 0 0 1.55 1H21a2 2 0 1 1 0 4h-.09a1.7 1.7 0 0 0-1.51 1z"/>',
    search:     '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.8-4.8"/>',
    help:       '<circle cx="12" cy="12" r="9"/><path d="M9.2 9a2.9 2.9 0 0 1 5.6 1c0 2-3 2.4-3 4"/><path d="M12 17.5h.01"/>',
    refresh:    '<path d="M21 12a9 9 0 1 1-2.6-6.3"/><path d="M21 3v6h-6"/>',
    chevdown:   '<path d="M6 9l6 6 6-6"/>',
    chevright:  '<path d="M9 6l6 6-6 6"/>',
    chevleft:   '<path d="M15 6l-6 6 6 6"/>',
    x:          '<path d="M18 6 6 18M6 6l12 12"/>',
    edit:       '<path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5z"/>',
    trash:      '<path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6M14 11v6"/>',
    copy:       '<rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
    download:   '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/>',
    upload:     '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12"/>',
    eye:        '<path d="M1 12s4-7.5 11-7.5S23 12 23 12s-4 7.5-11 7.5S1 12 1 12z"/><circle cx="12" cy="12" r="3"/>',
    eyeoff:     '<path d="M17.9 17.9A10.4 10.4 0 0 1 12 19.5C5 19.5 1 12 1 12a17.6 17.6 0 0 1 4.2-4.9M9.9 5.2A10 10 0 0 1 12 4.5c7 0 11 7.5 11 7.5a17.8 17.8 0 0 1-2.2 3.2"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/><path d="M2 2l20 20"/>',
    check:      '<path d="M20 6 9 17l-5-5"/>',
    alert:      '<path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/><path d="M12 9v4M12 17h.01"/>',
    info:       '<circle cx="12" cy="12" r="9"/><path d="M12 16v-5M12 8h.01"/>',
    logout:     '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5M21 12H9"/>',
    printer:    '<path d="M6 9V2h12v7"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><rect x="6" y="14" width="12" height="8"/>',
    card:       '<rect x="1" y="4" width="22" height="16" rx="2"/><path d="M1 10h22"/>',
    chat:       '<path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5c-1.6 0-3.1-.4-4.4-1.2L3 20l1.2-5.1A8.5 8.5 0 1 1 21 11.5z"/>',
    send:       '<path d="M22 2 11 13"/><path d="M22 2 15 22l-4-9-9-4z"/>',
    attach:     '<path d="M21.4 11.05 12.25 20.2a6 6 0 0 1-8.5-8.5l9.2-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/>',
    command:    '<path d="M15 6a3 3 0 1 1 3 3h-3zM15 6v12M9 6a3 3 0 1 0-3 3h3zM9 6v12M15 18a3 3 0 1 0 3-3h-3zM15 18H9M9 18a3 3 0 1 1-3-3h3z"/>',
    filter:     '<path d="M22 3H2l8 9.5V19l4 2v-8.5z"/>',
    calendar:   '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>',
    dots:       '<circle cx="12" cy="5" r="1.4"/><circle cx="12" cy="12" r="1.4"/><circle cx="12" cy="19" r="1.4"/>',
    lock:       '<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
    mail:       '<rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-10 6L2 7"/>',
    phone:      '<path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3 19.5 19.5 0 0 1-6-6 19.8 19.8 0 0 1-3-8.7A2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.13 1 .36 1.9.7 2.8a2 2 0 0 1-.45 2.1L8.1 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.45c.9.34 1.8.57 2.8.7a2 2 0 0 1 1.7 2z"/>',
    pin:        '<path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z"/><circle cx="12" cy="10" r="3"/>',
    zap:        '<path d="M13 2 3 14h8l-1 8 11-12h-8z"/>',
    wallet:     '<path d="M20 7H4a2 2 0 0 1-2-2 2 2 0 0 1 2-2h14v4"/><path d="M2 5v14a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2"/><path d="M16 13.5h4"/>',
    home:       '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/>',
    menu:       '<path d="M3 6h18M3 12h18M3 18h18"/>',
    save:       '<path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><path d="M17 21v-8H7v8M7 3v5h8"/>',
    external:   '<path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><path d="M15 3h6v6M10 14 21 3"/>',
    text:       '<path d="M4 7V5h16v2M9 19h6M12 5v14"/>',
    table:      '<rect x="3" y="3" width="18" height="18" rx="1"/><path d="M3 9h18M3 15h18M9 3v18M15 3v18"/>',
    shapes:     '<circle cx="7.5" cy="7.5" r="4"/><rect x="13" y="13" width="8" height="8"/><path d="M7.5 14.5 11 21H4z"/>',
    image:      '<rect x="3" y="3" width="18" height="18" rx="1"/><circle cx="8.8" cy="8.8" r="1.8"/><path d="m21 15-5-5L5 21"/>',
    components: '<path d="M12 2 2 12l10 10 10-10z"/><path d="M12 7v10M7 12h10"/>',
    barcode:    '<path d="M3 5v14M7 5v14M10 5v10M13 5v14M17 5v14M21 5v14M10 18v1M13 18v1"/>',
    qr:         '<rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><path d="M14 14h3v3h-3zM21 14v.01M14 21v.01M17.5 17.5 21 21"/>',
    pen:        '<path d="m12 19 7-7a3.5 3.5 0 0 0-5-5l-7 7-2 7z"/><path d="m18 13-3-3"/>',
    hand:       '<path d="M18 11V6.5a1.5 1.5 0 0 0-3 0V11m0-1V4.5a1.5 1.5 0 0 0-3 0V10m0 .5v-6a1.5 1.5 0 0 0-3 0V12m9-1.5a1.5 1.5 0 0 1 3 .5v3a8 8 0 0 1-8 8h-1a8 8 0 0 1-7-4.2L4.2 15a1.6 1.6 0 0 1 2.7-1.6L9 16V10.5"/>',
    cursor:     '<path d="M4 3l7 17 2.5-6.5L20 11z"/>',
    straight:   '<path d="M4.5 19.5 19.5 4.5"/><circle cx="4.5" cy="19.5" r="1.6"/><circle cx="19.5" cy="4.5" r="1.6"/>',
    page:       '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/>',
    grid:       '<rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/>',
    magnet:     '<path d="M6 15V9a6 6 0 0 1 12 0v6"/><path d="M6 15h4v-2H6zM14 15h4v-2h-4z" transform="translate(0 3)"/><path d="M6 18h4M14 18h4" />',
    undo:       '<path d="M3 7v6h6"/><path d="M21 17a9 9 0 0 0-15-6.7L3 13"/>',
    redo:       '<path d="M21 7v6h-6"/><path d="M3 17a9 9 0 0 1 15-6.7L21 13"/>',
    layers:     '<path d="m12 2 9 5-9 5-9-5z"/><path d="m3 12 9 5 9-5M3 17l9 5 9-5"/>',
    bold:       '<path d="M7 4h7a4 4 0 0 1 0 8H7zM7 12h8a4 4 0 0 1 0 8H7z"/>',
    italic:     '<path d="M19 4h-9M14 20H5M15 4 9 20"/>',
    alignleft:  '<path d="M3 6h18M3 12h12M3 18h15"/>',
    aligncenter:'<path d="M3 6h18M7 12h10M5 18h14"/>',
    alignright: '<path d="M3 6h18M9 12h12M6 18h15"/>',
    bell:       '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>',
    user:       '<circle cx="12" cy="8" r="4"/><path d="M4 21c1-4 4.2-6 8-6s7 2 8 6"/>',
    bank:       '<path d="M3 10h18M12 3 3 8h18zM5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 21h18M3 18h18"/>',
    tag:        '<path d="M12 2H3v9l9.6 9.6a2 2 0 0 0 2.8 0l6.2-6.2a2 2 0 0 0 0-2.8z"/><circle cx="7.5" cy="7.5" r="1.4"/>',
    zoomin:     '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.8-4.8M8 11h6M11 8v6"/>',
    zoomout:    '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.8-4.8M8 11h6"/>',
    fit:        '<path d="M3 8V3h5M21 8V3h-5M3 16v5h5M21 16v5h-5"/>',
    star:       '<path d="m12 2 3.1 6.3 6.9 1-5 4.9 1.2 6.8L12 17.8 5.8 21l1.2-6.8-5-4.9 6.9-1z"/>',
    duplicate:  '<rect x="8" y="8" width="13" height="13" rx="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/>',
    convert:    '<path d="M4 8h13M14 4l4 4-4 4"/><path d="M20 16H7M10 12l-4 4 4 4"/>',
    book:       '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>',
    shield:     '<path d="M12 22s8-3.6 8-10V5l-8-3-8 3v7c0 6.4 8 10 8 10z"/><path d="m9 11.5 2 2 4-4.5"/>',
    server:     '<rect x="2" y="2" width="20" height="8" rx="1.5"/><rect x="2" y="14" width="20" height="8" rx="1.5"/><path d="M6 6h.01M6 18h.01"/>',
    key:        '<circle cx="7.5" cy="15.5" r="4.5"/><path d="m11 12 9-9M17 6l3 3M14 9l2 2"/>',
    database:   '<ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.7-4 3-9 3s-9-1.3-9-3"/><path d="M3 5v14c0 1.7 4 3 9 3s9-1.3 9-3V5"/>',
    files:      '<path d="M15.5 2H8a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2V7z"/><path d="M14 2v5h5"/>',
    google:     '<path d="M21.35 11.1H12v3.2h5.3a4.55 4.55 0 0 1-1.97 2.98v2.48h3.19c1.87-1.72 2.83-4.25 2.83-7.26 0-.49-.04-.97-.1-1.4z" fill="currentColor" stroke="none"/><path d="M12 22c2.7 0 4.96-.9 6.62-2.43l-3.19-2.48c-.89.6-2.03.95-3.43.95a6.02 6.02 0 0 1-5.65-4.14H3.06v2.57A10 10 0 0 0 12 22z" fill="currentColor" stroke="none"/><path d="M6.35 13.9a6 6 0 0 1 0-3.8V7.53H3.06a10 10 0 0 0 0 8.94z" fill="currentColor" stroke="none"/><path d="M12 6a5.4 5.4 0 0 1 3.81 1.49l2.84-2.84A9.6 9.6 0 0 0 12 2 10 10 0 0 0 3.06 7.53l3.29 2.57A6.02 6.02 0 0 1 12 6z" fill="currentColor" stroke="none"/>'
  };

  window.AuroraIcons = P;

  function inject(root) {
    (root || document).querySelectorAll('i[data-icon]:not([data-done])').forEach(el => {
      const name = el.getAttribute('data-icon');
      const path = P[name];
      const size = el.getAttribute('data-size');
      if (path) {
        el.innerHTML = '<svg class="ic' + (size ? ' ' + size : '') + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + path + '</svg>';
        if (size) el.setAttribute('data-size', size);
      } else {
        el.innerHTML = '<svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="12" cy="12" r="9"/></svg>';
      }
      el.setAttribute('data-done', '1');
      el.style.display = 'inline-flex';
      el.style.lineHeight = '0';
    });
  }

  window.icon = function (name, cls) {
    return '<i data-icon="' + name + '"' + (cls ? ' data-size="' + cls + '"' : '') + '></i>';
  };

  window.AuroraInjectIcons = inject;
  document.addEventListener('DOMContentLoaded', () => inject(document));
})();
