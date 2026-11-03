import type { NextConfig } from "next";
import path from "path";

const isProduction = process.env.NODE_ENV === "production";
const backendUrl = process.env.BACKEND_URL || "http://localhost:8081";

if (isProduction && !process.env.BACKEND_URL) {
  console.warn(
    "\n\x1b[33m[WARNING] BACKEND_URL is not defined in production environment!\x1b[0m\n" +
    "Next.js API rewrites will fall back to http://localhost:8081 which may cause API calls to fail on serverless platforms (e.g. Vercel).\n" +
    "Please set BACKEND_URL in your Vercel Project Settings > Environment Variables.\n"
  );
}

const nextConfig: NextConfig = {
  turbopack: {
    root: path.resolve(__dirname),
  },
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
