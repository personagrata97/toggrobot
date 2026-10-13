// Gercek siteyi acar, T10F sekmesine tiklar; togg API cagrilarini (istek basliklariyla) kaydeder.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const OUT = process.argv[2] || 'dump';
const URL = 'https://configurator.togg.com.tr/';
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ locale: 'tr-TR', timezoneId: 'Europe/Istanbul', viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  const api = [];
  page.on('response', async (r) => {
    const req = r.request();
    if (!/togg\.cloud|togg\.com\.tr\/api/.test(r.url())) return;
    const e = { url: r.url(), method: req.method(), status: r.status(), reqHeaders: await req.allHeaders(), postData: req.postData() };
    try { const b = await r.text(); e.file = `api-${api.length}.json`; fs.writeFileSync(path.join(OUT, e.file), b); } catch (err) {}
    api.push(e);
  });
  const log = [];
  const snap = async (n) => {
    await page.screenshot({ path: path.join(OUT, n + '.png'), fullPage: true }).catch(() => {});
    fs.writeFileSync(path.join(OUT, n + '.txt'), await page.evaluate(() => document.body.innerText).catch(String));
  };
  try {
    await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60000 });
    await page.waitForTimeout(8000);
    // cerez
    for (const t of ['Tümüne İzin Ver', 'Tümünü Kabul Et', 'Kabul Et', 'İzin ver']) {
      const b = page.getByRole('button', { name: t });
      if (await b.count()) { await b.first().click().catch(() => {}); log.push('cerez: ' + t); break; }
    }
    await snap('1-acilis');
    const t10f = page.getByText('T10F', { exact: true });
    log.push('T10F eleman sayisi: ' + await t10f.count());
    if (await t10f.count()) {
      const el = t10f.first();
      log.push('T10F html: ' + (await el.evaluate(e => e.outerHTML.slice(0, 400) + ' || parent: ' + e.parentElement.outerHTML.slice(0, 600))));
      await el.click();
      await page.waitForTimeout(8000);
      log.push('url sonra: ' + page.url());
      await snap('2-t10f');
    }
    fs.writeFileSync(path.join(OUT, '2-t10f.html'), await page.content());
  } catch (err) { log.push('HATA ' + (err.stack || err)); }
  fs.writeFileSync(path.join(OUT, 'api.json'), JSON.stringify(api, null, 1));
  fs.writeFileSync(path.join(OUT, 'log.txt'), log.join('\n'));
  await browser.close();
})();
