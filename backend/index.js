const crypto = require("crypto");
const express = require("express");
const fs = require("fs");
const path = require("path");
const https = require("https");
const multer = require("multer");
const mysql = require("mysql2/promise");
const checkAuth = require("./middleware/auth");

const app = express();
app.use(express.json({ limit: "1mb" }));
const PORT = process.env.PORT || 443;
const SSL_CERT_PATH = process.env.SSL_CERT_PATH;
const SSL_KEY_PATH = process.env.SSL_KEY_PATH;

const DATA_DIR = path.join(__dirname, "data");
const UPLOADS_DIR = path.join(DATA_DIR, "uploads");
const DB_HOST = process.env.DB_HOST || "mysql";
const DB_PORT = Number(process.env.DB_PORT || 3306);
const DB_USER = process.env.DB_USER || "wildlife";
const DB_PASSWORD = process.env.DB_PASSWORD || "wildlife";
const DB_NAME = process.env.DB_NAME || "wildlife_spotter";

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

async function initDb() {
  ensureDirs();
  const pool = mysql.createPool({
    host: DB_HOST,
    port: DB_PORT,
    user: DB_USER,
    password: DB_PASSWORD,
    database: DB_NAME,
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0,
  });

  await pool.query(
    `CREATE TABLE IF NOT EXISTS images (
      id VARCHAR(64) PRIMARY KEY,
      filename VARCHAR(255) NOT NULL,
      mime VARCHAR(255) NOT NULL,
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      user_id VARCHAR(128)
    )`
  );
  await pool.query(
    "ALTER TABLE images MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"
  );

  return pool;
}

function generateRandomId() {
  return crypto.randomBytes(32).toString("hex");
}

function safeExtension(originalName, mimeType) {
  const ext = path.extname(originalName || "").toLowerCase();
  if (ext) return ext;
  if (mimeType === "image/jpeg") return ".jpg";
  if (mimeType === "image/png") return ".png";
  if (mimeType === "image/webp") return ".webp";
  return "";
}

app.post("/images", checkAuth, upload.single("image"), async (req, res) => {
  try {    
    if (!req.file) {
      res.status(400).json({ error: "Missing image file (field name: image)" });
      return;
    }

    const { buffer, originalname, mimetype } = req.file;
    
    const id = generateRandomId();
    const ext = safeExtension(originalname, mimetype);
    const filename = `${id}${ext}`;
    const filePath = path.join(UPLOADS_DIR, filename);

    const db = req.app.locals.db;
    fs.writeFileSync(filePath, buffer);
    
    await db.execute(
      "INSERT INTO images (id, filename, mime, created_at, user_id) VALUES (?, ?, ?, NOW(), ?)",
      [id, filename, mimetype, req.user.uid]
    );

    res.status(201).json({ id });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Failed to store image" });
  }
});

app.get("/images/:id", async (req, res) => {
  try {
    const { id } = req.params;
    const db = req.app.locals.db;
    const [rows] = await db.execute(
      "SELECT filename, mime FROM images WHERE id = ?",
      [id]
    );
    const row = rows[0];

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

app.get("/images/:id/identify", checkAuth, async (req, res) => {
  try {
    const apiKey = process.env.ANIMALDETECT_API_KEY;
    const apiUrl = "https://www.animaldetect.com/api/v1/detect";

    if (!apiKey) {
      res.status(500).json({ error: "Missing ANIMALDETECT_API_KEY" });
      return;
    }

    const { id } = req.params;
    const db = req.app.locals.db;
    const [rows] = await db.execute(
      "SELECT filename, mime FROM images WHERE id = ?",
      [id]
    );
    const row = rows[0];

    if (!row) {
      res.status(404).json({ error: "Image not found" });
      return;
    }

    const filePath = path.join(UPLOADS_DIR, row.filename);
    if (!fs.existsSync(filePath)) {
      res.status(404).json({ error: "Image file missing" });
      return;
    }

    const buffer = await fs.promises.readFile(filePath);
    
    const formData = new FormData();
    const blob = new Blob([buffer], {
      type: row.mime || "application/octet-stream",
    });
    formData.append("image", blob, row.filename);

    if (req.query.country) {
      formData.append("country", String(req.query.country));
    }

    const response = await fetch(apiUrl, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
      },
      body: formData,
    });

    const text = await response.text();
    if (!response.ok) {
      res.status(response.status).json({ error: "Detect failed", details: text });
      return;
    }

    try {
      res.json(JSON.parse(text));
    } catch {
      res.status(502).json({ error: "Invalid response", details: text });
    }
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Failed to identify image" });
  }
});

app.delete("/images/:id", checkAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const db = req.app.locals.db;
    const [rows] = await db.execute(
      "SELECT filename, user_id FROM images WHERE id = ?",
      [id]
    );
    const row = rows[0];

    if (!row) {
      res.status(404).json({ error: "Image not found" });
      return;
    }

    if (!row.user_id || row.user_id !== req.user.uid) {
      res.status(403).json({ error: "Forbidden: Not image owner" });
      return;
    }

    const filePath = path.join(UPLOADS_DIR, row.filename);
    if (fs.existsSync(filePath)) {
      fs.unlinkSync(filePath);
    }

    await db.execute("DELETE FROM images WHERE id = ?", [id]);

    res.status(200).json({ message: "Image deleted successfully" });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Failed to delete image" });
  }
});

app.get("/health", checkAuth, (req, res) => {
  res.json({ status: "ok" });
});

async function startServer() {
  try {
    const db = await initDb();
    app.locals.db = db;
    if (
      SSL_CERT_PATH &&
      SSL_KEY_PATH &&
      fs.existsSync(SSL_CERT_PATH) &&
      fs.existsSync(SSL_KEY_PATH)
    ) {
      // Start HTTPS if certs are available
      const sslOptions = {
        cert: fs.readFileSync(SSL_CERT_PATH),
        key: fs.readFileSync(SSL_KEY_PATH),
      };
      https.createServer(sslOptions, app).listen(PORT, () => {
        console.log(`Backend listening on https://localhost:${PORT}`);
      });
    } else {
      // Fallback to HTTP when no certs are present
      app.listen(PORT, () => {
        console.log(`Backend listening on http://localhost:${PORT}`);
      });
    }
  } catch (err) {
    console.error("Failed to start server", err);
    process.exit(1);
  }
}

startServer();
