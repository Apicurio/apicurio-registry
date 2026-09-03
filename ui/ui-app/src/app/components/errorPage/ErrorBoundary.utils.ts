export const shouldResetOnLocationChange = (
    hasError: boolean,
    previousLocation: string | undefined,
    currentLocation: string | undefined
): boolean => {
    if (!hasError || currentLocation === undefined) {
        return false;
    }
    return previousLocation !== currentLocation;
};

const normalizePath = (path: string): string => {
    return path.length > 1 && path.endsWith("/") ? path.slice(0, -1) : path;
};

export const shouldOfferNavigateHome = (currentLocation: string, homeLocation: string): boolean => {
    return normalizePath(currentLocation) !== normalizePath(homeLocation);
};
