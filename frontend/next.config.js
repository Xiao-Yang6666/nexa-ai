/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // 前端 /api/* 反向代理到后端（生产用 nginx/网关，本地与容器内用 Next rewrites 等价模拟）。
  // 后端地址用 BACKEND_ORIGIN 覆盖；默认指向同机 8080（标准本地后端端口）。
  async rewrites() {
    const backend = process.env.BACKEND_ORIGIN || 'http://localhost:8080';
    return [
      { source: '/api/:path*', destination: `${backend}/api/:path*` },
    ];
  },
};

module.exports = nextConfig;
