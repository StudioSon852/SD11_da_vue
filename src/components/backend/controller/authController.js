const { sql, config } = require("../config/db");

exports.register = async (req, res) => {
  console.log("REGISTER BODY:");
  console.log(req.body);

  try {
    const { full_name, email, password, phone, address, role } = req.body;

    if (!full_name || !email || !password || !phone) {
      return res.status(400).json({
        success: false,
        message: "Thiếu thông tin bắt buộc",
      });
    }

    const pool = await sql.connect(config);

    const checkEmail = await pool.request().input("email", sql.NVarChar, email)
      .query(`
        SELECT *
        FROM users
        WHERE email = @email
      `);

    if (checkEmail.recordset.length > 0) {
      return res.status(400).json({
        success: false,
        message: "Email đã tồn tại",
      });
    }

    await pool
      .request()
      .input("full_name", sql.NVarChar, full_name)
      .input("email", sql.NVarChar, email)
      .input("password", sql.NVarChar, password)
      .input("phone", sql.VarChar, phone)
      .input("address", sql.NVarChar, address)
      .input("role", sql.VarChar, role).query(`
        INSERT INTO users
        (
          full_name,
          email,
          password,
          phone,
          address,
          role
        )
        VALUES
        (
          @full_name,
          @email,
          @password,
          @phone,
          @address,
          @role
        )
      `);

    return res.json({
      success: true,
      message: "Đăng ký thành công",
    });
  } catch (err) {
    console.log("REGISTER ERROR:");
    console.log(err);

    return res.status(500).json({
      success: false,
      message: err.message,
    });
  }
};

exports.login = async (req, res) => {
  try {
    const email = req.body.email?.trim();
    const password = req.body.password?.trim();

    const pool = await sql.connect(config);

    const result = await pool
      .request()
      .input("email", sql.NVarChar, email)
      .input("password", sql.NVarChar, password).query(`
        SELECT *
        FROM users
        WHERE email = @email
        AND password = @password
      `);

    if (result.recordset.length === 0) {
      return res.status(401).json({
        success: false,
        message: "Sai tài khoản hoặc mật khẩu",
      });
    }

    return res.json({
      success: true,
      user: result.recordset[0],
    });
  } catch (err) {
    console.log(err);

    return res.status(500).json({
      success: false,
      message: err.message,
    });
  }
};
