/**
 * Safely derives the origin from a configured URL, handling potential malformed URLs gracefully.
 * @param url The configured URL (e.g. from config.js)
 * @param fallbackOrigin The origin to fallback to if the URL is relative (usually window.location.origin)
 * @returns The parsed origin, or undefined if parsing fails
 */
export const deriveOrigin = (url: string | undefined | null, fallbackOrigin: string): string | undefined => {
    if (!url) {
        return undefined;
    }
    try {
        return new URL(url, fallbackOrigin).origin;
    } catch (e) {
        console.error("[url.utils] Failed to parse origin from URL: ", url, e);
        return undefined;
    }
};
