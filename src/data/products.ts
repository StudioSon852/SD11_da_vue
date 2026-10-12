import type { Product } from "../types";

// Dữ liệu mẫu để giao diện vẫn hiển thị khi backend chưa có GET /products.
export const sampleProducts: Product[] = [
  {
    id: 1,
    name: "Nike Air Max Pulse",
    brand: "Nike",
    category: "Lifestyle",
    price: 2890000,
    old_price: 3390000,
    image:
      "https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=85",
    description:
      "Thiết kế năng động, đệm êm phù hợp đi học, đi làm và dạo phố.",
    sizes: [39, 40, 41, 42, 43],
    badge: "Bán chạy",
  },
  {
    id: 2,
    name: "Adidas Runfalcon 3.0",
    brand: "Adidas",
    category: "Chạy bộ",
    price: 1590000,
    old_price: 1890000,
    image:
      "https://images.unsplash.com/photo-1608231387042-66d1773070a5?auto=format&fit=crop&w=900&q=85",
    description: "Giày chạy bộ nhẹ, thoáng khí, phù hợp luyện tập hằng ngày.",
    sizes: [38, 39, 40, 41, 42, 43],
    badge: "Giảm giá",
  },
  {
    id: 3,
    name: "New Balance 574 Classic",
    brand: "New Balance",
    category: "Lifestyle",
    price: 2490000,
    image:
      "https://images.unsplash.com/photo-1539185441755-769473a23570?auto=format&fit=crop&w=900&q=85",
    description:
      "Phong cách cổ điển, phối đồ dễ dàng và thoải mái khi di chuyển.",
    sizes: [39, 40, 41, 42, 43, 44],
  },
  {
    id: 4,
    name: "Converse Chuck Taylor",
    brand: "Converse",
    category: "Thời trang",
    price: 1350000,
    image:
      "https://images.unsplash.com/photo-1495555961986-6d4c1ecb7be3?auto=format&fit=crop&w=900&q=85",
    description: "Kiểu dáng biểu tượng, tối giản và phù hợp nhiều phong cách.",
    sizes: [36, 37, 38, 39, 40, 41, 42],
  },
  {
    id: 5,
    name: "Nike Court Vision Low",
    brand: "Nike",
    category: "Thời trang",
    price: 1990000,
    old_price: 2290000,
    image:
      "https://images.unsplash.com/photo-1552346154-21d32810aba3?auto=format&fit=crop&w=900&q=85",
    description: "Cảm hứng bóng rổ cổ điển trong kiểu dáng thấp hiện đại.",
    sizes: [39, 40, 41, 42, 43],
    badge: "Mới",
  },
  {
    id: 6,
    name: "Puma Flyer Runner",
    brand: "Puma",
    category: "Chạy bộ",
    price: 1190000,
    image:
      "https://images.unsplash.com/photo-1460353581641-37baddaabfd3?auto=format&fit=crop&w=900&q=85",
    description:
      "Linh hoạt, nhẹ và thích hợp cho các buổi vận động thường ngày.",
    sizes: [39, 40, 41, 42, 43, 44],
  },
  {
    id: 7,
    name: "Vans Old Skool",
    brand: "Vans",
    category: "Thời trang",
    price: 1690000,
    image:
      "https://images.unsplash.com/photo-1525966222134-fcfa99b8ae77?auto=format&fit=crop&w=900&q=85",
    description:
      "Thiết kế đường phố đặc trưng, dễ kết hợp với trang phục casual.",
    sizes: [36, 37, 38, 39, 40, 41, 42],
  },
  {
    id: 8,
    name: "Asics Gel-Contend",
    brand: "Asics",
    category: "Chạy bộ",
    price: 1790000,
    image:
      "https://images.unsplash.com/photo-1539185441755-769473a23570?auto=format&fit=crop&w=900&q=85",
    description: "Đệm êm và hỗ trợ ổn định cho các buổi chạy nhẹ.",
    sizes: [39, 40, 41, 42, 43],
  },
];
