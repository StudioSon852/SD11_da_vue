const { pool } = require("../config/db");
function normalize(row) {
  let sizes = [39, 40, 41, 42, 43];
  try {
    sizes =
      typeof row.sizes_json === "string"
        ? JSON.parse(row.sizes_json)
        : row.sizes_json;
  } catch {
    /* use defaults */
  }
  return {
    id: row.id,
    name: row.name,
    brand: row.brand,
    category: row.category,
    price: Number(row.price),
    old_price: row.old_price == null ? null : Number(row.old_price),
    image: row.image_url,
    description: row.description,
    sizes: Array.isArray(sizes) ? sizes : [39, 40, 41, 42, 43],
    badge: row.badge || undefined,
  };
}
exports.list = async (_req, res) => {
  try {
    const [rows] = await pool.execute(
      "SELECT id,name,brand,category,price,old_price,image_url,description,sizes_json,badge FROM products WHERE is_active = 1 ORDER BY id",
    );
    return res.json({ products: rows.map(normalize) });
  } catch (error) {
    console.error("PRODUCT LIST ERROR:", error);
    return res
      .status(500)
      .json({ success: false, message: "Không tải được danh sách sản phẩm." });
  }
};
exports.detail = async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id) || id < 1)
    return res
      .status(400)
      .json({ success: false, message: "Mã sản phẩm không hợp lệ." });
  try {
    const [rows] = await pool.execute(
      "SELECT id,name,brand,category,price,old_price,image_url,description,sizes_json,badge FROM products WHERE id = ? AND is_active = 1 LIMIT 1",
      [id],
    );
    if (!rows.length)
      return res
        .status(404)
        .json({ success: false, message: "Không tìm thấy sản phẩm." });
    return res.json({ product: normalize(rows[0]) });
  } catch (error) {
    console.error("PRODUCT DETAIL ERROR:", error);
    return res
      .status(500)
      .json({ success: false, message: "Không tải được sản phẩm." });
  }
};
