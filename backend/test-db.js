require("dotenv").config();

const { sql, config, getPool } = require("./config/db");

async function testConnection() {
  try {
    console.log("Dang kiem tra SQL Server...");
    console.log("Server:", config.server);
    console.log("Database:", config.database);

    const pool = await getPool();

    const result = await pool.request().query(`
      SELECT
        @@SERVERNAME AS server_name,
        DB_NAME() AS database_name,
        SUSER_SNAME() AS login_name
    `);

    console.log("KET NOI THANH CONG:");
    console.log(result.recordset[0]);
  } catch (err) {
    console.error("KET NOI THAT BAI");
    console.error("Code:", err.code);
    console.error("Message:", err.message);
    console.error("Original:", err.originalError?.message);
  } finally {
    await sql.close().catch(() => {});
  }
}

testConnection();
