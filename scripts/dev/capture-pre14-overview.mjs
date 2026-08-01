#!/usr/bin/env node

/**
 * Deterministic local-browser capture for Pre-14 Result Overview visual
 * reconciliation. It only permits the disposable local dev server and logs
 * in through the ordinary form when a session is needed; it never reads or
 * exports browser cookies/storage.
 *
 * Required environment:
 *   KSH_CAPTURE_EMAIL, KSH_CAPTURE_PASSWORD, KSH_CAPTURE_ROUTE,
 *   KSH_CAPTURE_OUT
 * Optional environment:
 *   KSH_CAPTURE_WIDTH (default 1536), KSH_CAPTURE_HEIGHT (default 915),
 *   KSH_CAPTURE_STATE (default, writing-task-{0..3}[-criterion-{n}],
 *     speaking-overview or speaking-criterion-{0..5}),
 *   KSH_CAPTURE_SCROLL_TARGET (none or result-analysis),
 *   KSH_CAPTURE_BASE_URL (default http://127.0.0.1:18080),
 *   PLAYWRIGHT_CORE_PATH, CHROMIUM_EXECUTABLE
 */
import { createRequire } from "node:module";
import { mkdir } from "node:fs/promises";
import path from "node:path";

const captureBaseUrl = process.env.KSH_CAPTURE_BASE_URL ?? "http://127.0.0.1:18080";
const captureRoute = process.env.KSH_CAPTURE_ROUTE;
const captureOut = process.env.KSH_CAPTURE_OUT;
const captureWidth = Number(process.env.KSH_CAPTURE_WIDTH ?? "1536");
const captureHeight = Number(process.env.KSH_CAPTURE_HEIGHT ?? "915");
const captureState = process.env.KSH_CAPTURE_STATE ?? "default";
const captureScrollTarget = process.env.KSH_CAPTURE_SCROLL_TARGET ?? "none";
const playwrightCorePath = process.env.PLAYWRIGHT_CORE_PATH
  ?? "/Users/toanlamsaoduocc/Library/Caches/ms-playwright-go/1.57.0/package/index.js";
const chromiumExecutable = process.env.CHROMIUM_EXECUTABLE
  ?? "/Users/toanlamsaoduocc/Library/Caches/ms-playwright/chromium_headless_shell-1228/chrome-headless-shell-mac-arm64/chrome-headless-shell";

if (captureBaseUrl !== "http://127.0.0.1:18080") {
  throw new Error("Pre-14 capture only permits the disposable local dev server.");
}
if (!captureRoute?.startsWith("/practice/attempts/") || !captureOut) {
  throw new Error("KSH_CAPTURE_ROUTE must be a practice-attempt route and KSH_CAPTURE_OUT is required.");
}
if (!Number.isInteger(captureWidth) || !Number.isInteger(captureHeight)
    || captureWidth < 320 || captureHeight < 320) {
  throw new Error("KSH_CAPTURE_WIDTH and KSH_CAPTURE_HEIGHT must be valid CSS viewport dimensions.");
}
const writingState = /^writing-task-([0-3])(?:-criterion-([0-9]+))?$/.exec(captureState);
const speakingState = /^speaking-(overview|criterion-([0-5]))$/.exec(captureState);
if (captureState !== "default" && !writingState && !speakingState) {
  throw new Error("KSH_CAPTURE_STATE is outside the bounded Writing/Speaking Overview states.");
}
if (!new Set(["none", "result-analysis"]).has(captureScrollTarget)) {
  throw new Error("KSH_CAPTURE_SCROLL_TARGET must be none or result-analysis.");
}

const require = createRequire(import.meta.url);
const { chromium } = require(playwrightCorePath);
const browser = await chromium.launch({
  headless: true,
  executablePath: chromiumExecutable
});

