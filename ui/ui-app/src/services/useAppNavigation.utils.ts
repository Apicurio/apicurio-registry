// Strips a single trailing "/" so "/registry" and "/registry/" compare equal
// (but leaves a bare "/" alone).
export const normalizePath = (p: string): string => {
    return p !== "/" && p.endsWith("/") ? p.slice(0, -1) : p;
};

// React Router's <Router basename={contextPath}> already prepends contextPath
// to every navigation automatically. If navPrefixPath is also configured with
// a value that overlaps with contextPath -- either exactly (ignoring a
// trailing-slash difference) or as a subpath of it, e.g. navPrefixPath
// "/registry/ui" under contextPath "/registry" -- applying both would
// double-prefix the resulting URL (e.g. "/registry/registry/dashboard" or
// "/registry/registry/ui/..."), which does not match any route. This
// resolves the effective prefix so the portion Router's basename already
// covers is only applied once.
export const effectiveNavPrefixPath = (navPrefixPath: string, contextPath: string): string => {
    const prefix: string = normalizePath(navPrefixPath || "");
    const basename: string = normalizePath(contextPath || "");
    if (prefix === "") {
        return "";
    }
    if (basename !== "" && basename !== "/" && prefix.startsWith(basename)) {
        // Router's basename already covers this portion; only prepend the
        // remainder (if any), so a subpath overlap isn't applied twice.
        return prefix.slice(basename.length);
    }
    return prefix;
};
