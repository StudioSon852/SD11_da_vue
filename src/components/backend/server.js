const express = require("express");
const cors = require("cors");

const app = express();

app.use(cors());
app.use(express.json());

app.get("/", (req, res) => {
  res.send("Backend Running");
});

app.post("/api/auth/login", (req, res) => {
  console.log(req.body);

  res.json({
    success: true,
    message: "Login Success",
  });
});

app.listen(3000, () => {
  console.log("Server running port 3000");
});
