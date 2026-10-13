/*
 * TOGG configurator botu.
 * Sayfadaki elemanlari METIN ile bulur (CSS sinifina bagli degil), boylece
 * site tasarimi degisse bile anahtar kelimeler uyuyorsa calismaya devam eder.
 *
 * Kotlin tarafi: TOGGBOT.run(cfg) cagirir, sonuc ToggBridge.report(json) ile doner.
 * Asla "siparis / odeme / satin al" gibi butonlara tiklamaz.
 */
(function () {
  if (window.TOGGBOT && window.TOGGBOT.__v === 3) return;

  var STATE_KEY = 'toggbot_state_v3';

  function fold(s) {
    return (s || '')
      .replace(/İ/g, 'i').replace(/I/g, 'ı')
      .toLowerCase()
      .replace(/ı/g, 'i').replace(/ş/g, 's').replace(/ğ/g, 'g')
      .replace(/ü/g, 'u').replace(/ö/g, 'o').replace(/ç/g, 'c')
      .replace(/[âà]/g, 'a').replace(/î/g, 'i').replace(/û/g, 'u')
      .replace(/ /g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  // "v2, rwd, uzun menzil/long range"  ->  [["v2"],["rwd"],["uzun menzil","long range"]]
  function parseKeywords(str) {
    return (str || '').split(',').map(function (part) {
      return part.split('/').map(fold).filter(Boolean);
    }).filter(function (g) { return g.length; });
  }

  function textMatches(ftext, groups) {
    if (!groups.length) return false;
    for (var i = 0; i < groups.length; i++) {
      var ok = false;
      for (var j = 0; j < groups[i].length; j++) {
        if (ftext.indexOf(groups[i][j]) !== -1) { ok = true; break; }
      }
      if (!ok) return false;
    }
    return true;
  }

  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }

  function isVisible(el) {
    if (!el || !el.getBoundingClientRect) return false;
    var st = window.getComputedStyle(el);
    if (st.display === 'none' || st.visibility === 'hidden' || parseFloat(st.opacity) === 0) return false;
    var r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  }

  var SKIP_TAGS = { SCRIPT: 1, STYLE: 1, NOSCRIPT: 1, TEMPLATE: 1, HEAD: 1, META: 1, LINK: 1, PATH: 1, SVG: 1, IMG: 1, VIDEO: 1, SOURCE: 1, IFRAME: 1 };

  function allElements() {
    var out = [];
    var list = document.body ? document.body.getElementsByTagName('*') : [];
    for (var i = 0; i < list.length; i++) {
      if (!SKIP_TAGS[list[i].tagName.toUpperCase()]) out.push(list[i]);
    }
    return out;
  }

  // Butun anahtar kelimeleri iceren EN DERIN gorunur elemanlar
  function findDeepestMatches(groups, maxLen) {
    maxLen = maxLen || 400;
    var els = allElements();
    var matches = [];
    for (var i = 0; i < els.length; i++) {
      var t = els[i].textContent;
      if (!t || t.length > maxLen * 4) continue;
      var ft = fold(t);
      if (ft.length > maxLen) continue;
      if (textMatches(ft, groups)) matches.push(els[i]);
    }
    // icinde baska eslesme barindiranlari ele (en derin kalsin)
    var deepest = matches.filter(function (el) {
      for (var k = 0; k < matches.length; k++) {
        if (matches[k] !== el && el.contains(matches[k])) return false;
      }
      return true;
    });
    return deepest.filter(isVisible).sort(function (a, b) {
      return fold(a.textContent).length - fold(b.textContent).length;
    });
  }

  var CLICK_ROLES = { button: 1, radio: 1, checkbox: 1, option: 1, tab: 1, 'switch': 1, menuitem: 1, link: 1, menuitemradio: 1, menuitemcheckbox: 1 };

  function isClickable(el) {
    if (!el || el.nodeType !== 1) return false;
    var tag = el.tagName.toUpperCase();
    if (tag === 'BUTTON' || tag === 'A' || tag === 'LABEL' || tag === 'INPUT' || tag === 'SELECT' || tag === 'OPTION') return true;
    var role = (el.getAttribute('role') || '').toLowerCase();
    if (CLICK_ROLES[role]) return true;
    if (typeof el.onclick === 'function' || el.hasAttribute('onclick')) return true;
    if (el.hasAttribute('tabindex') && el.getAttribute('tabindex') !== '-1') return true;
    try { if (window.getComputedStyle(el).cursor === 'pointer') return true; } catch (e) {}
    return false;
  }

  function clickableAncestor(el) {
    var cur = el;
    for (var i = 0; i < 8 && cur && cur !== document.body; i++) {
      if (isClickable(cur)) {
        // cursor:pointer miras alinir; en ust pointer'a cik
        var up = cur;
        while (up.parentElement && up.parentElement !== document.body &&
               !isStrongClickable(up) && isClickable(up.parentElement) &&
               fold(up.parentElement.textContent).length < 600) {
          up = up.parentElement;
          if (isStrongClickable(up)) break;
        }
        return up;
      }
      cur = cur.parentElement;
    }
    return el;
  }

  function isStrongClickable(el) {
    var tag = el.tagName.toUpperCase();
    if (tag === 'BUTTON' || tag === 'A' || tag === 'LABEL' || tag === 'INPUT') return true;
    var role = (el.getAttribute('role') || '').toLowerCase();
    return !!CLICK_ROLES[role] || el.hasAttribute('onclick');
  }

  var DISABLED_CLASS = /(^|[\s_-])(disabled|is-disabled|unavailable|not-available|sold-?out|out-of-stock|passive|inactive)([\s_-]|$)/i;
  var DISABLED_TEXT = ['tukendi', 'stokta yok', 'satista degil', 'mevcut degil', 'yakinda', 'satisa kapali', 'secilemez', 'temin edilemiyor', 'sold out', 'not available'];

  function disabledReason(el) {
    var cur = el;
    for (var i = 0; i < 4 && cur && cur !== document.body; i++) {
      if (cur.disabled === true) return 'disabled';
      if ((cur.getAttribute('aria-disabled') || '') === 'true') return 'aria-disabled';
      var cls = typeof cur.className === 'string' ? cur.className : (cur.getAttribute('class') || '');
      if (DISABLED_CLASS.test(cls)) return 'class:' + cls.slice(0, 60);
      cur = cur.parentElement;
    }
    var inp = el.querySelector && el.querySelector('input,button');
    if (inp && inp.disabled) return 'input-disabled';
    var ft = fold(el.textContent);
    for (var k = 0; k < DISABLED_TEXT.length; k++) {
      if (ft.indexOf(DISABLED_TEXT[k]) !== -1) return 'text:' + DISABLED_TEXT[k];
    }
    return null;
  }

  // Sadece kesin sinyaller: aria-* = true veya input.checked
  function isSelected(el) {
    var nodes = [el];
    if (el.parentElement) nodes.push(el.parentElement);
    var inner = el.querySelectorAll ? el.querySelectorAll('input[type=radio],input[type=checkbox],[aria-checked],[aria-pressed],[aria-selected]') : [];
    for (var i = 0; i < inner.length && i < 4; i++) nodes.push(inner[i]);
    for (var j = 0; j < nodes.length; j++) {
      var n = nodes[j];
      if (n.tagName && n.tagName.toUpperCase() === 'INPUT' && (n.type === 'radio' || n.type === 'checkbox') && n.checked) return true;
      if (n.getAttribute && (n.getAttribute('aria-checked') === 'true' || n.getAttribute('aria-pressed') === 'true' || n.getAttribute('aria-selected') === 'true')) return true;
    }
    return false;
  }

  function fireClick(el) {
    try { el.scrollIntoView({ block: 'center' }); } catch (e) {}
    var r = el.getBoundingClientRect();
    var opts = { bubbles: true, cancelable: true, view: window, clientX: r.left + r.width / 2, clientY: r.top + r.height / 2, button: 0 };
    try { el.dispatchEvent(new PointerEvent('pointerdown', opts)); } catch (e) {}
    el.dispatchEvent(new MouseEvent('mousedown', opts));
    try { el.dispatchEvent(new PointerEvent('pointerup', opts)); } catch (e) {}
    el.dispatchEvent(new MouseEvent('mouseup', opts));
    if (typeof el.click === 'function') el.click();
    else el.dispatchEvent(new MouseEvent('click', opts));
  }

  // ---------- Fiyat ----------
  var PRICE_RE = /(₺\s*\d{1,3}(?:[.\s]\d{3})+(?:,\d{1,2})?|\d{1,3}(?:[.\s]\d{3})+(?:,\d{1,2})?\s*(?:TL|₺|TRY))/gi;

  function parsePrices(text) {
    var out = [];
    var m;
    PRICE_RE.lastIndex = 0;
    while ((m = PRICE_RE.exec(text || '')) !== null) {
      var s = m[0].replace(/₺|TL|TRY/gi, '').trim();
      s = s.split(',')[0].replace(/[.\s]/g, '');
      var v = parseInt(s, 10);
      if (v >= 300000 && v <= 20000000) out.push(v);
    }
    return out;
  }

  function hasFixedAncestor(el) {
    var cur = el;
    for (var i = 0; i < 10 && cur && cur !== document.body; i++) {
      var p = window.getComputedStyle(cur).position;
      if (p === 'fixed' || p === 'sticky') return true;
      cur = cur.parentElement;
    }
    return false;
  }

  function readTotal() {
    var els = allElements();
    var cands = [];
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      var t = el.textContent || '';
      if (t.length > 120) continue;
      var ps = parsePrices(t);
      if (ps.length !== 1) continue;
      // en derin: cocuklarindan biri ayni fiyati tasiyorsa atla
      var childHas = false;
      for (var c = 0; c < el.children.length; c++) {
        if (parsePrices(el.children[c].textContent).length) { childHas = true; break; }
      }
      if (childHas || !isVisible(el)) continue;
      var ctx = fold(t);
      var p1 = el.parentElement, p2 = p1 && p1.parentElement;
      if (p1 && p1.textContent.length < 300) ctx += ' ' + fold(p1.textContent);
      if (p2 && p2.textContent.length < 300) ctx += ' ' + fold(p2.textContent);
      var score = 0;
      if (ctx.indexOf('toplam') !== -1) score += 4;
      if (ctx.indexOf('anahtar teslim') !== -1) score += 2;
      if (ctx.indexOf('fiyat') !== -1) score += 1;
      if (hasFixedAncestor(el)) score += 1;
      if (/indirim|kampanya|aylik|taksit|pesinat|kredi/.test(ctx)) score -= 3;
      cands.push({ v: ps[0], score: score, text: ctx.slice(0, 120) });
    }
    if (!cands.length) return null;
    cands.sort(function (a, b) { return (b.score - a.score) || (b.v - a.v); });
    return cands[0];
  }

  function priceInside(el) {
    var cur = el;
    for (var i = 0; i < 4 && cur && cur !== document.body; i++) {
      var ps = parsePrices(cur.textContent);
      var uniq = ps.filter(function (v, k) { return ps.indexOf(v) === k; });
      if (uniq.length === 1) return uniq[0];
      if (uniq.length > 1) return null;
      cur = cur.parentElement;
    }
    return null;
  }

  // ---------- Hata / dialog ----------
  var ERROR_WORDS = ['hata', 'uyumsuz', 'secilemez', 'gecersiz', 'bir sorun', 'kullanilamaz', 'birlikte secilemez', 'error', 'basarisiz'];

  function findErrors() {
    var sel = '[role=dialog],[role=alertdialog],[role=alert],.modal,.toast,.snackbar,[class*="error"],[class*="Error"],[class*="alert"],[class*="Alert"],[class*="notification"]';
    var found = [];
    var list = document.querySelectorAll(sel);
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      if (!isVisible(el)) continue;
      var ft = fold(el.textContent);
      if (!ft || ft.length > 600) continue;
      for (var k = 0; k < ERROR_WORDS.length; k++) {
        if (ft.indexOf(ERROR_WORDS[k]) !== -1) { found.push(ft.slice(0, 200)); break; }
      }
    }
    return found.filter(function (v, k) { return found.indexOf(v) === k; });
  }

  // ---------- Buton bulma ----------
  var FORBIDDEN = ['siparis', 'odeme', 'satin al', 'rezervasyon', 'on talep', 'giris yap', 'uye ol', 'kayit ol', 'onayla ve', 'kapora', 'sepete', 'teklif al', 'test surusu', 'paylas', 'indir', 'pdf', 'geri', 'onceki'];

  function isForbidden(ft) {
    for (var i = 0; i < FORBIDDEN.length; i++) if (ft.indexOf(FORBIDDEN[i]) !== -1) return true;
    return false;
  }

  function findButtonByWords(words, maxLen) {
    maxLen = maxLen || 40;
    var list = document.querySelectorAll('button,a,[role=button],input[type=button],input[type=submit],div,span');
    var best = null;
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      var ft = fold(el.textContent || el.value || el.getAttribute('aria-label') || '');
      if (!ft || ft.length > maxLen) continue;
      var hit = false;
      for (var k = 0; k < words.length; k++) {
        if (ft === words[k] || ft.indexOf(words[k] + ' ') === 0 || ft === words[k] + ' >' || ft.indexOf(words[k]) === 0) { hit = true; break; }
      }
      if (!hit || isForbidden(ft)) continue;
      var target = isClickable(el) ? el : clickableAncestor(el);
      if (!isVisible(target) || disabledReason(target)) continue;
      if (!best || isStrongClickable(target) && !isStrongClickable(best)) best = target;
      if (isStrongClickable(best)) break;
    }
    return best;
  }

  function pageSig() {
    var t = document.body ? document.body.innerText || '' : '';
    return location.href + '|' + t.length + '|' + t.slice(0, 300);
  }

  // ---------- State ----------
  function loadState() {
    try { return JSON.parse(sessionStorage.getItem(STATE_KEY) || 'null'); } catch (e) { return null; }
  }
  function saveState(st) {
    try { sessionStorage.setItem(STATE_KEY, JSON.stringify(st)); } catch (e) {}
  }

  function bridgeLog(msg) {
    try { if (window.ToggBridge && window.ToggBridge.log) window.ToggBridge.log(String(msg)); } catch (e) {}
  }

  async function waitForContent(timeoutMs) {
    var t0 = Date.now();
    while (Date.now() - t0 < timeoutMs) {
      var txt = document.body ? (document.body.innerText || '') : '';
      if ((txt.length > 40 && /t10|versiyon|model/i.test(txt)) || txt.length > 800) return true;
      await sleep(500);
    }
    return false;
  }

  async function acceptCookies() {
    var btn = findButtonByWords(['tumunu kabul et', 'tumunu kabul', 'kabul et', 'kabul ediyorum', 'cerezleri kabul', 'accept all', 'accept', 'tamam'], 40);
    if (btn) { fireClick(btn); await sleep(600); return true; }
    return false;
  }

  async function run(cfg) {
    var st = loadState();
    if (!st || st.runId !== cfg.runId) {
      st = { runId: cfg.runId, done: {}, blocked: {}, log: [], errors: [], prices: {}, steps: 0, stalls: 0, tabsTried: {} };
    }
    var t0 = Date.now();
    var deadline = t0 + (cfg.timeoutMs || 60000);
    function L(m) { st.log.push(((Date.now() - t0) / 1000).toFixed(1) + 's ' + m); bridgeLog(m); saveState(st); }

    var targets = cfg.targets.map(function (t) {
      return { id: t.id, label: t.label, kind: t.kind, toggle: !!t.toggle, optional: !!t.optional, groups: parseKeywords(t.keywords) };
    }).filter(function (t) { return t.groups.length; });

    var ok = await waitForContent(Math.max(5000, Math.min(25000, deadline - Date.now())));
    if (!ok) L('Sayfa icerigi gec yuklendi / bos');
    await acceptCookies();

    var stepDelay = cfg.stepDelayMs || 1500;
    var nextWords = parseKeywords(cfg.nextWords || 'devam/ileri/sonraki/next/continue')[0] || ['devam'];
    var tabGroups = (cfg.tabWords || '').split(',').map(fold).filter(Boolean);

    function variantDone() { return !!st.done.variant; }

    while (Date.now() < deadline && st.steps < (cfg.maxSteps || 40)) {
      st.steps++;
      var errs = findErrors();
      errs.forEach(function (e) { if (st.errors.indexOf(e) === -1) { st.errors.push(e); L('HATA/uyari: ' + e); } });

      var clickedSomething = false;
      for (var i = 0; i < targets.length; i++) {
        var tg = targets[i];
        if (st.done[tg.id] || st.blocked[tg.id]) continue;
        if (!cfg.fullMode && tg.kind === 'extra') continue;
        if (tg.kind === 'extra' && !variantDone()) continue;
        var ms = findDeepestMatches(tg.groups, tg.kind === 'variant' ? 300 : 200);
        if (!ms.length) continue;
        var el = ms[0];
        var clickEl = clickableAncestor(el);
        var dis = disabledReason(clickEl);
        if (dis) {
          // ayni metinli baska (aktif) bir aday var mi?
          var alt = null;
          for (var a = 1; a < ms.length; a++) {
            var ce = clickableAncestor(ms[a]);
            if (!disabledReason(ce)) { alt = ms[a]; clickEl = ce; break; }
          }
          if (!alt) {
            st.blocked[tg.id] = dis;
            L(tg.label + ' bulundu ama SECILEMIYOR (' + dis + ')');
            continue;
          }
          el = alt;
        }
        var before = readTotal();
        if (isSelected(clickEl)) {
          L(tg.label + ' zaten secili');
        } else {
          L(tg.label + ' tiklaniyor: "' + fold(el.textContent).slice(0, 80) + '"');
          fireClick(clickEl);
          await sleep(stepDelay);
          // kutu/radio icerde secilmediyse inputa tikla
          var inp = clickEl.querySelector && clickEl.querySelector('input[type=checkbox],input[type=radio]');
          if (inp && !inp.checked) { fireClick(inp); await sleep(stepDelay / 2); }
        }
        var after = readTotal();
        if (tg.toggle && before && after && after.v < before.v) {
          // fiyat dustu -> yanlislikla kaldirdik, geri sec
          L(tg.label + ' fiyat dustu (' + before.v + ' -> ' + after.v + '), tekrar tiklaniyor');
          fireClick(clickEl);
          await sleep(stepDelay);
          after = readTotal();
        }
        if (tg.kind === 'variant') {
          var cardPrice = priceInside(el) || priceInside(clickEl);
          st.prices.variantCard = cardPrice;
          st.prices.afterVariant = after ? after.v : null;
          L('Versiyon fiyati: kart=' + cardPrice + ' toplam=' + (after ? after.v + ' [' + after.text + ']' : '-'));
        }
        st.done[tg.id] = true;
        st.prices[tg.id] = after ? after.v : null;
        clickedSomething = true;
        saveState(st);
        break; // sayfa degismis olabilir, bastan tara
      }
      if (clickedSomething) continue;

      // Basit modda versiyonu sectiysek isimiz bitti
      if (variantDone() && (!cfg.fullMode)) break;
      if (st.blocked.variant) break;

      var pending = targets.filter(function (t) {
        return !st.done[t.id] && !st.blocked[t.id] && (cfg.fullMode || t.kind !== 'extra') && !t.optional;
      });
      if (!pending.length) { L('Tum hedefler secildi'); break; }

      // Sonraki adima gec
      var sigBefore = pageSig();
      var nb = findButtonByWords(nextWords, 40);
      if (nb) {
        L('Ileri: "' + fold(nb.textContent || nb.value).slice(0, 40) + '"');
        fireClick(nb);
        await sleep(stepDelay + 500);
      } else {
        // sekme / adim basliklarini dene
        var tabClicked = false;
        for (var tgi = 0; tgi < tabGroups.length; tgi++) {
          var w = tabGroups[tgi];
          if (st.tabsTried[w]) continue;
          st.tabsTried[w] = true;
          var tb = findButtonByWords([w], 30);
          if (tb) { L('Sekme: "' + w + '"'); fireClick(tb); tabClicked = true; await sleep(stepDelay + 500); break; }
        }
        if (!tabClicked) { L('Ileri butonu / sekme bulunamadi'); await sleep(1500); st.stalls++; }
      }
      if (pageSig() === sigBefore) st.stalls++; else st.stalls = 0;
      if (st.stalls >= 4) { L('Sayfa ilerlemiyor, duruluyor'); break; }
      saveState(st);
    }

    var total = readTotal();
    var missing = targets.filter(function (t) {
      return !t.optional && (cfg.fullMode || t.kind !== 'extra') && !st.done[t.id];
    }).map(function (t) { return t.label + (st.blocked[t.id] ? ' (secilemez)' : ' (bulunamadi)'); });
    var result = {
      runId: cfg.runId,
      url: location.href,
      variantFound: !!st.done.variant || !!st.blocked.variant,
      variantSelectable: !!st.done.variant && !st.blocked.variant,
      variantPrice: st.prices.variantCard || st.prices.afterVariant || null,
      finalPrice: total ? total.v : null,
      finalPriceText: total ? total.text : null,
      done: Object.keys(st.done),
      missing: missing,
      errors: st.errors,
      steps: st.steps,
      log: st.log,
      allPrices: parsePrices(document.body ? document.body.innerText : '').slice(0, 30),
      pageText: cfg.debug ? (document.body ? document.body.innerText.slice(0, 30000) : '') : ''
    };
    result.fullOk = cfg.fullMode && result.variantSelectable && missing.length === 0 && st.errors.length === 0;
    try { sessionStorage.removeItem(STATE_KEY); } catch (e) {}
    return result;
  }

  window.TOGGBOT = {
    __v: 3,
    running: false,
    start: function (cfg) {
      if (window.TOGGBOT.running) return 'busy';
      window.TOGGBOT.running = true;
      run(cfg).then(function (res) {
        window.TOGGBOT.running = false;
        var s = JSON.stringify(res);
        if (window.ToggBridge) window.ToggBridge.report(s); else console.log('TOGGBOT_RESULT ' + s);
      }).catch(function (e) {
        window.TOGGBOT.running = false;
        var s = JSON.stringify({ runId: cfg.runId, fatal: String(e && e.stack || e) });
        if (window.ToggBridge) window.ToggBridge.report(s); else console.log('TOGGBOT_RESULT ' + s);
      });
      return 'started';
    },
    _fold: fold,
    _parsePrices: parsePrices,
    _readTotal: readTotal
  };
})();
