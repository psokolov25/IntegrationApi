import {test, expect} from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test('capture integration ui screenshot', async ({page}) => {
    await page.addInitScript(() => {
        localStorage.setItem('integration-ui-api-key', 'dev-api-key');
        localStorage.setItem('integration-ui-active-tab', 'eventing');
    });
    await page.goto('/ui/');
    await expect(page.locator('h1')).toBeVisible();

    const screenshotPath = path.resolve(__dirname, '../cache/ui-tools/playwright-artifacts/ui-console.png');
    fs.mkdirSync(path.dirname(screenshotPath), {recursive: true});
    await page.screenshot({path: screenshotPath, fullPage: true});
});
