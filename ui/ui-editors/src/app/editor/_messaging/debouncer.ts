export interface DebouncerOptions {
    period?: number;
}

/**
 * A simple debouncer class.  Used to convert a stream of values into a less
 * granular stream of values.
 */
export class Debouncer<T> {

    private options: DebouncerOptions;
    private callback: (value: T) => void;
    private currentValue: T;
    private timeoutId: ReturnType<typeof setTimeout>;

    constructor(options: DebouncerOptions, callback: (value: T) => void) {
        this.options = options;
        this.callback = callback;
    }

    /**
     * Gets the current value.
     */
    getValue(): T {
        return this.currentValue;
    }

    /**
     * Send a value.
     */
    emit(value: T): void {
        this.currentValue = value;
        const period = this.options.period || 250;

        // Clear any outstanding timeout.
        if (this.timeoutId) {
            clearTimeout(this.timeoutId);
        }

        // Emit the current value in the future
        this.timeoutId = setTimeout(() => {
            this.callback(this.currentValue);
        }, period);
    }
}
