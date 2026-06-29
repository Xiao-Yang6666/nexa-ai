# Repository Guidelines

Nexa AI is an AI gateway SaaS platform. Single repo, two halves: `backend/` (Spring Boot) and `frontend/` (Next.js).

## Project Structure & Module Organization

`backend/` is a Maven reactor with two modules: `nexa-common` (shared kernel, unified response, global exception handling, security primitives) and `nexa-service` (the business Spring Boot app, which depends on `nexa-common`).

`nexa-service` is organized by **bounded context, not by technical layer**. Each domain under `com.nexa.<domain>` (e.g. `account`, `relay`, `billing`, `model`, `routing`) is sliced into four DDD layers:
- `domain/` — `model`, `vo`, `repository` (interfaces), `event`, `exception`, `service`, `port`
- `application/` — one class per use case (`XxxUseCase` + paired `XxxCommand`/`XxxResult`)
- `infrastructure/` — `persistence` (repository impls), `config`, `messaging`, plus domain-specific adapters
- `interfaces/api/` — REST controllers and their `dto/`

Cross-domain shared persistence helpers live in `com.nexa.shared.persistence` (`JsonbCodec`, `PageQueries`). `relay` is the gateway core (protocol translation, upstream forwarding); it adds `domain/protocol`, `domain/ir`, and `infrastructure/upstream`.

`frontend/src/` uses path alias `@/*`. Routes are App Router groups under `app/` (`(admin)`, `(console)`, `(docs)`, `(public)`). Domain logic lives in `features/<name>/` (each with `api`, `components`, `model`, `index.ts`); cross-cutting code in `shared/` (`api`, `ui`).

## Build, Test, and Development Commands

```bash
# Backend (requires JDK 21) — run from backend/
cp .env.example .env                          # fill DB / JWT / AES keys, then export
mvn -pl nexa-service -am spring-boot:run      # starts :8080, Flyway auto-migrates
mvn test                                       # full reactor unit tests
mvn -pl nexa-service test -Dtest=LoginUseCaseTest   # single test class

# Frontend — run from frontend/
cp .env.example .env.local                    # set NEXT_PUBLIC_API_BASE
npm install
npm run dev                                    # :3100, hot reload
npm run lint
npm run build && npm start                     # NEXT_PUBLIC_* are baked at build time — rebuild after changing them
```

DB migrations: add `V<n>__description.sql` to `nexa-service/src/main/resources/db/migration`; out-of-order migrations are permitted.

## Coding Style & Naming Conventions

Backend targets Java 21 (virtual threads, `maven.compiler.release=21`), UTF-8 source. Keep domain code free of framework/persistence imports; put adapters in `infrastructure/`. Domain exceptions extend `shared/kernel/HttpAwareDomainException`.

Frontend TypeScript runs in `strict` mode with `noUnusedLocals`, `noUnusedParameters`, and `noFallthroughCasesInSwitch` — unused symbols fail the build. Lint via ESLint `next/core-web-vitals`. Regenerate API types with `npm run gen:api` rather than hand-editing `src/shared/api/schema.ts`.

Never commit `.env` / `.env.local` or real credentials; all secrets are injected via env vars (templates in each `.env.example`).

## Testing Guidelines

JUnit on the backend. Name unit tests `XxxTest.java` and integration tests `XxxIT.java`, mirroring the class under test. Place tests under `nexa-service/src/test`.

## Commit & Pull Request Guidelines

Commits use Conventional Commits with a scope, often tagging the domain and iteration round: `feat(account/r7): ...`, `refactor(persistence): ...`, `fix(relay): ...`. Subjects are written in Chinese. Keep one logical change per commit and reference the affected domain in the scope.
