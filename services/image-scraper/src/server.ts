import express, { Request, Response } from "express";
import cors from "cors";
import * as path from "path";
import * as fs from "fs";
import { Worker } from "worker_threads";
import { SERIALIZABLE_SEARCH_ENGINE, SerializableSearchLocators } from "./scraper/search_const";
import { resizeAndConvert } from "./image-processing";

export const app = express();

app.use(
  cors({
    credentials: true,
    origin: true
  }),
  express.json({ limit: "50mb" })
);

// Health check endpoint (Milestone 1.1 requirement)
app.get("/health", (_req: Request, res: Response) => {
  res.status(200).json({
    status: "ok",
    service: "image-scraper",
    timestamp: new Date().toISOString(),
    uptime: process.uptime()
  });
});

function resolveWorkerPath(): string {
  const candidates = [
    path.join(__dirname, "scraper", "worker.js"),
    path.join(__dirname, "worker.js"),
    path.join(__dirname, "scraper", "worker.ts"),
    path.join(__dirname, "worker.ts")
  ];

  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return path.join(__dirname, "scraper", "worker.js");
}

function processByWorker(
  workerScript: string,
  search_locators: SerializableSearchLocators,
  search_term: string
): { promise: Promise<Uint8Array>; terminate: () => void } {
  // If mock mode is enabled (useful for CI and fast unit tests)
  if (process.env.MOCK_SCRAPER === "true") {
    let timer: NodeJS.Timeout | null = null;
    const promise = new Promise<Uint8Array>((resolve) => {
      timer = setTimeout(async () => {
        // 1x1 transparent PNG converted to WebP
        const samplePng = new Uint8Array([
          137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0,
          1, 8, 6, 0, 0, 0, 31, 21, 196, 137, 0, 0, 0, 10, 73, 68, 65, 84, 120, 156, 99, 0,
          1, 0, 0, 5, 0, 1, 13, 10, 45, 180, 0, 0, 0, 0, 73, 69, 78, 68, 174, 66, 96, 130
        ]);
        const converted = await resizeAndConvert(samplePng);
        resolve(converted);
      }, 50);
    });

    return {
      promise,
      terminate: () => {
        if (timer) clearTimeout(timer);
      }
    };
  }

  let worker: Worker | null = null;
  const promise = new Promise<Uint8Array>((resolve, reject) => {
    try {
      worker = new Worker(workerScript);
      worker.postMessage({ search_locators, search_term });

      worker.on("message", (result: Uint8Array) => {
        worker?.terminate();
        resolve(result);
      });

      worker.on("error", (error) => {
        worker?.terminate();
        reject(new Error(`${search_locators.name} worker error: ${error.message}`));
      });

      worker.on("exit", (code) => {
        if (code !== 0) {
          reject(new Error(`Worker for ${search_locators.name} stopped with exit code ${code}`));
        }
      });
    } catch (err) {
      reject(err);
    }
  });

  return {
    promise,
    terminate: () => {
      worker?.terminate();
    }
  };
}

// Scrape endpoint (Milestone 1.1 requirement: GET /internal/scrape/:searchTerm)
app.get("/internal/scrape/:searchTerm", (req: Request, res: Response) => {
  const rawSearchTerm = req.params.searchTerm;
  const searchTerm = Array.isArray(rawSearchTerm) ? rawSearchTerm[0] : rawSearchTerm;
  if (!searchTerm || typeof searchTerm !== "string" || searchTerm.trim().length === 0) {
    res.status(400).json({ error: "searchTerm parameter is required" });
    return;
  }

  res.writeHead(200, {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache",
    "Connection": "keep-alive",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Cache-Control"
  });

  const workerScript = resolveWorkerPath();
  const terminators: (() => void)[] = [];
  let isClosed = false;

  const cleanup = () => {
    if (isClosed) return;
    isClosed = true;
    for (const terminate of terminators) {
      try {
        terminate();
      } catch {
        // ignore
      }
    }
  };

  req.on("close", () => {
    cleanup();
    if (!res.writableEnded) {
      res.end();
    }
  });

  let completedEngines = 0;
  const totalEngines = SERIALIZABLE_SEARCH_ENGINE.length;

  const checkCompletion = () => {
    if (completedEngines >= totalEngines && !res.writableEnded && !isClosed) {
      res.write(`data: ${JSON.stringify({ type: "complete" })}\n\n`);
      res.end();
      cleanup();
    }
  };

  for (const searchEngine of SERIALIZABLE_SEARCH_ENGINE) {
    const { promise, terminate } = processByWorker(workerScript, searchEngine, searchTerm);
    terminators.push(terminate);

    promise
      .then((result) => {
        if (isClosed || res.writableEnded) return;

        if (result && result.length > 0) {
          const res_base64 = Buffer.from(result).toString("base64");
          res.write(`data: ${JSON.stringify({ engine: searchEngine.name, image: res_base64 })}\n\n`);
        }
        completedEngines++;
        checkCompletion();
      })
      .catch((error) => {
        console.error(`Error scraping ${searchEngine.name}:`, error.message);
        completedEngines++;
        checkCompletion();
      });
  }
});

const PORT = process.env.PORT ? parseInt(process.env.PORT, 10) : 3001;

let serverInstance: any = null;
if (require.main === module) {
  serverInstance = app.listen(PORT, () => {
    console.log(`✅ image-scraper service running on http://localhost:${PORT}`);
  });

  const shutdown = (signal: string) => {
    console.log(`🛑 ${signal} received, closing image-scraper server...`);
    if (serverInstance) {
      serverInstance.close(() => {
        console.log("Image scraper server closed cleanly.");
        process.exit(0);
      });
    } else {
      process.exit(0);
    }
  };

  process.on("SIGTERM", () => shutdown("SIGTERM"));
  process.on("SIGINT", () => shutdown("SIGINT"));
}
