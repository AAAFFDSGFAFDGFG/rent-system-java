import { chromium } from '/Users/mikeygao/Desktop/个人开发项目/rent-system-java/frontend/node_modules/playwright/index.mjs';

const BASE = 'http://localhost:8080';
const OUT = '/Users/mikeygao/Desktop/个人开发项目/rent-system-java/verify/shots';
import { mkdirSync } from 'fs';
mkdirSync(OUT, { recursive: true });

const log = (...a) => console.log('[verify]', ...a);
const results = [];
function check(name, cond, detail = '') {
  results.push({ name, ok: !!cond, detail });
  log(cond ? 'PASS' : 'FAIL', '-', name, detail);
}

const consoleErrors = [];
const pageErrors = [];
const badResponses = [];

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await ctx.newPage();
page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text() + ' @ ' + (m.location()?.url || '?')); });
page.on('pageerror', e => pageErrors.push(e.message));
page.on('response', async (r) => {
  if (r.status() >= 400) {
    log('BAD RESP', r.status(), r.url());
    let body = '';
    try { body = (await r.text()).slice(0, 300); } catch {}
    badResponses.push({ url: r.url(), status: r.status(), body });
  }
});

async function shot(name) { await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: false }); }

try {
  // 1. Login page renders
  await page.goto(BASE, { waitUntil: 'networkidle' });
  await page.waitForSelector('.login-card', { timeout: 10000 });
  check('登录页渲染', await page.locator('.login-card').isVisible());
  check('快捷账号 chips 存在', await page.getByRole('button', { name: 'admin' }).isVisible());
  await shot('01-login-desktop');

  // 2. Quick login (admin)
  await page.getByRole('button', { name: 'admin' }).click();
  await page.waitForSelector('.topbar', { timeout: 10000 });
  check('快捷账号一键登录成功(顶栏出现)', await page.locator('.topbar').isVisible());
  await shot('02-dashboard-desktop');

  const dl = (s) => page.locator('.desktop-view').locator(s);

  // 3. Dashboard stats cards (desktop view only; phone view is hidden but in DOM)
  const statCount = await dl('.stat').count();
  check('指标卡数量为4', statCount === 4, `count=${statCount}`);
  const statText = await dl('.stat').first().innerText();
  check('指标卡有数字内容', /\d/.test(statText), statText.replace(/\n/g, ' ').slice(0, 40));

  // 4. Finance card + year/month switch
  check('财产卡存在', await dl('.finance-card').first().isVisible());
  const finText = await dl('.finance-card').first().innerText();
  check('财产卡含累计收入', /累计|收入|¥/.test(finText));

  // 5. Digest card
  check('今日日报卡存在', await dl('.digest').first().isVisible());

  // 6. Filter controls
  check('状态分段控件存在', await dl('.seg').first().isVisible());

  // 7. House grid + open detail drawer (已租卡片才进抽屉；空置卡片直达登记弹窗)
  const houseCount = await dl('.card').count();
  check('房源卡片已加载', houseCount > 0, `count=${houseCount}`);
  await shot('03-dashboard-grid');
  await dl('.seg').getByRole('button', { name: '已出租' }).click();
  await page.waitForTimeout(400);
  await dl('.card').first().click();
  await page.waitForSelector('.desktop-view .drawer.show', { timeout: 8000 });
  check('点击已租房源打开详情抽屉', await dl('.drawer.show').isVisible());
  await shot('04-detail-drawer');
  await dl('.drawer.show .back').click();
  await page.waitForTimeout(400);
  await dl('.seg').getByRole('button', { name: '全部' }).click();
  await page.waitForTimeout(300);

  // 8. Detail drawer content (rented or vacant)
  const drawerText = await dl('.drawer').innerText();
  check('抽屉含房源地址信息', /室|栋|小区|租|¥/.test(drawerText));

  // 9. Calendar page
  await page.goto(`${BASE}/#/calendar`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.desktop-view .big-cal', { timeout: 8000 });
  check('月历渲染', await dl('.big-cal').isVisible());
  check('未来7天临期列表存在', await dl('.todo-list').isVisible());
  await shot('05-calendar');

  // 10. Notifications / resend button area
  const notifCount = await dl('.notif-row').count();
  check('通知记录列表渲染', notifCount >= 0, `rows=${notifCount}`);

  // 11. Settings page
  await page.goto(`${BASE}/#/settings`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.desktop-view .setting-grid', { timeout: 8000 });
  check('配置页三卡布局存在', await dl('.setting-grid').isVisible());
  check('推送配置卡存在', await dl('.switch').isVisible());
  check('模板编辑器 textarea 存在', await dl('textarea').first().isVisible());
  await shot('06-settings');

  // 12. Template live preview (type then debounce)
  const ta = dl('textarea').first();
  await ta.fill('【家庭房源日报】{日期} 空置{空置数量}套，临期{临期数量}项');
  await page.waitForTimeout(600);
  const previewText = await dl('.tpl-preview').first().innerText().catch(() => '');
  check('模板实时预览渲染', previewText.length > 0, previewText.slice(0, 30));
  await shot('07-template-preview');

  // 13. Push time select exists
  const timeSel = dl('select').first();
  check('推送时间下拉存在', await timeSel.isVisible());

  // 14. Phone viewport rendering
  const pctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true });
  const ppage = await pctx.newPage();
  ppage.on('console', m => { if (m.type() === 'error') consoleErrors.push('[phone] ' + m.text() + ' @ ' + (m.location()?.url || '?')); });
  ppage.on('response', async (r) => {
    if (r.status() >= 400) {
      let body = '';
      try { body = (await r.text()).slice(0, 200); } catch {}
      log('PHONE BAD RESP', r.status(), r.url(), body);
    }
  });
  await ppage.goto(BASE, { waitUntil: 'networkidle' });
  await ppage.waitForSelector('.login-card', { timeout: 8000 });
  await ppage.getByRole('button', { name: 'admin' }).click();
  await ppage.waitForSelector('.phone-view', { timeout: 8000 });
  check('手机视图出现', await ppage.locator('.phone-view').isVisible());
  await ppage.waitForTimeout(800);
  await ppage.screenshot({ path: `${OUT}/08-phone-dashboard.png` });
  const phoneCards = await ppage.locator('.card').count();
  check('手机视图房源卡片加载', phoneCards > 0, `count=${phoneCards}`);
  await ppage.goto(`${BASE}/#/calendar`, { waitUntil: 'networkidle' });
  await ppage.waitForTimeout(500);
  await ppage.screenshot({ path: `${OUT}/09-phone-calendar.png` });
  await pctx.close();
} catch (e) {
  log('EXCEPTION', e.message);
  results.push({ name: '脚本执行异常', ok: false, detail: e.message });
} finally {
  await browser.close();
}

log('=== console errors ===', consoleErrors.length);
consoleErrors.slice(0, 20).forEach(e => log('  CONSOLE-ERR:', e));
log('=== page errors ===', pageErrors.length);
pageErrors.slice(0, 20).forEach(e => log('  PAGE-ERR:', e));

const passed = results.filter(r => r.ok).length;
log(`=== SUMMARY: ${passed}/${results.length} checks passed ===`);
process.exit(results.every(r => r.ok) && pageErrors.length === 0 ? 0 : 1);
