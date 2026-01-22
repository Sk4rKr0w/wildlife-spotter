const crypto = require("crypto");
const express = require("express");
const fs = require("fs");
const path = require("path");
const multer = require("multer");
const Database = require("better-sqlite3");

const app = express();
const PORT = process.env.PORT || 3000;

const DATA_DIR = path.join(__dirname, "data");
const UPLOADS_DIR = path.join(DATA_DIR, "uploads");
const DB_PATH = path.join(DATA_DIR, "images.db");

const storage = multer.memoryStorage();
const upload = multer({
  storage,
  limits: {
    fileSize: 20 * 1024 * 1024, // 20MB max
  },
});

function ensureDirs() {
  if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR);
  }
  if (!fs.existsSync(UPLOADS_DIR)) {
    fs.mkdirSync(UPLOADS_DIR);
  }
}

function openDb() {
  return new Database(DB_PATH);
}

function initDb() {
  ensureDirs();
  const db = openDb();
  db.exec(
    `CREATE TABLE IF NOT EXISTS images (
      id TEXT PRIMARY KEY,
      filename TEXT NOT NULL,
      mime TEXT NOT NULL,
      created_at TEXT NOT NULL
    )`
  );
  return db;
}

function sha256(buffer) {
  return crypto.createHash("sha256").update(buffer).digest("hex");
}

function safeExtension(originalName, mimeType) {
  const ext = path.extname(originalName || "").toLowerCase();
  if (ext) return ext;
  if (mimeType === "image/jpeg") return ".jpg";
  if (mimeType === "image/png") return ".png";
  if (mimeType === "image/webp") return ".webp";
  if (mimeType === "image/gif") return ".gif";
  return "";
}

app.post("/images", upload.single("image"), (req, res) => {
  try {
    if (!req.file) {
      res.status(400).json({ error: "Missing image file (field name: image)" });
      return;
    }

    const { buffer, originalname, mimetype } = req.file;
    const id = sha256(buffer);
    const ext = safeExtension(originalname, mimetype);
    const filename = `${id}${ext}`;
    const filePath = path.join(UPLOADS_DIR, filename);

    const db = req.app.locals.db;
    const existing = db.prepare("SELECT id FROM images WHERE id = ?").get(id);

    if (!existing) {
      fs.writeFileSync(filePath, buffer);
      db.prepare(
        "INSERT INTO images (id, filename, mime, created_at) VALUES (?, ?, ?, ?)"
      ).run(id, filename, mimetype, new Date().toISOString());
    }

    res.status(201).json({ id });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Failed to store image" });
  }
});

app.get("/images/:id", (req, res) => {
  try {
    const { id } = req.params;
    const db = req.app.locals.db;
    const row = db
      .prepare("SELECT filename, mime FROM images WHERE id = ?")
      .get(id);

    if (!row) {
      res.status(404).json({ error: "Image not found" });
      return;
    }

    const filePath = path.join(UPLOADS_DIR, row.filename);
    if (!fs.existsSync(filePath)) {
      res.status(404).json({ error: "Image file missing" });
      return;
    }

    res.setHeader("Content-Type", row.mime);
    res.sendFile(filePath);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Failed to fetch image" });
  }
});

app.get("/health", (req, res) => {
  res.json({ status: "ok" });
});

try {
  const db = initDb();
  app.locals.db = db;
  app.listen(PORT, () => {
    console.log(`Backend listening on http://localhost:${PORT}`);
  });
} catch (err) {
  console.error("Failed to start server", err);
  process.exit(1);
}
