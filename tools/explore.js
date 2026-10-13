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

  // Uygulamadaki duzeltme: telefon boyutunda (412 px, mobil) ama viewport 1100'e sabitlenmis
  const fixJs = fs.readFileSync(path.join(__dirname, 'viewport-fix.js'), 'utf8');
  for (const variant of ['fix-yok', 'fix-var']) {
    const ctx = await browser.newContext({ locale: 'tr-TR', userAgent: DESK, viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2.6 });
    if (variant === 'fix-var') await ctx.addInitScript(fixJs);
    const page = await ctx.newPage();
    await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60000 });
    await page.waitForTimeout(10000);
    const info = await page.evaluate(() => ({ text: document.body.innerText.slice(0, 300), w: window.innerWidth }));
    const blocked = /uygulamamızdan|dijital mağazalardan/i.test(info.text);
    await page.screenshot({ path: path.join(OUT, 'telefon-' + variant + '.png') });
    results.push({ name: 'telefon-412-' + variant, blocked, innerWidth: info.w, text: info.text.slice(0, 120) });
    await ctx.close();
  }
  fs.writeFileSync(path.join(OUT, 'results.json'), JSON.stringify(results, null, 1));
  console.log(JSON.stringify(results.slice(-2), null, 1));
  await browser.close();
})();
