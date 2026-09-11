import path from "node:path";
import { fileURLToPath } from "node:url";
import { config } from "dotenv";

const envFilePath = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../.env");
config({ path: envFilePath });

function required(name: string, fallback?: string): string {
  const value = process.env[name];
  if (value) {
    return value;
  }

  if (fallback !== undefined) {
    return fallback;
  }

  if (process.env.NODE_ENV === "production") {
    throw new Error(`Missing required environment variable: ${name}`);
  }

  return "";
}

export const env = {
  appSecret: required("APP_SECRET", "alpha-erp-local-secret-key-2026"),
  isProduction: process.env.NODE_ENV === "production",
  databaseUrl: required("DATABASE_URL", "./data"),
};
