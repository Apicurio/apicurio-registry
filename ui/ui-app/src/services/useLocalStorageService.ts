const STORAGE_PREFIX = "apicurio-registry.";
const CONFIG_STORAGE_PREFIX = "config.";
const SNAPSHOT_STORAGE_PREFIX = "snapshot.";

function createStorageKey(namespacePrefix: string, key: string): string {
    return STORAGE_PREFIX + namespacePrefix + key;
}

function isQuotaExceededError(error: unknown): boolean {
    return error instanceof DOMException && (
        error.name === "QuotaExceededError" ||
        error.name === "NS_ERROR_DOM_QUOTA_REACHED"
    );
}

function setConfigProperty(propertyName: string, propertyValue: string): boolean {
    console.info(`[LocalStorageService] Setting config property ${propertyName} to value ${propertyValue}.`);
    try {
        localStorage.setItem(createStorageKey(CONFIG_STORAGE_PREFIX, propertyName), propertyValue);
        return true;
    } catch (error) {
        if (isQuotaExceededError(error)) {
            console.warn(`[LocalStorageService] Unable to persist ${propertyName}: browser storage quota exceeded.`);
            return false;
        }
        throw error;
    }
}

function getConfigProperty(
    propertyName: string,
    defaultValue: string | undefined
): string | undefined {
    console.info(`[LocalStorageService] Getting config property ${propertyName}`);
    const value = localStorage.getItem(createStorageKey(CONFIG_STORAGE_PREFIX, propertyName));
    if (value === null) {
        return defaultValue;
    }
    return value;
}

function clearConfigProperty(propertyName: string): void {
    console.info(`[LocalStorageService] Clearing config property ${propertyName}`);
    localStorage.removeItem(createStorageKey(CONFIG_STORAGE_PREFIX, propertyName));
}

function storeSnapshot<T extends object>(snapshotKey: string, snapshot: T): boolean {
    console.info(`[LocalStorageService] Storing snapshot ${snapshotKey}.`);
    try {
        localStorage.setItem(createStorageKey(SNAPSHOT_STORAGE_PREFIX, snapshotKey), JSON.stringify(snapshot));
        return true;
    } catch (error) {
        if (isQuotaExceededError(error)) {
            console.warn(`[LocalStorageService] Unable to persist snapshot ${snapshotKey}: browser storage quota exceeded.`);
            return false;
        }
        throw error;
    }
}

function loadSnapshot<T extends object>(snapshotKey: string): T | undefined {
    console.info(`[LocalStorageService] Loading snapshot ${snapshotKey}`);
    const value = localStorage.getItem(createStorageKey(SNAPSHOT_STORAGE_PREFIX, snapshotKey));
    if (value === null) {
        return undefined;
    }

    try {
        const parsedValue: unknown = JSON.parse(value);
        if (parsedValue !== null && typeof parsedValue === "object") {
            return parsedValue as T;
        }
    } catch { /* empty */ }

    console.warn(`[LocalStorageService] Ignoring malformed snapshot ${snapshotKey}.`);
    return undefined;
}

function clearSnapshot(snapshotKey: string): void {
    console.info(`[LocalStorageService] Clearing snapshot ${snapshotKey}`);
    localStorage.removeItem(createStorageKey(SNAPSHOT_STORAGE_PREFIX, snapshotKey));
}

/**
 * The Local Storage Service interface.
 */
export interface LocalStorageService {
    setConfigProperty(propertyName: string, propertyValue: string): boolean;
    getConfigProperty(propertyName: string, defaultValue: string | undefined): string | undefined;
    clearConfigProperty(propertyName: string): void;
    storeSnapshot<T extends object>(snapshotKey: string, snapshot: T): boolean;
    loadSnapshot<T extends object>(snapshotKey: string): T | undefined;
    clearSnapshot(snapshotKey: string): void;
}

const LOCAL_STORAGE_SERVICE: LocalStorageService = {
    setConfigProperty,
    getConfigProperty,
    clearConfigProperty,
    storeSnapshot,
    loadSnapshot,
    clearSnapshot
};

/**
 * React hook to get the LocalStorage service.
 * Returns a stable singleton so consumers can safely use it in effect dependencies.
 */
export const useLocalStorageService: () => LocalStorageService = (): LocalStorageService => {
    return LOCAL_STORAGE_SERVICE;
};
