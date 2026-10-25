# SD_11 — FREEDOMS: Logo, variant images, tab sessions, product details, QR

## Changes in this package

1. **Logo and Admin-only wordmark**
   - Replaced `img/logogiay.png` and `public/img/logogiay.png` with the user-provided image.
   - Changed the admin sidebar and admin welcome/logout UI wordmark to `FREEDOMS`.
   - Black background behind the wordmark is scoped to Admin CSS/components; it is not added to the public storefront header.
   - Updated visible POS/admin wording to FREEDOMS for consistency.
   - Note: the supplied image itself visibly contains `pikbest` watermark marks; it was copied as provided, not retouched.

2. **Different tabs have independent authentication**
   - Auth user/JWT values are stored in `sessionStorage`, not `localStorage`.
   - The current tab's logout/401 callback clears only that tab's session.
   - Stale legacy token aliases are removed when the tab no longer has a user record.
   - Shopping cart/theme/order demonstration preferences remain stored according to their existing store logic; this change isolates authentication/session state, not all business data.

3. **Variant/color images**
   - Admin product save sends each color's `image` as `{ color, image }` in `payload.images`.
   - The existing backend `ProductAdminController` writes those rows to `dbo.ProductImages(ProductID, ColorName, ImageURL, ...)`.
   - `ProductController` reads images matched by `ColorName` and returns per-color images on the `colors` collection and each matching size/color variant. Multiple sizes in the same color share that color image; this matches a common shoe-catalog model. If a unique image per exact size+color SKU is required later, the database/API would need a `ProductVariantID` association.

4. **Category/material detail**
   - Product list/detail API queries now include category/material/collection IDs and names.
   - Admin data mapping and the eye/detail modal use these returned names with ID-based fallbacks.
   - Product create/update persists `CategoryID`, `MaterialID`, and `CollectionID`.

5. **QR display**
   - Printed order invoice has a single QR after the totals table, not a duplicate QR at the top and bottom.
   - POS bank-transfer modal shows the payment amount before its single QR so the QR sits under the total.
   - Important: the existing generated QR payload encodes order reference/amount text via a generic QR service. It is not a bank-specific VietQR transfer code until real bank account details and a supported VietQR payload are configured.

## Validation

- `node --check` passed for `src/stores/authStore.js`, `src/services/apiClient.js`, `src/services/httpInterceptor.js`, and `src/views/admin/adminStore.js`.
- Full Vite build and existing tests could not be completed in the packaging environment because dependency installation did not complete (`esbuild` and `vite` were unavailable). Maven was also unavailable in this environment, so no Java compilation or SQL Server integration test is claimed.

## Apply locally

1. Back up your current project and extract this ZIP over a copy of the project.
2. Keep your actual `backend/.env` file; do not replace it with `.env.example`.
3. From the project root run `npm install`, then `npm run build`, `npm test`, and `npm run dev`.
4. In another terminal, if running separately, use `npm run dev:api` (requires JDK 17 and Maven). Check `http://localhost:8080/api/health` before testing Admin CRUD.
5. Test a product with at least two colors and distinct image URLs, save, refresh Admin, open the eye/detail modal, then select each color in the storefront/product detail.
6. Open Admin and Employee as separate fresh tabs and log in independently. Do not duplicate a tab that already has a login session if you want a clean isolated login.
