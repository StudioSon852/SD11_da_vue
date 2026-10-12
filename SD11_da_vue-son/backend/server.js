const express = require("express");
const cors = require("cors");
require("dotenv").config();

const app = express();

app.use(
  cors({
    origin: [
      "http://localhost:5173",
      "http://localhost:5174",
      "http://127.0.0.1:5173",
      "http://127.0.0.1:5174",
    ],
    methods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allowedHeaders: ["Content-Type", "Authorization"],
  }),
);

app.use(express.json({ limit: "1mb" }));

// Giữ các route hiện tại của bạn bên dưới phần middleware này.

app.get("/", (_req, res) => res.send("ShoeHub API is running"));
app.get("/api/health", async (_req, res) => {
  try {
    await getPool();
    res.json({ success: true, database: "connected" });
  } catch (error) {
    console.error("DB HEALTH ERROR:", error);
    res.status(503).json({ success: false, database: "disconnected" });
  }
});
app.use("/api/auth", require("./routes/authRoutes"));
app.use("/api/products", require("./routes/productRoutes"));
app.use("/api/orders", require("./routes/orderRoutes"));
app.use((req, res) =>
  res.status(404).json({ success: false, message: "API không tồn tại." }),
);
app.use((error, _req, res, _next) => {
  console.error("SERVER ERROR:", error.message || error);

  if (error.message === "Origin không được phép bởi CORS.") {
    return res.status(403).json({
      success: false,
      message: error.message,
    });
  }

  res.status(500).json({
    success: false,
    message: "Lỗi máy chủ.",
  });
});

const port = Number(process.env.PORT || 3000);
app.listen(port, () =>
  console.log(`ShoeHub backend đang chạy tại http://localhost:${port}`),
);
