import { chromium } from '/Users/mikeygao/Desktop/个人开发项目/rent-system-java/frontend/node_modules/playwright/index.mjs';
const BASE = 'http://localhost:8080';
const OUT = '/Users/mikeygao/Desktop/个人开发项目/rent-system-java/verify/shots';
const log = (...a) => console.log('[ui]', ...a);
const results = [];
function check(n, ok, d = '') { results.push({ n, ok: !!ok }); log(ok ? 'PASS' : 'FAIL', '-', n, d); }

const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  // ---------- 桌面 ----------
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  const errs = [];
  page.on('console', m => { if (m.type() === 'error') errs.push(m.text()); });
  await page.goto(BASE, { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: 'admin' }).click();
  await page.waitForSelector('.topbar');
  await page.waitForTimeout(600);
  const dl = s => page.locator('.desktop-view').locator(s);
  await page.screenshot({ path: `${OUT}/20-desktop-layout.png` });

  // 品牌名
  check('顶栏品牌名为家庭房源', /家庭房源/.test(await dl('.brand').innerText()));
  check('无旧品牌名残留', !(await dl('.brand').innerText()).includes('家·房源'));

  // 需求4：三个 tab 在上、四卡+财产卡在下
  const sidebarStats = await dl('.sidebar .overview .stat').count();
  check('桌面四张指标卡在左侧栏', sidebarStats === 4, `count=${sidebarStats}`);
  check('桌面财产卡在左侧栏', await dl('.sidebar .overview .finance-card').isVisible());
  const navCount = await dl('.sidebar .side-nav button').count();
  check('左侧三个tab存在', navCount === 3, `count=${navCount}`);
  const ovBox = await dl('.sidebar .overview').boundingBox();
  const navBox = await dl('.sidebar .side-nav').boundingBox();
  check('三个tab在上、卡片在下', !!ovBox && !!navBox && navBox.y < ovBox.y, `navY=${navBox?.y} overviewY=${ovBox?.y}`);
  const contentStats = await dl('.content .stat').count();
  check('右侧内容区不再重复四卡', contentStats === 0, `count=${contentStats}`);

  // 需求2：空置卡片任意点击直达登记弹窗
  await dl('.seg').getByRole('button', { name: '空置中' }).click();
  await page.waitForTimeout(400);
  await dl('.card').first().locator('.head').click();
  await page.waitForSelector('.modal.show', { timeout: 8000 });
  check('桌面空置卡片直达登记弹窗', await page.locator('.modal.show').isVisible());
  check('桌面未弹出抽屉', await dl('.drawer.show').count() === 0);
  // 需求1：弹窗挂到 body 且输入框左右留白对称
  check('弹窗 Teleport 到 body', await page.locator('body > .modal').count() >= 1);
  const mBox = await page.locator('.modal.show').boundingBox();
  const inputs = page.locator('.modal.show .frm-grid input');
  const iLeft = await inputs.nth(0).boundingBox();   // 左列
  const iRight = await inputs.nth(1).boundingBox();  // 右列
  const lGap = iLeft.x - mBox.x;
  const rGap = mBox.x + mBox.width - iRight.x - iRight.width;
  check('桌面弹窗输入框左右留白对称', lGap > 12 && Math.abs(lGap - rGap) < 4, `left=${lGap} right=${rGap}`);
  await page.locator('.modal.show .close').click();
  await page.waitForTimeout(400);

  // 需求4（付款日历回归）
  await dl('.seg').getByRole('button', { name: '已出租' }).click();
  await page.waitForTimeout(400);
  await dl('.card').first().click();
  await page.waitForSelector('.desktop-view .drawer.show');
  const dayCount = await dl('.drawer .cal .d').count();
  check('付款日历渲染日期格', dayCount >= 28, `dayCells=${dayCount}`);
  const payCells = await dl('.drawer .cal .d.pay').count();
  check('付款日历有付款日高亮', payCells === 1, `payCells=${payCells}`);
  await page.screenshot({ path: `${OUT}/21-desktop-payment-calendar.png` });
  check('桌面无控制台错误', errs.length === 0, errs.join(' | '));
  await ctx.close();

  // ---------- 手机 ----------
  const pctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true });
  const pp = await pctx.newPage();
  const perrs = [];
  pp.on('console', m => { if (m.type() === 'error') perrs.push(m.text()); });
  await pp.goto(BASE, { waitUntil: 'networkidle' });
  await pp.getByRole('button', { name: 'admin' }).click();
  await pp.waitForSelector('.phone-view.show');
  await pp.waitForTimeout(700);
  await pp.screenshot({ path: `${OUT}/22-phone-layout.png` });

  // 需求1/手机：视口四周留缝隙
  const devBox = await pp.locator('.phone-view .device').boundingBox();
  check('手机设备框左侧留缝隙', !!devBox && devBox.x > 0, `deviceX=${devBox?.x}`);
  check('手机设备框右侧留缝隙', !!devBox && devBox.x + devBox.width < 390, `right=${devBox ? devBox.x + devBox.width : '?'}`);
  check('手机设备框顶部留缝隙', !!devBox && devBox.y > 0, `deviceY=${devBox?.y}`);

  // 手机端四卡一行
  const phoneStats = pp.locator('.phone-view .phone-stats');
  check('手机端四卡一行容器存在', await phoneStats.isVisible());
  const firstStat = await phoneStats.locator('.stat').first().boundingBox();
  const fourthStat = await phoneStats.locator('.stat').nth(3).boundingBox();
  check('手机端四卡同一行', !!firstStat && !!fourthStat && Math.abs(firstStat.y - fourthStat.y) < 3, `y1=${firstStat?.y} y4=${fourthStat?.y}`);

  // 手机端登记弹窗：直达 + 输入框不贴边
  await pp.locator('.phone-view .seg').getByRole('button', { name: '空置中' }).click();
  await pp.waitForTimeout(400);
  await pp.locator('.phone-view .card').first().locator('.head').click();
  await pp.waitForSelector('.modal.show', { timeout: 8000 });
  check('手机空置卡片直达登记弹窗', await pp.locator('.modal.show').isVisible());
  check('手机未弹出抽屉', await pp.locator('.drawer.show').count() === 0);
  const pm = await pp.locator('.modal.show').boundingBox();
  const pin = pp.locator('.modal.show .frm-grid input');
  const piL = await pin.nth(0).boundingBox();
  const piR = await pin.nth(1).boundingBox();
  const pl = piL.x - pm.x, pr = pm.x + pm.width - piR.x - piR.width;
  check('手机弹窗输入框左右对称留白', pl > 12 && Math.abs(pl - pr) < 4, `left=${pl} right=${pr}`);
  check('手机弹窗未超出视口', pm.x >= 0 && pm.x + pm.width <= 390, `x=${pm.x} right=${pm.x + pm.width}`);
  await pp.screenshot({ path: `${OUT}/32-phone-modal.png` });
  await pp.locator('.modal.show .close').click();
  await pp.waitForTimeout(400);

  // 配置页「每日推送时间」行：小字已移除、选择框不出边界
  await pp.locator('.phone-view .phone-tab').getByRole('button', { name: '我的' }).click();
  await pp.waitForTimeout(600);
  const pushRow = pp.locator('.phone-view .push-time-row');
  check('推送时间行存在', await pushRow.count() === 1);
  check('推送时间行已无细节小字', await pushRow.locator('.ls').count() === 0);
  check('推送时间行保留标题', /每日推送时间/.test(await pushRow.locator('.lt').innerText()));
  const pCard = await pushRow.locator('xpath=ancestor::div[contains(@class,"setting-card")][1]').boundingBox();
  const pSel = await pushRow.locator('select').boundingBox();
  check('推送时间选择框未出边界', !!pCard && !!pSel && (pSel.x + pSel.width) <= (pCard.x + pCard.width),
    `selRight=${pSel?.x + pSel?.width} cardRight=${pCard?.x + pCard?.width}`);
  await pushRow.scrollIntoViewIfNeeded();
  await pp.screenshot({ path: `${OUT}/41-after-push-time.png` });

  check('手机端无控制台错误', perrs.length === 0, perrs.join(' | '));
  await pctx.close();
} catch (e) {
  log('EXCEPTION', e.message);
  results.push({ n: '脚本异常', ok: false });
} finally {
  await browser.close();
}
const passed = results.filter(r => r.ok).length;
log(`=== UI SUMMARY: ${passed}/${results.length} ===`);
process.exit(results.every(r => r.ok) ? 0 : 1);
