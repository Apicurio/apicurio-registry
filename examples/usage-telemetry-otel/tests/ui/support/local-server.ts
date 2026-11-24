import { createServer, type Server } from "node:http";

/** Harmless markers: the self-tests look for exactly these strings in what a trace stored. No real credential is involved. */
export const MARKERS = {
    password: "PASSWORD_MARKER_VALUE",
    cookie: "COOKIE_MARKER_VALUE",
    header: "HEADER_MARKER_VALUE",
    payload: "PAYLOAD_MARKER_VALUE",
} as const;

/**
 * A throwaway local site that behaves like a login: a form with a password field, a session cookie that JavaScript cannot read
 * (HttpOnly), and a page whose script sends a request header and shows a response. It exists only for the trace self-tests.
 */
export async function startMarkerServer(): Promise<{ url: string; close: () => Promise<void> }> {
    const server: Server = createServer((request, response) => {
        const cookie = request.headers.cookie ?? "";
        if (request.url === "/login") {
            response.setHeader("content-type", "text/html");
            response.end(
                '<form><input id="user" aria-label="user"><input id="password" type="password" aria-label="password">' +
                    '<button id="submit" type="button" onclick="fetch(\'/session\',{method:\'POST\',body:document.getElementById(\'password\').value}).then(()=>{location=\'/home\'})">Log in</button></form>',
            );
        } else if (request.url === "/session") {
            response.setHeader("set-cookie", `session=${MARKERS.cookie}; HttpOnly; Path=/`);
            response.end("ok");
        } else if (request.url === "/home") {
            response.setHeader("content-type", "text/html");
            response.end(
                `<h1>Welcome</h1><div id="data"></div><script>fetch("/api/data",{headers:{"X-Marker":"${MARKERS.header}"}}).then(r=>r.json()).then(j=>{document.getElementById("data").textContent=j.payload})</script>`,
            );
        } else if (request.url === "/api/data") {
            response.setHeader("content-type", "application/json");
            response.end(JSON.stringify({ payload: cookie.includes(MARKERS.cookie) ? MARKERS.payload : "anonymous" }));
        } else {
            response.statusCode = 404;
            response.end();
        }
    });
    await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
    const address = server.address();
    if (address === null || typeof address === "string") {
        throw new Error("the marker server has no port");
    }
    return {
        url: `http://127.0.0.1:${address.port}`,
        close: () => new Promise<void>((resolve) => server.close(() => resolve())),
    };
}

/**
 * A stand-in for the datasource endpoint at the root and below /grafana: it answers every POST to /api/ds/query and to
 * /grafana/api/ds/query with the body it received, so a test can see what a route did to a request. It is not Grafana and not a
 * reverse proxy; it proves how the interception helpers choose the requests they touch.
 */
export async function startQueryEchoServer(): Promise<{ origin: string; close: () => Promise<void> }> {
    const server: Server = createServer((request, response) => {
        const path = request.url ?? "";
        if (request.method === "POST" && (path === "/api/ds/query" || path === "/grafana/api/ds/query")) {
            const chunks: Buffer[] = [];
            request.on("data", (chunk: Buffer) => chunks.push(chunk));
            request.on("end", () => {
                response.setHeader("content-type", "application/json");
                response.end(JSON.stringify({ path, received: Buffer.concat(chunks).toString("utf8") }));
            });
        } else if (path === "/page") {
            response.setHeader("content-type", "text/html");
            response.end("<title>echo</title>");
        } else {
            response.statusCode = 404;
            response.end();
        }
    });
    await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
    const address = server.address();
    if (address === null || typeof address === "string") {
        throw new Error("the echo server has no port");
    }
    return {
        origin: `http://127.0.0.1:${address.port}`,
        close: () => new Promise<void>((resolve) => server.close(() => resolve())),
    };
}

/**
 * A stand-in datasource endpoint whose answer is chosen per request by the x-mode header, for tests of what the query log counts as
 * completed: an empty success, an error inside HTTP 200, HTTP 500, a body and status given by the test (custom), headers followed by a body
 * that is destroyed, and headers followed by a body that never ends. It is not Grafana.
 */
export async function startDatasourceStub(): Promise<{ origin: string; close: () => Promise<void> }> {
    const sockets = new Set<import("node:net").Socket>();
    const server: Server = createServer((request, response) => {
        if (request.method === "POST" && request.url === "/api/ds/query") {
            request.resume();
            request.on("end", () => {
                const mode = request.headers["x-mode"];
                if (mode === "ok-empty") {
                    response.setHeader("content-type", "application/json");
                    response.end(JSON.stringify({ results: { A: { status: 200, frames: [] } } }));
                } else if (mode === "error-in-200") {
                    response.setHeader("content-type", "application/json");
                    response.end(JSON.stringify({ results: { A: { status: 500, error: "boom: the query failed inside HTTP 200", frames: [] } } }));
                } else if (mode === "custom") {
                    response.statusCode = Number(request.headers["x-status"] ?? 200);
                    response.setHeader("content-type", "application/json");
                    response.end(String(request.headers["x-body"] ?? "{}"));
                } else if (mode === "http-500") {
                    response.statusCode = 500;
                    response.end(JSON.stringify({ message: "controlled" }));
                } else if (mode === "destroyed" || mode === "stalled") {
                    response.writeHead(200, { "content-type": "application/json", "content-length": "400" });
                    response.write('{"results":{"A":{"status":200,', () => {
                        if (mode === "destroyed") {
                            request.socket.destroy();
                        }
                    });
                } else {
                    response.statusCode = 400;
                    response.end();
                }
            });
        } else if (request.url === "/page") {
            response.setHeader("content-type", "text/html");
            response.end("<title>stub</title>");
        } else {
            response.statusCode = 404;
            response.end();
        }
    });
    server.on("connection", (socket) => {
        sockets.add(socket);
        socket.on("close", () => sockets.delete(socket));
    });
    await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
    const address = server.address();
    if (address === null || typeof address === "string") {
        throw new Error("the datasource stub has no port");
    }
    return {
        origin: `http://127.0.0.1:${address.port}`,
        close: () =>
            new Promise<void>((resolve) => {
                for (const socket of sockets) {
                    socket.destroy();
                }
                server.close(() => resolve());
            }),
    };
}
