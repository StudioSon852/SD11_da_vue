export type Product = {
  id: number | string;
  id_product?: number | string;
  name: string;
  product_name?: string;
  brand?: string;
  brand_name?: string;
  category?: string;
  category_name?: string;
  price: number;
  old_price?: number;
  sale_price?: number;
  image?: string;
  image_url?: string;
  description?: string;
  sizes?: number[];
  variants?: Array<Record<string, unknown>>;
  badge?: string;
  sold_count?: number;
  [key: string]: unknown;
};

export type SessionUser = {
  id?: number;
  id_user?: number;
  role_id?: number;
  role?: string;
  email?: string;
  full_name?: string;
  name?: string;
  token?: string;
  [key: string]: unknown;
};
