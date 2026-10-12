const { sql, getPool } = require("../config/db");

// ==================== ĐĂNG KÝ ====================
exports.register = async (req, res) => {
  try {
    const full_name = String(req.body.full_name || "").trim();
    const email = String(req.body.email || "")
      .trim()
      .toLowerCase();
    const password = String(req.body.password || "");
    const phone = String(req.body.phone || "").trim();
    const address = String(req.body.address || "").trim();
    const birth_date = req.body.birth_date || null;

    // Kiểm tra dữ liệu bắt buộc
    if (!full_name || !email || !password || !phone) {
      return res.status(400).json({
        success: false,
        message: "Vui lòng nhập họ tên, email, mật khẩu và số điện thoại.",
      });
    }

    if (password.length < 8) {
      return res.status(400).json({
        success: false,
        message: "Mật khẩu cần tối thiểu 8 ký tự.",
      });
    }

    // Kiểm tra định dạng ngày sinh

    // Kiểm tra ngày sinh
    let birthDateForSql = null;

    if (
      req.body.birth_date !== null &&
      req.body.birth_date !== undefined &&
      req.body.birth_date !== ""
    ) {
      const birthDateString = String(req.body.birth_date).trim();
      const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(birthDateString);

      if (!match) {
        return res.status(400).json({
          success: false,
          message: "Ngày sinh phải có định dạng YYYY-MM-DD.",
        });
      }

      const year = Number(match[1]);
      const month = Number(match[2]);
      const day = Number(match[3]);

      // Tạo ngày theo giờ địa phương và kiểm tra ngày hợp lệ
      const parsedDate = new Date(year, month - 1, day);

      if (
        parsedDate.getFullYear() !== year ||
        parsedDate.getMonth() !== month - 1 ||
        parsedDate.getDate() !== day
      ) {
        return res.status(400).json({
          success: false,
          message: "Ngày sinh không hợp lệ.",
        });
      }

      // So sánh ngày, không so sánh giờ
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      parsedDate.setHours(0, 0, 0, 0);

      if (parsedDate > today) {
        return res.status(400).json({
          success: false,
          message: "Ngày sinh không thể nằm trong tương lai.",
        });
      }

      birthDateForSql = new Date(Date.UTC(year, month - 1, day));
    }

    // Sử dụng connection pool dùng chung từ config/db.js
    const pool = await getPool();

    // Kiểm tra email đã tồn tại chưa
    const checkEmail = await pool
      .request()
      .input("email", sql.NVarChar(255), email).query(`
        SELECT TOP 1 email
        FROM dbo.users
        WHERE email = @email
      `);

    if (checkEmail.recordset.length > 0) {
      return res.status(409).json({
        success: false,
        message: "Email đã tồn tại.",
      });
    }

    // Tài khoản đăng ký công khai mặc định là customer
    // Lưu ý: code này giữ cách lưu mật khẩu hiện tại để tương thích dữ liệu cũ.
    await pool
      .request()
      .input("full_name", sql.NVarChar(255), full_name)
      .input("email", sql.NVarChar(255), email)
      .input("password", sql.NVarChar(255), password)
      .input("phone", sql.VarChar(30), phone)
      .input("address", sql.NVarChar(500), address || null)
      .input(
        "birth_date",
        sql.Date,
        birth_date ? new Date(`${birth_date}T00:00:00`) : null,
      )
      .input("role", sql.VarChar(50), "customer").query(`
        INSERT INTO dbo.users
          (full_name, email, password, phone, address, birth_date, role)
        VALUES
          (@full_name, @email, @password, @phone, @address, @birth_date, @role)
      `);

    return res.status(201).json({
      success: true,
      message: "Đăng ký thành công. Vui lòng đăng nhập.",
    });
  } catch (err) {
    console.error("REGISTER ERROR:", err);

    return res.status(500).json({
      success: false,
      message: "Lỗi máy chủ khi đăng ký tài khoản.",
    });
  }
};

// ==================== ĐĂNG NHẬP ====================
exports.login = async (req, res) => {
  try {
    const email = String(req.body.email || "")
      .trim()
      .toLowerCase();
    // Không trim mật khẩu vì khoảng trắng có thể là một phần của mật khẩu.
    const password = String(req.body.password || "");

    if (!email || !password) {
      return res.status(400).json({
        success: false,
        message: "Vui lòng nhập email và mật khẩu.",
      });
    }

    const pool = await getPool();

    const result = await pool
      .request()
      .input("email", sql.NVarChar(255), email)
      .input("password", sql.NVarChar(255), password).query(`
        SELECT TOP 1 *
        FROM dbo.users
        WHERE email = @email AND password = @password
      `);

    if (result.recordset.length === 0) {
      return res.status(401).json({
        success: false,
        message: "Sai tài khoản hoặc mật khẩu.",
      });
    }

    // Không gửi mật khẩu về trình duyệt
    const { password: _password, ...safeUser } = result.recordset[0];

    return res.status(200).json({
      success: true,
      message: "Đăng nhập thành công.",
      user: safeUser,
    });
  } catch (err) {
    console.error("LOGIN ERROR:", err);

    return res.status(500).json({
      success: false,
      message: "Lỗi máy chủ khi đăng nhập.",
    });
  }
};
