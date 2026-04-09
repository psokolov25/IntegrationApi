import {defineConfig, devices} from '@playwright/test';

export default defineConfig({
    testDir: '.',
    timeout: 30_000,
    use: {
        baseURL: process.env.UI_BASE_URL || 'http://127.0.0.1:8080',
        trace: 'retain-on-failure'
    },
    projects: [
        {
            name: 'chromium-desktop',
            use: {...devices['Desktop Chrome']}
        }
    ]
});

