import { describe, it } from "node:test";
import assert from "node:assert";

describe("CloudPool Node.js SDK exports", () => {
  it("should export all modules from dist", async () => {
    const mod = await import("../dist/index.js");
    assert.ok(mod.CloudPool);
    assert.ok(mod.AuthService);
    assert.ok(mod.FilesService);
    assert.ok(mod.DatabaseService);
    assert.ok(mod.VectorService);
    assert.ok(mod.ComputeService);
    assert.ok(mod.NetworkService);
    assert.ok(mod.PaymentsService);
    assert.ok(mod.KvStoreService);
    assert.ok(mod.EmailsService);
  });

  it("should create a client instance with config", async () => {
    const { CloudPool } = await import("../dist/index.js");
    const client = new CloudPool({ apiKey: "test-key" });
    assert.ok(client);
  });
});