import {defineConfig, devices} from '@playwright/test';

export default defineConfig({
    testDir: '.',
    timeout: 30_000,
    retries: process.env.CI ? 1 : 0,
    reporter: [['list'], ['html', {open: 'never', outputFolder: '../cache/ui-tools/playwright-report'}]],
    use: {
        baseURL: process.env.UI_BASE_URL || 'http://127.0.0.1:8080',
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure',
        video: 'retain-on-failure'
    },
    outputDir: '../cache/ui-tools/playwright-artifacts',
    projects: [
        {
            name: 'chromium-desktop',
            use: {...devices['Desktop Chrome']}
        },
        {
            name: 'firefox-desktop',
            use: {...devices['Desktop Firefox']}
        },
        {
            name: 'webkit-desktop',
            use: {...devices['Desktop Safari']}
        }
    ]
});
