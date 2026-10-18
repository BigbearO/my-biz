const { spawn } = require("node:child_process");

const VITE_READY_PATTERN = /Local:\s+http:\/\/127\.0\.0\.1:\d+/;
const children = [];

/**
 * Spawn an npm command and inherit terminal output.
 *
 * @param {string[]} args npm command arguments.
 * @returns {import("node:child_process").ChildProcess} Spawned process.
 */
function spawnNpm(args) {
  const npmCli = process.env.npm_execpath;
  if (!npmCli) {
    throw new Error("Development script must be started through npm");
  }
  const child = spawn(process.execPath, [npmCli, ...args], {
    stdio: ["ignore", "pipe", "pipe"],
    windowsHide: true
  });
  children.push(child);
  return child;
}

/**
 * Kill the renderer and Electron process trees before script exit.
 *
 * @returns {void}
 */
function stopChildren() {
  for (const child of children) {
    if (child.exitCode === null) {
      if (process.platform === "win32") {
        spawn("taskkill", ["/pid", String(child.pid), "/T", "/F"], { stdio: "ignore" });
      } else {
        child.kill("SIGTERM");
      }
    }
  }
}

const renderer = spawnNpm(["run", "dev:renderer"]);

renderer.stdout.on("data", (chunk) => {
  const text = chunk.toString();
  process.stdout.write(text);
  const readyMatch = text.match(VITE_READY_PATTERN);
  if (readyMatch) {
    const rendererUrl = readyMatch[0].replace(/^Local:\s+/, "").trim();
    process.env.VITE_DEV_SERVER_URL = rendererUrl;
    const electron = spawnNpm(["run", "dev:electron"]);
    electron.stdout.pipe(process.stdout);
    electron.stderr.pipe(process.stderr);
    electron.once("exit", (code) => {
      stopChildren();
      process.exit(code === null ? 1 : code);
    });
  }
});

renderer.stderr.pipe(process.stderr);
renderer.once("exit", (code) => {
  if (code !== 0) {
    stopChildren();
    process.exit(code);
  }
});

process.once("SIGINT", () => {
  stopChildren();
  process.exit(0);
});

