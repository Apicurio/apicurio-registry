const http = require("http");
const { spawn } = require("child_process");
const path = require("path");
const fs = require("fs");

const PRESENTATION_PORT = 9090;
const TTYD_PORT = 7681;
const SHELL = process.env.SHELL || "/bin/zsh";

const DEMO_DIR = path.resolve(__dirname, "../../examples/odcs-data-contracts");

const ttyd = spawn("ttyd", [
  "--port", String(TTYD_PORT),
  "--writable",
  "--base-path", "/terminal",
  "--cwd", DEMO_DIR,
  SHELL
], { stdio: "inherit" });

ttyd.on("error", (err) => {
  console.error("Failed to start ttyd:", err.message);
  console.error("Install with: brew install ttyd");
  process.exit(1);
});

const server = http.createServer((req, res) => {
  if (req.url === "/" || req.url.startsWith("/#")) {
    const file = path.join(__dirname, "odcs-data-contracts-presentation.html");
    let html = fs.readFileSync(file, "utf8");
    html = html.replace("__TTYD_URL__", `http://localhost:${TTYD_PORT}/terminal/`);
    res.writeHead(200, { "Content-Type": "text/html" });
    res.end(html);
  } else {
    res.writeHead(404);
    res.end();
  }
});

server.listen(PRESENTATION_PORT, () => {
  console.log(`Presentation: http://localhost:${PRESENTATION_PORT}`);
  console.log(`Terminal (ttyd): http://localhost:${TTYD_PORT}/terminal/`);
  console.log(`Press Ctrl+C to stop`);
});

process.on("SIGINT", () => {
  ttyd.kill();
  process.exit();
});
