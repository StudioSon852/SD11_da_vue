const sql = require("mssql");

const config = {
  user: "sa",
  password: "123",
  server: "localhost",
  database: "company_system",

  options: {
    trustServerCertificate: true,
  },
};

module.exports = {
  sql,
  config,
};
