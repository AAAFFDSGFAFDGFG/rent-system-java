import { chromium } from '/Users/mikeygao/Desktop/个人开发项目/rent-system-java/frontend/node_modules/playwright/index.mjs';
const BASE = 'http://localhost:8080';
const OUT = '/Users/mikeygao/Desktop/个人开发项目/rent-system-java/verify/shots';
import { mkdirSync } from 'fs';
mkdirSync(OUT, { recursive: true });

const log = (...a) => console.log('[flow]', ...a);
const results = [];
function check(name, ok, d = '') { results.push({ name, ok: !!ok }); log(ok ? 'PASS' : 'FAIL', '-', name, d); }

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await ctx.newPage();
const consoleErrors = [];
page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text() + ' @ ' + (m.location()?.url || '?')); });
page.on('response', async (r) => { if (r.status() >= 400) log('HTTP', r.status(), r.url()); });
const dl = (s) => page.locator('.desktop-view').locator(s);
async function shot(n) { await page.screenshot({ path: `${OUT}/${n}.png` }); }

try {
  await page.goto(BASE, { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: 'admin' }).click();
  await page.waitForSelector('.topbar');
  await page.waitForTimeout(400);

  // ---- Test A: 卡片「登记出租」按钮直接弹窗（不跳抽屉） ----
  await dl('.seg').getByRole('button', { name: '空置中' }).click();
  await page.waitForTimeout(500);
  const vacantCards = await dl('.card').count();
  check('空置筛选后有房源', vacantCards > 0, `count=${vacantCards}`);
  // 点击卡片主体（非按钮区域）也应直达弹窗，彻底不经抽屉
  await dl('.card').first().locator('.head').click();
  await page.waitForSelector('.modal.show', { timeout: 8000 });
  check('空置卡片任意位置直达登记表单', await page.locator('.modal.show').isVisible());
  check('未跳转详情抽屉', await dl('.drawer.show').count() === 0);
  const modalBox = await page.locator('.modal.show').boundingBox();
  const fInputs = page.locator('.modal.show .frm-grid input');
  const inL = await fInputs.nth(0).boundingBox();
  const inR = await fInputs.nth(1).boundingBox();
  const lg = inL.x - modalBox.x, rg = modalBox.x + modalBox.width - inR.x - inR.width;
  check('弹窗内输入框左右对称留白', lg > 12 && Math.abs(lg - rg) < 4, `left=${lg} right=${rg}`);
  await page.locator('.modal.show').getByPlaceholder('如 张伟').fill('测试租客');
  await page.locator('.modal.show').getByPlaceholder('>0').fill('3500');
  await shot('10-lease-form');
  await page.locator('.modal.show').getByRole('button', { name: '确认提交' }).click();
  await page.waitForTimeout(1200);
  check('提交后弹窗关闭', await page.locator('.modal.show').count() === 0);
  const vacantAfter = await dl('.card').count();
  check('登记后空置房源减少一套', vacantAfter === vacantCards - 1, `before=${vacantCards} after=${vacantAfter}`);
  await shot('11-after-lease');

  // ---- Test B: 两阶段违约退租（重载页面，会话持久化，避免残留抽屉干扰）----
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForSelector('.topbar');
  await page.waitForTimeout(400);
  // 该刚登记房源现为已租且合同未到期 -> 退租应触发违约确认
  await dl('.seg').getByRole('button', { name: '已出租' }).click();
  await page.waitForTimeout(500);
  await dl('.card').first().click();
  await page.waitForSelector('.desktop-view .drawer.show');
  await dl('.drawer').getByRole('button', { name: '退租' }).click();
  await page.waitForTimeout(800);
  const breachBox = await dl('.breach-box').count();
  check('提前退租弹出违约确认框(两阶段)', breachBox === 1, `breachBox=${breachBox}`);
  if (breachBox === 1) {
    await shot('12-breach-confirm');
    await dl('.breach-box').getByRole('button', { name: '确认退租并没收押金' }).click();
    await page.waitForTimeout(1000);
    const pill = await dl('.drawer').locator('.pill').innerText();
    check('确认违约退租后房源转为空置', /空置/.test(pill), pill);
    await shot('13-after-breach');
  }
  const realErrors = consoleErrors.filter(e => !/\/api\/houses\/\d+\/vacate/.test(e));
  check('流程期间无控制台错误(两阶段违约 409 为预期)', realErrors.length === 0, realErrors.join(' | '));
} catch (e) {
  log('EXCEPTION', e.message);
  results.push({ name: '异常', ok: false });
} finally {
  await browser.close();
}
const passed = results.filter(r => r.ok).length;
log(`=== FLOW SUMMARY: ${passed}/${results.length} ===`);
process.exit(results.every(r => r.ok) ? 0 : 1);
