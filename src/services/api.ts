import client, { api as sharedApi, API_BASE_URL } from "./apiClient.js";

// Typed facade for the refreshed SD_11 Vue + TypeScript screens.
export type ApiEnvelope<T = unknown> = {
  success?: boolean;
  message?: string;
  data?: T;
  [key: string]: unknown;
};

export const api = sharedApi;
export { API_BASE_URL };
export default client;
