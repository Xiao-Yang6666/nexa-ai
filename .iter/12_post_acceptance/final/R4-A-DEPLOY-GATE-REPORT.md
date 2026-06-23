# R4-A 部署门验证报告 ✅ PASS

> 验证时间：2026-06-23
> 基线：本地 main = 8353481（含 R1+R2+R3-01）
> 部署演练库：nexa_deploy（全新空库，Flyway 从头建表）
> 目标：验证"本地代码能否从零部署上线"——这是"明天能上线"的总门槛

## 结论：本地 main 代码具备从零完整部署的能力

之前 prod 容器(8091)起不来，根因已查清：是 prod 库被一条**脱离 git 的野生 V28 迁移**删了 platform_model_mappings 表，导致库状态与代码不一致。**与本地代码质量无关**。本次在干净库验证，本地代码完全自洽。

## 验证步骤与结果

### 1. 后端从零部署 ✅
- 全新空库 nexa_deploy，Flyway 从头跑 **V1→V27 全部迁移成功**（含 V12 create platform_model_mappings，干净库无 V28 漂移）
- 无 schema-validation 错误
- 服务启动成功：`Started NexaApplication in 173.8s`，Tomcat on 8082

### 2. 后端真实业务链路（直连 8082）✅
- `POST /api/user/register` → 200 `register success`
- `POST /api/user/login` → 200，拿 session cookie（role/quota/aff_code 正确）
- `GET /api/user/self`（带 cookie）→ 200
- DB 确认用户真落库（id=1 deploytest）

### 3. 端到端（经前端代理，生产形态）✅
- 前端 next.config rewrites：`/api/* → 后端`（等价生产 nginx 反代）
- 前端关 mock（NEXT_PUBLIC_USE_MOCK=0）
- 经前端 3100 代理：注册 200 / 登录 200 拿 cookie / 鉴权端点 200（cookie 透传正确，id=2 e2euser）

### 4. 前端真实渲染（关 mock 后）✅
- 登录页 SSR 渲染完整表单（标题"登录·Nexa·AI"、邮箱、密码、登录/注册按钮）
- 注册页 200、管理页 200
- 无前端崩溃（"error" 命中仅为 Next.js 框架自带 error-boundary，非真错误）

## 部署门交付物（进 iter/round4）
- `frontend/next.config.js`：加 rewrites 反代后端（BACKEND_ORIGIN 可覆盖，默认 localhost:8080）。这是生产部署必需的真实配置。
- `.env.local`：演练专用（已 gitignore，不进库）

## 上线前剩余事项（非阻塞，记录）
1. **prod 库漂移对齐**：若要复用现有 prod 库部署本地 main，需先把 platform_model_mappings 表按 V12 重建（或用干净库）。用户已决策"以本地为主"。
2. **生产 CORS/反代**：生产用 nginx 反代时 CORS 非问题；若前后端分域，需配 APP_CORS_ALLOWED_ORIGINS。
3. **SECURITY_ENCRYPTION_KEY**：生产必须用固定密钥（演练用临时生成的）。
4. **R4-B**：系统设置 3 个操作按钮接线（进行中）。

## 红线守住
- 全程用独立干净库 nexa_deploy，**未碰 prod 库、未碰 prod 容器**
- 未推生产、未花钱
