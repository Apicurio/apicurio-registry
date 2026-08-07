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
