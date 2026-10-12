const { pool } = require("../config/db");
exports.create = async (req, res) => {
  const customerName = String(req.body.customer_name || "").trim();
  const phone = String(req.body.phone || "").trim();
  const shippingAddress = String(req.body.shipping_address || "").trim();
  const note = String(req.body.note || "").trim() || null;
  const items = req.body.items;
  if (
    !customerName ||
    !phone ||
    !shippingAddress ||
    !Array.isArray(items) ||
    items.length === 0
  )
    return res
      .status(400)
      .json({
        success: false,
        message: "Vui lòng nhập đủ thông tin giao hàng và sản phẩm.",
      });
  if (items.length > 50)
    return res
      .status(400)
      .json({ success: false, message: "Đơn hàng có quá nhiều sản phẩm." });
  let conn;
  try {
    conn = await pool.getConnection();
    await conn.beginTransaction();
    let total = 0;
    const normalized = [];
    for (const item of items) {
      const productId = Number(item.product_id),
        quantity = Number(item.quantity),
        size = Number(item.size);
      if (
        !Number.isInteger(productId) ||
        productId < 1 ||
        !Number.isInteger(quantity) ||
        quantity < 1 ||
        quantity > 10 ||
        !Number.isFinite(size)
      )
        throw Object.assign(
          new Error("Thông tin sản phẩm trong đơn hàng không hợp lệ."),
          { statusCode: 400 },
        );
      const [rows] = await conn.execute(
        "SELECT id,name,price,sizes_json FROM products WHERE id = ? AND is_active = 1 LIMIT 1",
        [productId],
      );
      if (!rows.length)
        throw Object.assign(
          new Error(`Không tìm thấy sản phẩm mã ${productId}.`),
          { statusCode: 400 },
        );
      const product = rows[0];
      let sizes = [];
      try {
        sizes =
          typeof product.sizes_json === "string"
            ? JSON.parse(product.sizes_json)
            : product.sizes_json;
      } catch {
        sizes = [];
      }
      if (!Array.isArray(sizes) || !sizes.includes(size))
        throw Object.assign(
          new Error(`Size ${size} không hợp lệ cho ${product.name}.`),
          { statusCode: 400 },
        );
      const price = Number(product.price);
      total += price * quantity;
      normalized.push({ productId, name: product.name, price, quantity, size });
    }
    const [orderResult] = await conn.execute(
      "INSERT INTO orders (user_id, customer_name, phone, shipping_address, note, total, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
      [
        req.user.id,
        customerName,
        phone,
        shippingAddress,
        note,
        total,
        "pending",
      ],
    );
    const orderId = orderResult.insertId;
    for (const item of normalized)
      await conn.execute(
        "INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity, shoe_size) VALUES (?, ?, ?, ?, ?, ?)",
        [
          orderId,
          item.productId,
          item.name,
          item.price,
          item.quantity,
          item.size,
        ],
      );
    await conn.commit();
    return res
      .status(201)
      .json({
        success: true,
        message: "Đặt hàng thành công.",
        order_id: orderId,
        total,
      });
  } catch (error) {
    if (conn) await conn.rollback().catch(() => {});
    console.error("CREATE ORDER ERROR:", error);
    return res
      .status(error.statusCode || 500)
      .json({
        success: false,
        message: error.statusCode
          ? error.message
          : "Không thể tạo đơn hàng lúc này.",
      });
  } finally {
    if (conn) conn.release();
  }
};
