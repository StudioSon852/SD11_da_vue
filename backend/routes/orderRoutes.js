const express = require('express');
const router = express.Router();
const auth = require('../middleware/auth');
const controller = require('../controller/orderController');
router.post('/', auth, controller.create);
module.exports = router;
