const STORAGE_PREFIX = "apicurio-registry.";

function createStorageKey(propertyName: string): string {
    return STORAGE_PREFIX + propertyName;
}

function isQuotaExceededError(error: unknown): boolean {
    return error instanceof DOMException && (
        error.name === "QuotaExceededError" ||
        error.name === "NS_ERROR_DOM_QUOTA_REACHED"
    );
}

function setConfigProperty(propertyName: string, propertyValue: string | object): boolean {
    console.info(`[LocalStorageService] Setting config property ${propertyName} to value ${propertyValue}.`);
    const value: string = typeof propertyValue === "string" ? propertyValue : JSON.stringify(propertyValue);
    try {
        localStorage.setItem(createStorageKey(propertyName), value);
        return true;
    } catch (error) {
        if (isQuotaExceededError(error)) {
            console.warn(`[LocalStorageService] Unable to persist ${propertyName}: browser storage quota exceeded.`);
            return false;
        }
        throw error;
    }
}

function getConfigProperty<T extends string | object>(
    propertyName: string,
    defaultValue: T | undefined
): T | undefined {
    console.info(`[LocalStorageService] Getting config property ${propertyName}`);
    const value = localStorage.getItem(createStorageKey(propertyName));
    if (value === null) {
        return defaultValue;
    }

    try {
        const parsedValue: unknown = JSON.parse(value);
        // Only JSON objects are stored through this path; parsed primitives should still be treated as plain strings.
        if (parsedValue !== null && typeof parsedValue === "object") {
            return parsedValue as T;
        }
    } catch { /* empty */ }

    return value as T;
}

function clearConfigProperty(propertyName: string): void {
    console.info(`[LocalStorageService] Clearing config property ${propertyName}`);
    localStorage.removeItem(createStorageKey(propertyName));
}

/**
 * The Local Storage Service interface.
 */
export interface LocalStorageService {
    setConfigProperty(propertyName: string, propertyValue: string | object): boolean;
    getConfigProperty<T extends string | object>(propertyName: string, defaultValue: T | undefined): T | undefined;
    clearConfigProperty(propertyName: string): void;
}

const LOCAL_STORAGE_SERVICE: LocalStorageService = {
    setConfigProperty,
    getConfigProperty,
    clearConfigProperty
};

/**
 * React hook to get the LocalStorage service.
 * Returns a stable singleton so consumers can safely use it in effect dependencies.
 */
export const useLocalStorageService: () => LocalStorageService = (): LocalStorageService => {
    return LOCAL_STORAGE_SERVICE;
};
