// Siteyi gercek Chrome ile acar; ekran goruntusu, metin, DOM ve ag isteklerini kaydeder.
// Ardindan bot.js'i ayni sayfada calistirip sonucunu kaydeder.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const OUT = process.argv[2] || 'dump';
const URL = process.env.TOGG_URL || 'https://configurator.togg.com.tr/';
fs.mkdirSync(OUT, { recursive: true });

async function session(name, contextOpts, fn) {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ locale: 'tr-TR', timezoneId: 'Europe/Istanbul', ...contextOpts });
  const page = await ctx.newPage();
  const net = [];
  page.on('response', async (r) => {
    const req = r.request();
    const type = req.resourceType();
    if (!['xhr', 'fetch', 'document'].includes(type)) return;
    const e = { url: r.url(), method: req.method(), status: r.status(), type, ct: r.headers()['content-type'] || '' };
    if (req.method() !== 'GET') e.postData = (req.postData() || '').slice(0, 3000);
    try {
      if (/json|text\/plain|graphql/.test(e.ct)) {
        const body = await r.text();
        e.size = body.length;
        const f = `${name}-net-${net.length}.txt`;
        fs.writeFileSync(path.join(OUT, f), body.slice(0, 400000));
        e.file = f;
      }
    } catch (err) { e.err = String(err); }
    net.push(e);
  });
  page.on('dialog', d => d.dismiss());
  const log = [];
  try {
    await fn(page, log);
  } catch (err) {
    log.push('HATA: ' + (err.stack || err));
  }
  fs.writeFileSync(path.join(OUT, `${name}-network.json`), JSON.stringify(net, null, 1));
  fs.writeFileSync(path.join(OUT, `${name}-log.txt`), log.join('\n'));
  await browser.close();
}

async function snap(page, name, step) {
  const base = path.join(OUT, `${name}-${step}`);
  await page.screenshot({ path: base + '.png', fullPage: true }).catch(() => {});
  fs.writeFileSync(base + '.txt', await page.evaluate(() => document.body ? document.body.innerText : '').catch(e => String(e)));
  fs.writeFileSync(base + '.html', await page.content().catch(e => String(e)));
}

(async () => {
  // 1) Masaustu: sadece ac ve kaydet
  await session('desktop', { viewport: { width: 1440, height: 900 } }, async (page, log) => {
    const resp = await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60000 });
    log.push('status ' + (resp && resp.status()) + ' url ' + page.url());
    await page.waitForTimeout(12000);
    await snap(page, 'desktop', '0-acilis');
  });

  // 2) Mobil (telefondaki WebView gibi): bot.js'i calistir
  const bot = fs.readFileSync(path.join(__dirname, '..', 'app/src/main/assets/bot.js'), 'utf8');
  const cfg = JSON.parse(fs.readFileSync(path.join(__dirname, 'botcfg.json'), 'utf8'));
  await session('mobile', {
    viewport: { width: 412, height: 915 }, deviceScaleFactor: 2.6, isMobile: true, hasTouch: true,
    userAgent: 'Mozilla/5.0 (Linux; Android 13; SM-G781B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36',
  }, async (page, log) => {
    await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60000 });
    await page.waitForTimeout(3000);
    await snap(page, 'mobile', '0-acilis');
    await page.evaluate(bot);
    await page.exposeFunction('__botLog', m => log.push(m));
    const res = await page.evaluate(c => new Promise(r => {
      window.ToggBridge = { log: m => window.__botLog(m), report: s => r(JSON.parse(s)) };
      TOGGBOT.start(c);
    }), { ...cfg, runId: 'gh', debug: true });
    const pageText = res.pageText; delete res.pageText;
    fs.writeFileSync(path.join(OUT, 'mobile-bot-result.json'), JSON.stringify(res, null, 1));
    fs.writeFileSync(path.join(OUT, 'mobile-bot-pagetext.txt'), pageText || '');
    await snap(page, 'mobile', '9-bot-sonu');
  });
})();
