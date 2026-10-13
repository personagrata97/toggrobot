// Sitenin "mobil uygulamaya yonlendirme" ekranini neyin tetikledigini bulur.
// Farkli kombinasyonlarla acar ve engel ekraninin cikip cikmadigini kaydeder.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const OUT = process.argv[2] || 'dump';
const URL = 'https://configurator.togg.com.tr/';
fs.mkdirSync(OUT, { recursive: true });

const DESK = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36';
const MOB = 'Mozilla/5.0 (Linux; Android 13; SM-G781B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36';

const cases = [
  { name: 'A-masaustu-genis', userAgent: DESK, viewport: { width: 1366, height: 900 } },
  { name: 'B-masaustuUA-dar-ekran', userAgent: DESK, viewport: { width: 412, height: 915 } },
  { name: 'C-masaustuUA-genis-dokunmatik', userAgent: DESK, viewport: { width: 1366, height: 900 }, hasTouch: true },
  { name: 'D-mobilUA-genis', userAgent: MOB, viewport: { width: 1366, height: 900 } },
  { name: 'E-masaustuUA-dar-dokunmatik-mobil', userAgent: DESK, viewport: { width: 412, height: 915 }, hasTouch: true, isMobile: true, deviceScaleFactor: 2.6 },
  { name: 'F-1024-genislik', userAgent: DESK, viewport: { width: 1024, height: 900 } },
  { name: 'G-800-genislik', userAgent: DESK, viewport: { width: 800, height: 900 } },
];

(async () => {
  const browser = await chromium.launch();
  const results = [];
  for (const c of cases) {
    const { name, ...opts } = c;
    const ctx = await browser.newContext({ locale: 'tr-TR', timezoneId: 'Europe/Istanbul', ...opts });
    const page = await ctx.newPage();
    let blocked = null, err = null;
    try {
      await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await page.waitForTimeout(9000);
      const txt = await page.evaluate(() => document.body.innerText);
      blocked = /uygulamamızdan|dijital mağazalardan/i.test(txt);
      await page.screenshot({ path: path.join(OUT, name + '.png') });
    } catch (e) { err = String(e); }
    results.push({ name, blocked, err });
    await ctx.close();
  }
  fs.writeFileSync(path.join(OUT, 'results.json'), JSON.stringify(results, null, 1));
  console.log(JSON.stringify(results, null, 1));

  // Uygulama paketini indir ve mobil algilama kodunu bul
  const ctx = await browser.newContext({ userAgent: DESK });
  const page = await ctx.newPage();
  await page.goto(URL, { waitUntil: 'domcontentloaded' });
  const srcs = await page.evaluate(() => [...document.querySelectorAll('script[src]')].map(s => s.src).filter(s => s.includes('configurator.togg.com.tr')));
  const out = [];
  for (const s of srcs) {
    const js = await (await ctx.request.get(s)).text();
    fs.writeFileSync(path.join(OUT, 'bundle-' + path.basename(s)), js);
    const re = /(uygulamamızdan|innerWidth|matchMedia|maxTouchPoints|ontouchstart|userAgentData|navigator\.userAgent|isMobile|android|iphone|screen\.width|pointer:\s*coarse|hover:\s*none|max-width)/gi;
    let m;
    while ((m = re.exec(js)) !== null) {
      out.push(`${path.basename(s)} @${m.index} [${m[0]}]: ` + js.slice(Math.max(0, m.index - 220), m.index + 220).replace(/\n/g, ' '));
      if (out.length > 300) break;
    }
  }
  fs.writeFileSync(path.join(OUT, 'detection-snippets.txt'), out.join('\n\n'));
  await browser.close();
})();
