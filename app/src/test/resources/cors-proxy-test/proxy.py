"""Minimal HTTPS reverse proxy for CORS testing. Forwards all requests to http://localhost:8080."""
import http.server
import ssl
import urllib.request
import os

BACKEND = "http://localhost:8080"
LISTEN_PORT = int(os.environ.get("PROXY_PORT", "8443"))
CERT_DIR = os.path.join(os.path.dirname(__file__), "certs")


class ProxyHandler(http.server.BaseHTTPRequestHandler):
    def do_request(self):
        url = BACKEND + self.path
        body = None
        if "Content-Length" in self.headers:
            body = self.rfile.read(int(self.headers["Content-Length"]))

        headers = {k: v for k, v in self.headers.items()
                   if k.lower() not in ("host", "transfer-encoding")}
        headers["X-Forwarded-Proto"] = "https"
        headers["X-Forwarded-Port"] = str(LISTEN_PORT)

        req = urllib.request.Request(url, data=body, headers=headers, method=self.command)
        try:
            with urllib.request.urlopen(req) as resp:
                resp_body = resp.read()
                self.send_response(resp.status)
                for k, v in resp.getheaders():
                    if k.lower() not in ("transfer-encoding",):
                        self.send_header(k, v)
                self.end_headers()
                self.wfile.write(resp_body)
        except urllib.error.HTTPError as e:
            resp_body = e.read()
            self.send_response(e.code)
            for k, v in e.headers.items():
                if k.lower() not in ("transfer-encoding",):
                    self.send_header(k, v)
            self.end_headers()
            self.wfile.write(resp_body)

    do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = do_OPTIONS = do_HEAD = do_request

    def log_message(self, fmt, *args):
        print(f"  [proxy] {self.command} {self.path} -> {args[1] if len(args) > 1 else '?'}")


def main():
    server = http.server.HTTPServer(("0.0.0.0", LISTEN_PORT), ProxyHandler)
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.load_cert_chain(os.path.join(CERT_DIR, "server.crt"),
                        os.path.join(CERT_DIR, "server.key"))
    server.socket = ctx.wrap_socket(server.socket, server_side=True)
    print(f"HTTPS reverse proxy listening on https://localhost:{LISTEN_PORT} -> {BACKEND}")
    server.serve_forever()


if __name__ == "__main__":
    main()