try {
  const context = await browser.newContext({
    viewport: { width: captureWidth, height: captureHeight },
    deviceScaleFactor: 1,
    colorScheme: "light",
    reducedMotion: "reduce",
    locale: "vi-VN"
  });
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.__KSH_DISABLE_RESULT_MOTION__ = true;
    document.documentElement.dataset.practiceMotion = "off";
  });
  await page.goto(`${captureBaseUrl}${captureRoute}`, { waitUntil: "networkidle" });

  if (new URL(page.url()).pathname === "/login") {
    const email = process.env.KSH_CAPTURE_EMAIL;
    const password = process.env.KSH_CAPTURE_PASSWORD;
    if (!email || !password) {
      throw new Error("Local capture needs KSH_CAPTURE_EMAIL and KSH_CAPTURE_PASSWORD to use the normal login form.");
    }
    await page.locator("#account").fill(email);
    await page.locator("#password").fill(password);
    await Promise.all([
      page.waitForURL(url => url.pathname !== "/login"),
      page.locator("#loginForm button[type=submit]").click()
    ]);
    await page.goto(`${captureBaseUrl}${captureRoute}`, { waitUntil: "networkidle" });
  }

  const currentUrl = new URL(page.url());
  if (currentUrl.origin !== captureBaseUrl || currentUrl.pathname === "/login") {
    throw new Error(`Capture did not reach the requested local Result route: ${page.url()}`);
  }

  if (writingState) {
    const taskIndex = Number(writingState[1]);
    const taskTab = page.locator(`#writing-task-tab-${taskIndex}`);
    if (await taskTab.count() !== 1) {
      throw new Error(`Writing task tab ${taskIndex} is unavailable on ${captureRoute}.`);
    }
    await taskTab.click();
    await page.locator(`#writing-task-panel-${taskIndex}:not([hidden])`).waitFor();

    if (writingState[2] !== undefined) {
      const criterionIndex = Number(writingState[2]);
      const criterionTab = page.locator(`#writing-criterion-tab-${taskIndex}-${criterionIndex}`);
      if (await criterionTab.count() !== 1) {
        throw new Error(`Writing criterion tab ${taskIndex}-${criterionIndex} is unavailable on ${captureRoute}.`);
      }
      await criterionTab.click();
      await page.locator(`#writing-criterion-panel-${taskIndex}-${criterionIndex}:not([hidden])`).waitFor();
    }
  }

  if (speakingState) {
    const tabId = speakingState[1] === "overview"
      ? "speaking-overview-tab"
      : `speaking-criterion-tab-${Number(speakingState[2])}`;
    const tab = page.locator(`#${tabId}`);
    if (await tab.count() !== 1) {
      throw new Error(`Speaking overview tab ${tabId} is unavailable on ${captureRoute}.`);
    }
    await tab.click();
    const controlledPanelId = await tab.getAttribute("aria-controls");
    if (!controlledPanelId) {
      throw new Error(`Speaking overview tab ${tabId} has no controlled panel.`);
    }
    await page.locator(`#${controlledPanelId}:not([hidden])`).waitFor();
  }

  if (captureScrollTarget === "result-analysis") {
    const resultAnalysis = page.locator("#result-analysis");
    if (await resultAnalysis.count() !== 1) {
      throw new Error(`Result analysis is unavailable on ${captureRoute}.`);
    }
    await resultAnalysis.scrollIntoViewIfNeeded();
    await resultAnalysis.evaluate(element => element.scrollIntoView({ block: "start" }));
  }

  const measured = await page.evaluate(() => {
    const rect = selector => {
      const element = document.querySelector(selector);
      if (!element) return null;
      const box = element.getBoundingClientRect();
      return {
        x: Number(box.x.toFixed(2)),
        y: Number(box.y.toFixed(2)),
        width: Number(box.width.toFixed(2)),
        height: Number(box.height.toFixed(2)),
        bottom: Number(box.bottom.toFixed(2))
      };
    };
    const learnerText = document.body.innerText;
    const tableHeaders = [...document.querySelectorAll(".pr-table thead th")]
      .filter(element => getComputedStyle(element).display !== "none")
      .map(element => {
        const box = element.getBoundingClientRect();
        return { left: box.left, right: box.right };
      });
    return {
      innerWidth: window.innerWidth,
      innerHeight: window.innerHeight,
      devicePixelRatio: window.devicePixelRatio,
      clientWidth: document.documentElement.clientWidth,
      clientHeight: document.documentElement.clientHeight,
      scrollWidth: document.documentElement.scrollWidth,
      scrollHeight: document.documentElement.scrollHeight,
      title: document.title,
      geometry: {
        topbar: rect(".pr-topbar"),
        main: rect(".pr-main"),
        summary: rect(".pr-summary"),
        analysis: rect("#result-analysis"),
        speakingHeading: rect(".pr-speaking-overview > .pr-section-heading"),
        speakingTabs: rect(".pr-speaking-overview-tabs"),
        speakingOverviewPanel: rect(".pr-speaking-overview-panel:not([hidden])"),
        speakingDashboard: rect(".pr-speaking-criteria-dashboard"),
        speakingGuidance: rect(".pr-speaking-overview-guidance"),
        speakingScoreStatus: rect(".pr-speaking-score-status"),
        speakingSummary: rect(".pr-speaking-summary"),
        speakingFindingSummary: rect(".pr-speaking-finding-summary"),
        speakingActionPlan: rect(".pr-speaking-action-plan"),
        speakingEvidenceDetails: rect(".pr-speaking-evidence-details"),
        speakingDetailAction: rect(".pr-speaking-detail-action"),
        speakingCriterionPanel: rect(".pr-speaking-criterion-panel:not([hidden])"),
        speakingCriterionDescriptor: rect(".pr-speaking-criterion-panel:not([hidden]) .pr-speaking-criterion-descriptor"),
        speakingCriterionDetail: rect(".pr-speaking-criterion-panel:not([hidden]) .pr-speaking-criterion-detail"),
        objectiveGroups: rect(".pr-objective-groups"),
        objectiveTable: rect(".pr-table-wrap"),
        nextAction: rect(".pr-next-action")
      },
      objectiveGroupCount: document.querySelectorAll(".pr-objective-group").length,
      tableHeaderOverlap: tableHeaders.some((header, index) => {
        const next = tableHeaders[index + 1];
        return next ? header.right > next.left + 0.5 : false;
      }),
      internalCodeLeakage: /strategyCode|evidenceId|scenario\s*\d+|provider/i.test(learnerText)
    };
  });
  if (measured.innerWidth !== captureWidth || measured.innerHeight !== captureHeight
      || measured.devicePixelRatio !== 1) {
    throw new Error(`Viewport mismatch: ${JSON.stringify(measured)}`);
  }

  await mkdir(path.dirname(captureOut), { recursive: true });
  await page.screenshot({ path: captureOut, type: "png", fullPage: false });
  process.stdout.write(`${JSON.stringify({
    route: captureRoute,
    state: captureState,
    scrollTarget: captureScrollTarget,
    output: captureOut,
    viewport: measured,
    horizontalOverflow: measured.scrollWidth > measured.clientWidth
  })}\n`);
  await context.close();
} finally {
  await browser.close();
}
