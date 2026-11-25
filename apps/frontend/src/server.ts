import { createServer } from "node:http";
// Plain JS module, so Sonar and Coverity see both TS and JS in this app.
import { greeting } from "./greeting.js";

const port = Number(process.env.PORT ?? 8080);

const server = createServer((_req, res) => {
  res.writeHead(200, { "Content-Type": "text/plain; charset=utf-8" });
  res.end(greeting(process.env.GREETING_NAME));
});

server.listen(port, () => {
  console.log(`frontend listening on :${port}`);
});
