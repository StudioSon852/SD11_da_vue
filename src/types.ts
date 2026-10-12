export interface User {
  id?: number | string;
  full_name?: string;
  name?: string;
  email: string;
  phone?: string;
  address?: string;
  birth_date?: string;
  role?: string;
}

export interface Product {
  id: number | string;
  name: string;
  brand: string;
  category: string;
  price: number;
  old_price?: number;
  image: string;
  description: string;
  sizes: number[];
  badge?: string;
}

export interface CartItem {
  product_id: number | string;
  name: string;
  price: number;
  image: string;
  quantity: number;
  size: number;
}
