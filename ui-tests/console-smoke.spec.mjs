import {test, expect} from '@playwright/test';

test('ui dashboard loads without console errors', async ({page}) => {
    const consoleErrors = [];
    page.on('console', msg => {
        if (msg.type() === 'error') {
            consoleErrors.push(msg.text());
        }
    });
    await page.addInitScript(() => {
        localStorage.setItem('integration-ui-api-key', 'dev-api-key');
    });
    await page.goto('/ui/');
    await expect(page.locator('h1')).toContainText(/Integration\s*API/i);
    await expect(page.locator('h2').first()).toBeVisible();
    await expect(page.locator('#scriptBodyInput')).toBeVisible();
    expect(consoleErrors, `Console errors:\n${consoleErrors.join('\n')}`).toEqual([]);
});

test('ui supports script editor draft and debug payload editing', async ({page}) => {
    await page.goto('/ui/');
    await page.click('#newScriptBtn');
    await page.fill('#scriptIdInput', 'ui-draft-script');
    await page.fill('#scriptDescriptionInput', 'черновик для smoke-теста');
    await page.fill('#scriptBodyInput', 'return [ok: true, source: "ui-test"]');
    await page.fill('#debugPayloadInput', '{"sample":true}');
    await page.fill('#debugContextInput', '{"source":"ui-e2e","channel":"playwright"}');
    await expect(page.locator('#scriptBodyInput')).toHaveValue(/ui-test/);
    await expect(page.locator('#debugPayloadInput')).toHaveValue('{"sample":true}');
    await expect(page.locator('#debugContextInput')).toHaveValue('{"source":"ui-e2e","channel":"playwright"}');
    await expect(page.locator('#snapshotExportBtn')).toBeVisible();
    await expect(page.locator('#replayDlqBtn')).toBeVisible();
    await expect(page.locator('#formatScriptBtn')).toBeVisible();
    await expect(page.locator('#wordWrapInput')).toBeChecked();
    await expect(page.locator('#loadConnectorsBtn')).toBeVisible();
    await expect(page.locator('#invokeRestBtn')).toBeVisible();
    await expect(page.locator('#loadBrokerTypesBtn')).toBeVisible();
    await expect(page.locator('#runInboundReactionBtn')).toBeVisible();
});
