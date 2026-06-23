# SLICE R2-04: 修流式 SSE 真实链路 500（StreamingResponseBody 写出层 bug）

## 严重程度
P0 — 流式请求（stream:true）在真实 HTTP 链路下 100% 返回 500，R2-01 修的计费逻辑根本跑不到。
单测全绿是因为单测 mock 了 useCase，没走真实 Spring MVC 返回值处理器。S11 端到端复验才暴露。

## 根因（已定位，勿重新推翻）
文件：`backend/nexa-service/src/main/java/com/nexa/relay/interfaces/api/RelayController.java`

第 215-235 行 `forwardRelay(...)` 方法返回类型是 `ResponseEntity<?>`。流式分支（224-230 行）：
```java
StreamingResponseBody stream = out -> useCase.forwardStream(path, body, authContext, out);
return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
        .body(stream);
```
返回的 `ResponseEntity` body 装的是 `StreamingResponseBody` Lambda。但因为方法签名是 `ResponseEntity<?>`（泛型擦除成通配），Spring MVC 的 `ResponseEntityReturnValueHandler` 取出 body 后**走 HttpMessageConverter 链**去序列化它，而没有任何 converter 能把一个 Lambda 写成 `text/event-stream`，于是抛：
```
HttpMessageNotWritableException: No converter for [class ...RelayController$$Lambda...] with preset Content-Type 'text/event-stream'
```

`videoContent`（194 行）也用了同样的 `ResponseEntity.ok().body(streamingResponseBody)` 写法，同样的隐患（只是没被流式请求触发过）。

## 复现（必须先复现再修）
1. 服务已在 8080 跑（连 sub2 PG 5434 + Redis 6380），mock 流式上游在 172.17.0.1:18080（`/tmp/mock_upstream_stream.py`，支持 SSE）。
2. 测试数据：token key = `sk-e2e-test-token-0001`，model = `gpt-test`（映射到 mock-model-b）。
3. 复现命令：
```bash
curl -s -N -X POST "http://localhost:8080/v1/chat/completions" \
  -H "Authorization: Bearer sk-e2e-test-token-0001" \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-test","messages":[{"role":"user","content":"hi"}],"stream":true}'
```
当前返回 `{"success":false,"message":"internal server error"}`，应返回 SSE 流（data: {...} 多块 + data: [DONE]）。

## 修复方向（自行判断最佳实现，以下是建议）
让流式响应走 Spring 的 `StreamingResponseBodyReturnValueHandler` 而不是消息转换器。可选方案：
- **方案 A（推荐）**：把流式分支抽到一个返回类型明确为 `ResponseEntity<StreamingResponseBody>` 的私有方法/独立处理，让 Spring 识别 body 类型走 SRB handler。注意 controller 各端点方法签名是 `ResponseEntity<?>`，需要确认抽出后泛型不再是通配。
- **方案 B**：流式端点单独用 `SseEmitter` 或 `ResponseBodyEmitter` 返回类型。
- **方案 C**：直接拿 `HttpServletResponse` 注入，手动 `getOutputStream()` 写 SSE（绕过返回值处理器）。

选哪个都行，但必须满足：
1. 真实 curl 流式请求返回 200 + 正确 SSE 流（不是 500）。
2. 非流式请求（stream:false）行为不变，仍返回完整 JSON。
3. R2-01 的计费逻辑（forwardStream 内 billStreamConsume）真实跑到，流结束后 logs 表落一条 `is_stream=true` 且 prompt_tokens/completion_tokens/quota 非零的记录。
4. videoContent 端点若同隐患，一并修（同样的 SRB 写出问题）。

## 验收（你必须自己跑到绿再 commit）
1. `cd backend && mvn -pl nexa-service -am test -Dsurefire.failIfNoSpecifiedTests=false` 全绿（当前 512 测试，不准减少）。
2. 真实 curl 流式请求返回 SSE 200。
3. 跑完流式请求后，查 logs 表确认落了 is_stream=true 的计费记录：
   `ssh sub2 "docker exec nexa-pg-test psql -U postgres -d nexa -c \"SELECT id,is_stream,prompt_tokens,completion_tokens,quota FROM logs WHERE is_stream=true ORDER BY id DESC LIMIT 3;\""`
   必须看到至少一条 is_stream=t 且 token 数非零。
4. 重起服务用 `bash /root/.hermes/scripts/nexa-backend-start.sh`（后台）。

## 禁区
- 不碰 RelayForwardUseCase 的计费逻辑（R2-01 已验证正确，只修接口层写出）。
- 不动选渠/鉴权。
- 不 push origin、不合 main、不动生产。
- 你有 mvn/npm/git 权限（settings.json allowlist），自测跑到绿 + git commit 是你的职责，不准报 blocked-on-permission。

## 完成标记
最后一行输出：`DRIVER_ITEM_DONE: R2-04` + commit hash。
卡住则：`DRIVER_ITEM_BLOCKED: R2-04 <原因>`。
