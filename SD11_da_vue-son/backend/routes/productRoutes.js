const express = require("express");
const router = express.Router();
const { getPool, sql } = require("../config/db");

// GET /api/products - Lấy danh sách sản phẩm
router.get("/", async (_req, res) => {
  try {
    const pool = await getPool();

    const result = await pool.request().query(`
      SELECT
        id,
        name,
        brand,
        category,
        price,
        old_price AS oldPrice,
        image_url AS imageUrl,
        description,
        sizes_json AS sizesJson,
        badge
      FROM dbo.products
      WHERE is_active = 1
      ORDER BY id ASC
    `);

    const products = result.recordset.map((product) => ({
      ...product,
      sizes: JSON.parse(product.sizesJson || "[39,40,41,42,43]"),
    }));

    res.json({ success: true, products });
  } catch (error) {
    console.error("GET PRODUCTS ERROR:", error);
    res.status(500).json({
      success: false,
      message: "Không thể tải danh sách sản phẩm.",
    });
  }
});

// GET /api/products/:id - Lấy chi tiết một sản phẩm
router.get("/:id", async (req, res) => {
  const id = Number(req.params.id);

  if (!Number.isInteger(id) || id <= 0) {
    return res.status(400).json({
      success: false,
      message: "ID sản phẩm không hợp lệ.",
    });
  }

  try {
    const pool = await getPool();

    const result = await pool
      .request()
      .input("id", sql.Int, id)
      .query(`
        SELECT
          id,
          name,
          brand,
          category,
          price,
          old_price AS oldPrice,
          image_url AS imageUrl,
          description,
          sizes_json AS sizesJson,
          badge
        FROM dbo.products
        WHERE id = @id AND is_active = 1
      `);

    if (!result.recordset.length) {
      return res.status(404).json({
        success: false,
        message: "Không tìm thấy sản phẩm.",
      });
    }

    const product = result.recordset[0];

    res.json({
      success: true,
      product: {
        ...product,
        sizes: JSON.parse(product.sizesJson || "[39,40,41,42,43]"),
      },
    });
  } catch (error) {
    console.error("GET PRODUCT ERROR:", error);
    res.status(500).json({
      success: false,
      message: "Không thể tải chi tiết sản phẩm.",
    });
  }
});

module.exports = router;