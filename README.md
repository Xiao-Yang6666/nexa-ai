# Nexa AI 平台

AI 网关 SaaS 平台。全栈单仓：`backend/`（Spring Boot）+ `frontend/`（Next.js）。

## 结构

```
.
├── backend/    # Spring Boot 3.2 + Java 21 + PostgreSQL + Flyway
└── frontend/   # Next.js 14 + React + TypeScript + TanStack Query
```

## 后端

技术栈：Spring Boot 3.2、Java 21（虚拟线程）、JPA/Hibernate 6、PostgreSQL、Flyway。

```bash
cd backend
cp .env.example .env        # 填入真实数据库/密钥
# export 环境变量后启动（maven）
mvn spring-boot:run         # 默认 :8080，Flyway 自动建表
mvn test                    # 单元测试
```

环境变量见 `backend/.env.example`（数据库、JWT 密钥、AES 加密密钥、CORS 白名单）。

## 前端

技术栈：Next.js 14、React、TypeScript、TanStack Query。

```bash
cd frontend
cp .env.example .env.local  # 配置 NEXT_PUBLIC_API_BASE 指向后端
npm install
npm run dev                 # 开发 :3100（热重载）
npm run build && npm start  # 生产构建并启动
```

注意：`NEXT_PUBLIC_*` 在 `npm run build` 时固化进 bundle，改环境变量后必须重新 build。

## 安全说明

仓库内不含任何真实凭据。所有密钥、数据库密码、加密密钥均通过环境变量注入，
模板见各自的 `.env.example`。请勿将 `.env` / `.env.local` 提交到仓库。
