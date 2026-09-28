import * as http from "http";
import { app } from "../src/server";
import { resizeAndConvert } from "../src/image-processing";

describe("Image Scraper Service", () => {
  let server: http.Server;
  let baseUrl: string;

  beforeAll((done) => {
    server = app.listen(0, () => {
      const addr = server.address();
      if (addr && typeof addr === "object") {
        baseUrl = `http://localhost:${addr.port}`;
      }
      done();
    });
  });

  afterAll((done) => {
    server.close(done);
  });

  describe("GET /health", () => {
    it("should return 200 with service health info", async () => {
      const response = await fetch(`${baseUrl}/health`);
      expect(response.status).toBe(200);
      const body = await response.json() as any;
      expect(body.status).toBe("ok");
      expect(body.service).toBe("image-scraper");
      expect(typeof body.uptime).toBe("number");
      expect(body.timestamp).toBeDefined();
    });
  });

  describe("GET /internal/scrape/:searchTerm", () => {
    beforeAll(() => {
      process.env.MOCK_SCRAPER = "true";
    });

    afterAll(() => {
      delete process.env.MOCK_SCRAPER;
    });

    it("should return 400 when search term is whitespace", async () => {
      const response = await fetch(`${baseUrl}/internal/scrape/%20%20`);
      expect(response.status).toBe(400);
      const body = await response.json() as any;
      expect(body.error).toBeDefined();
    });

    it("should stream SSE events and finish with complete message", async () => {
      const response = await fetch(`${baseUrl}/internal/scrape/testquery`);
      expect(response.status).toBe(200);
      expect(response.headers.get("content-type")).toContain("text/event-stream");

      const reader = response.body?.getReader();
      expect(reader).toBeDefined();

      let streamData = "";
      const decoder = new TextDecoder();

      while (true) {
        const { done, value } = await reader!.read();
        if (done) break;
        streamData += decoder.decode(value, { stream: true });
      }

      expect(streamData).toContain('"type":"complete"');
      expect(streamData).toContain('"image"');
      expect(streamData).toContain('"engine"');
    });
  });

  describe("image-processing", () => {
    it("should handle empty buffer gracefully", async () => {
      const result = await resizeAndConvert(new Uint8Array());
      expect(result.length).toBe(0);
    });

    it("should convert an image buffer to WebP", async () => {
      const samplePng = new Uint8Array([
        137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0,
        1, 8, 6, 0, 0, 0, 31, 21, 196, 137, 0, 0, 0, 10, 73, 68, 65, 84, 120, 156, 99, 0,
        1, 0, 0, 5, 0, 1, 13, 10, 45, 180, 0, 0, 0, 0, 73, 69, 78, 68, 174, 66, 96, 130
      ]);
      const result = await resizeAndConvert(samplePng);
      expect(result.length).toBeGreaterThan(0);
      // WebP files start with 'RIFF'
      const header = Buffer.from(result.slice(0, 4)).toString("ascii");
      expect(header).toBe("RIFF");
    });
  });
});
