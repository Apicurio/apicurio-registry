// Where a Grafana answers, derived from its validated base URL, so a helper that intercepts the datasource endpoint matches the
// endpoint of the Grafana under test (which may be served below a path such as /grafana) and nothing else.

/**
 * The path of the datasource query endpoint of a Grafana: its base path, without a trailing slash, then /api/ds/query.
 * @param {string} grafanaUrl a URL accepted by publicUrl() @returns {string}
 */
export function datasourceQueryPath(grafanaUrl) {
    return `${new URL(grafanaUrl).pathname.replace(/\/+$/, "")}/api/ds/query`;
}

/**
 * Whether a request URL is exactly the datasource query endpoint of this Grafana: same origin, same full path.
 * @param {string} grafanaUrl @param {URL} url @returns {boolean}
 */
export function matchesDatasourceQuery(grafanaUrl, url) {
    return url.origin === new URL(grafanaUrl).origin && url.pathname === datasourceQueryPath(grafanaUrl);
}
