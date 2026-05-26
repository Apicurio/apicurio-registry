const http = require("http");
const { WebSocketServer } = require("ws");
const pty = require("node-pty");
const path = require("path");
const fs = require("fs");

const PORT = 9090;
const SHELL = process.env.SHELL || "/bin/zsh";

const server = http.createServer((req, res) => {
  if (req.url === "/" || req.url === "/index.html") {
    const file = path.join(__dirname, "odcs-data-contracts-presentation.html");
    res.writeHead(200, { "Content-Type": "text/html" });
    fs.createReadStream(file).pipe(res);
  } else {
    res.writeHead(404);
    res.end();
  }
});

const wss = new WebSocketServer({ server });

wss.on("connection", (ws) => {
  const term = pty.spawn(SHELL, [], {
    name: "xterm-256color",
    cols: 120,
    rows: 30,
    cwd: process.cwd(),
    env: { ...process.env, TERM: "xterm-256color" },
  });

  term.onData((data) => ws.send(data));

  ws.on("message", (msg) => {
    const str = msg.toString();
    try {
      const cmd = JSON.parse(str);
      if (cmd.type === "resize") {
        term.resize(cmd.cols, cmd.rows);
        return;
      }
    } catch {}
    term.write(str);
  });

  ws.on("close", () => term.kill());
});

server.listen(PORT, () => {
  console.log(`Presentation: http://localhost:${PORT}`);
  console.log(`Terminal WebSocket on same port`);
  console.log(`Press Ctrl+C to stop`);
});
