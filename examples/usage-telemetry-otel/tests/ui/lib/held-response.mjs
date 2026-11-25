// The coordination of the two readiness specs, apart from the browser: one real response is held, and it is released by exactly one
// thing, a signal that the selection under test reached the readiness step of the helper. A deadline exists only to fail: it opens the
// gate so that nothing stays blocked, but it marks the run as failed, and the verdict refuses a run that was released by it (or by a
// premature read, or by the cleanup). Dependency-free like plan.mjs; the timers are handed in, so a test can fire the deadline by hand
// and no test waits for a clock.
// Limit of the signal: it is the helper's LOOKUP of the loading indicator (the locator is built, synchronously, before any assertion on it
// runs), so it proves that the selection reached the readiness step, not that the helper then waited on it. A helper that builds the
// locator and never asserts on it is caught only by the specs' own premature-read check, which stays in place.

/** @typedef {"signal" | "deadline" | "premature-read" | "cleanup"} Releaser */
/** @typedef {{ startTimer: (fire: () => void, ms: number) => unknown, stopTimer: (timer: unknown) => void }} Timers */

/**
 * @param {{ deadlineMs: number, timers: Timers }} options
 * @returns {{ phase: () => "idle" | "held" | "released", releasedBy: () => Releaser | undefined, held: (proceed: () => Promise<void>) => Promise<void>, release: (by: Releaser) => void, signalReadiness: () => void, arm: () => void, verdict: () => string[], drain: () => void }}
 */
export function createHold({ deadlineMs, timers }) {
    /** @type {"idle" | "held" | "released"} */
    let phase = "idle";
    /** @type {Releaser | undefined} */
    let by;
    let armed = false;
    /** @type {unknown} */
    let timer;
    /** @type {() => void} */
    let open = () => undefined;
    const gate = new Promise((resolve) => {
        open = () => resolve(undefined);
    });
    /** @param {Releaser} releaser */
    const release = (releaser) => {
        by ??= releaser;
        open();
    };
    return {
        phase: () => phase,
        releasedBy: () => by,
        /** The route handler's body: hold the request until the gate opens, then let it continue unchanged. */
        async held(proceed) {
            if (phase === "idle") {
                phase = "held";
                timer = timers.startTimer(() => release("deadline"), deadlineMs);
            }
            await gate;
            timers.stopTimer(timer);
            phase = "released";
            await proceed();
        },
        release,
        /** Called by the instrumentation each time the helper reaches its readiness step; only the first one after arm() counts. */
        signalReadiness() {
            if (armed && phase === "held") {
                release("signal");
            }
        },
        /** From here on a readiness step is the selection's own (the setup and loading assertions come first and never release). */
        arm() {
            armed = true;
        },
        /** What makes the run a failure, empty when the response was held until the selection reached the readiness step. */
        verdict() {
            if (by === undefined) {
                return phase === "idle" ? ["the response was never requested, so nothing was held"] : ["the response is still held"];
            }
            return by === "signal" ? [] : [`the held response was released by ${by}, not by the selection reaching the readiness step`];
        },
        /** The finally block: nothing stays held, and a pending deadline does not outlive the test. */
        drain() {
            if (timer !== undefined) {
                timers.stopTimer(timer);
            }
            release("cleanup");
        },
    };
}

/**
 * Counts the calls of an object's `locator` method whose selector is the loading indicator, which is what the helper's readiness step
 * does first for every variable it is about to use, and reports each one. Returns the function that puts the original back.
 * @param {{ locator: (selector: string, ...rest: unknown[]) => unknown }} prototype @param {() => void} reached @param {string} indicator
 * @returns {() => void}
 */
export function watchReadinessStep(prototype, reached, indicator) {
    const native = prototype.locator;
    prototype.locator = function (/** @type {string} */ selector, /** @type {unknown[]} */ ...rest) {
        if (typeof selector === "string" && selector.includes(indicator)) {
            reached();
        }
        return native.call(this, selector, ...rest);
    };
    return () => {
        prototype.locator = native;
    };
}
