import assert from "node:assert/strict";
import { test } from "node:test";

import { createHold, watchReadinessStep } from "../lib/held-response.mjs";

const INDICATOR = '[aria-label="data-testid Loading indicator"]';

/** Timers that never run by themselves: the test fires the deadline by hand, so nothing here waits for a clock. */
const manualTimers = () => {
    /** @type {Array<{ fire: () => void, stopped: boolean }>} */
    const started = [];
    return {
        started,
        timers: {
            startTimer: (/** @type {() => void} */ fire) => {
                const entry = { fire, stopped: false };
                started.push(entry);
                return entry;
            },
            stopTimer: (/** @type {{ stopped: boolean }} */ entry) => {
                entry.stopped = true;
            },
        },
    };
};

/** A stand-in for the page's locators: the helper under test calls .locator(selector) on its variable field, like expectVariableReady. */
const fakeLocators = () => ({ locator: (/** @type {string} */ selector) => ({ selector }) });

/** The two helpers of the dashboard, reduced to what the readiness specs observe of them. */
const correctHelper = async (/** @type {{ locator: (s: string) => unknown }} */ field, /** @type {() => Promise<void>} */ waitUntilLoaded) => {
    field.locator(INDICATOR); // the readiness step: look for the loading indicator first
    await waitUntilLoaded();
};
const bypassedHelper = async (/** @type {{ locator: (s: string) => unknown }} */ _field, /** @type {() => Promise<void>} */ _waitUntilLoaded) => {
    // no readiness step: it goes on to read the control at once
};

const turns = async (/** @type {number} */ count) => {
    for (let index = 0; index < count; index += 1) {
        await Promise.resolve();
    }
};

test("the held response is released by the selection reaching its readiness step, after any amount of setup", async () => {
    const { timers, started } = manualTimers();
    const hold = createHold({ deadlineMs: 60000, timers });
    const field = fakeLocators();
    const restore = watchReadinessStep(field, () => hold.signalReadiness(), INDICATOR);
    let continued = 0;
    const request = hold.held(async () => {
        continued += 1;
    });
    // a slow setup and slow loading assertions: many turns pass, and the request stays held with nothing released (the old timer would have gone off here)
    field.locator(INDICATOR); // the test's own loading assertion, before arm(): it must not release
    await turns(5000);
    assert.equal(hold.phase(), "held");
    assert.equal(continued, 0, "the request has not continued");
    hold.arm();
    const selection = correctHelper(field, async () => {
        await request;
    });
    await selection;
    restore();
    hold.drain();
    assert.equal(hold.phase(), "released");
    assert.equal(continued, 1, "the request continued once, unchanged");
    assert.equal(hold.releasedBy(), "signal");
    assert.deepEqual(hold.verdict(), []);
    assert.equal(started.length, 1);
    assert.equal(started[0]?.stopped, true, "the deadline timer does not outlive the test");
});

test("a helper that bypasses the readiness step is a failure: the deadline releases the response and the verdict names it", async () => {
    const { timers, started } = manualTimers();
    const hold = createHold({ deadlineMs: 60000, timers });
    const field = fakeLocators();
    const restore = watchReadinessStep(field, () => hold.signalReadiness(), INDICATOR);
    const request = hold.held(async () => undefined);
    hold.arm();
    await bypassedHelper(field, async () => undefined);
    await turns(50);
    assert.equal(hold.phase(), "held", "nothing signalled, so the response is still held");
    started[0]?.fire(); // the overall deadline
    await request;
    restore();
    hold.drain();
    assert.equal(hold.releasedBy(), "deadline");
    assert.match(hold.verdict().join(" "), /released by deadline, not by the selection reaching the readiness step/);
});

test("a release by a premature read or by the cleanup never passes", async () => {
    for (const by of /** @type {const} */ (["premature-read", "cleanup"])) {
        const { timers } = manualTimers();
        const hold = createHold({ deadlineMs: 60000, timers });
        const request = hold.held(async () => undefined);
        hold.release(by);
        await request;
        assert.match(hold.verdict().join(" "), new RegExp(`released by ${by}`));
    }
    const { timers } = manualTimers();
    const never = createHold({ deadlineMs: 60000, timers });
    assert.match(never.verdict().join(" "), /never requested/);
});

test("the readiness step of the test itself, before arm(), and a second step after the release, change nothing", async () => {
    const { timers } = manualTimers();
    const hold = createHold({ deadlineMs: 60000, timers });
    const field = fakeLocators();
    const restore = watchReadinessStep(field, () => hold.signalReadiness(), INDICATOR);
    const request = hold.held(async () => undefined);
    field.locator(INDICATOR);
    field.locator("#unrelated");
    assert.equal(hold.phase(), "held", "unarmed and unrelated lookups release nothing");
    hold.arm();
    field.locator("#unrelated");
    assert.equal(hold.phase(), "held");
    field.locator(INDICATOR);
    await request;
    field.locator(INDICATOR);
    assert.equal(hold.releasedBy(), "signal", "the first armed step released, later ones do not take over");
    restore();
    assert.equal(typeof field.locator, "function");
    assert.deepEqual(field.locator("x"), { selector: "x" }, "the original lookup is back");
});

test("the cleanup releases a held request that nothing released, so no handler stays blocked", async () => {
    const { timers, started } = manualTimers();
    const hold = createHold({ deadlineMs: 60000, timers });
    let continued = 0;
    const request = hold.held(async () => {
        continued += 1;
    });
    hold.drain();
    await request;
    assert.equal(continued, 1, "the in-flight handler finished, so the interception can be removed");
    assert.equal(hold.releasedBy(), "cleanup");
    assert.equal(started[0]?.stopped, true);
    assert.notDeepEqual(hold.verdict(), []);
});

test("two requests that arrive while held share one gate and one deadline timer", async () => {
    const { timers, started } = manualTimers();
    const hold = createHold({ deadlineMs: 60000, timers });
    let continued = 0;
    const first = hold.held(async () => {
        continued += 1;
    });
    const second = hold.held(async () => {
        continued += 1;
    });
    await turns(20);
    assert.equal(started.length, 1, "one timer");
    assert.equal(continued, 0);
    hold.arm();
    hold.signalReadiness();
    await Promise.all([first, second]);
    assert.equal(continued, 2, "both continued once the gate opened");
    assert.equal(hold.releasedBy(), "signal");
});
