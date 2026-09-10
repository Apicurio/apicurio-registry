export type LoaderCallback<T> = (value: T) => void;

export interface LoaderGuard {
    wrap: <T>(callback: LoaderCallback<T>) => LoaderCallback<T>;
    cancel: () => void;
}

export function newLoaderGuard(): LoaderGuard {
    let cancelled = false;
    return {
        wrap: <T>(callback: LoaderCallback<T>): LoaderCallback<T> => {
            return (value: T): void => {
                if (!cancelled) {
                    callback(value);
                }
            };
        },
        cancel: (): void => {
            cancelled = true;
        }
    };
}
