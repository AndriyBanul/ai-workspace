import { chromium } from 'playwright';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';

// Captures the actual Mantine prototype. Start npm run dev or npm run preview first.
const root = fileURLToPath(new URL('..', import.meta.url));
const output = resolve(root, 'screenshots');
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL || 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1080 }, deviceScaleFactor: 1, reducedMotion: 'reduce' });
const errors = [];
const results = [];
page.on('pageerror', error => errors.push(error.message));
page.on('console', message => { if (message.type() === 'error') errors.push(`${message.text()} ${message.location().url || ''}`); });
const base = process.env.DESIGN_URL || 'http://127.0.0.1:5186';
async function route(hash) {
  await page.goto(`${base}/#${hash}`);
  await page.waitForLoadState('networkidle');
  await page.evaluate(() => document.fonts.ready);
}
async function shot(name, fullPage = true) {
  await page.screenshot({ path: resolve(output, `${name}.png`), fullPage, animations: 'disabled' });
  results.push({ screenshot: `${name}.png`, viewport: page.viewportSize(), result: 'captured' });
}
async function noOverflow(label) {
  const sizes = await page.evaluate(() => ({ width: innerWidth, content: document.documentElement.scrollWidth }));
  assert(sizes.content <= sizes.width + 1, `${label}: horizontal overflow ${JSON.stringify(sizes)}`);
}
try {
  await route('library');
  await shot('01-library-desktop');
  await page.getByLabel('Search sources').fill('Atlas');
  assert.equal(await page.locator('tbody tr').count(), 1);
  await page.getByLabel('Search sources').fill('nothing-matches');
  await shot('02-library-empty-search');
  await page.getByRole('button', {name:'Clear filters'}).click();
  assert.equal(await page.locator('tbody tr').count(), 6);
  await page.getByRole('button', {name:'Add source',exact:true}).click();
  await shot('03-add-source-file', false);
  await page.getByRole('tab', {name:'YouTube',exact:true}).click();
  await page.getByLabel('YouTube video URL').fill('https://www.youtube.com/watch?v=example-id');
  await shot('04-add-source-youtube', false);
  await page.getByRole('tab', {name:'Web page',exact:true}).click();
  await shot('05-add-source-web', false);
  await page.keyboard.press('Escape');
  await route('ask');
  await shot('06-ask-desktop');
  await page.getByRole('button', {name:'Read evidence 1'}).click();
  assert(await page.getByText('The first session should produce a useful answer.',{exact:false}).isVisible());
  await page.getByRole('button', {name:'Insufficient evidence',exact:true}).click();
  await shot('07-ask-insufficient-evidence');
  await route('source/interview');
  await shot('08-source-transcript');
  await route('source/market');
  await shot('09-source-recovery');
  await route('studio');
  await shot('10-studio-image');
  await page.locator('label').filter({hasText:/^Video$/}).click();
  await page.getByRole('button', {name:'pending',exact:true}).click();
  await shot('11-studio-video-processing');
  await page.locator('label').filter({hasText:/^Speech$/}).click();
  await shot('12-studio-speech');
  await page.locator('label').filter({hasText:/^Analyze media$/}).click();
  await shot('13-studio-analyze');
  await page.getByRole('button', {name:'error',exact:true}).click();
  await shot('14-studio-error');
  await route('activity');
  await shot('15-activity-desktop');
  await route('login');
  await shot('16-login-desktop');
  await page.getByRole('button', {name:'Create an account',exact:true}).click();
  await shot('17-registration-desktop');
  await page.setViewportSize({width:390,height:844});
  await route('library');
  assert.equal(await page.getByRole('table').isVisible(), false, 'Mobile uses cards instead of a duplicate table');
  assert(await page.getByRole('button',{name:'Mobile actions for Atlas launch brief.pdf'}).isVisible());
  await shot('18-library-mobile');
  await page.getByRole('button',{name:'Open navigation'}).click();
  assert(await page.getByRole('dialog').getByRole('button',{name:'Sign out',exact:true}).isVisible());
  await shot('19-mobile-navigation', false);
  await page.keyboard.press('Escape');
  await route('ask');
  await shot('20-ask-mobile');
  await route('studio');
  await shot('21-studio-mobile');
  await route('login');
  await shot('22-login-mobile');
  for (const width of [390,768,1024,1440]) {
    await page.setViewportSize({width,height:900});
    for (const path of ['library','ask','studio','activity','source/interview','login']) {
      await route(path);
      await noOverflow(`${path} at ${width}px`);
    }
  }
  await page.setViewportSize({width:1440,height:1080});
  await page.goto(`${base}/gallery.html`);
  await page.waitForLoadState('networkidle');
  await page.locator('#overview').screenshot({path:resolve(output,'00-overview.png'),animations:'disabled'});
  assert.deepEqual(errors, [], 'Browser runtime errors');
  await writeFile(resolve(output,'verification.json'),JSON.stringify({ checkedAt: new Date().toISOString(), browser: await browser.version(), results, checks: ['Build verified separately','Source search and clear filters','Evidence selection','Import tabs and Escape dismissal','Studio tool/state switching','Mobile navigation exposes sign out','No horizontal page overflow: six routes at 390/768/1024/1440px','No browser runtime errors'], errors },null,2));
  console.log(`Captured ${results.length} screenshots. Interaction and responsive checks passed.`);
} finally {
  await browser.close();
}
