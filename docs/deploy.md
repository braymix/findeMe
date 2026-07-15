# Deployment

## Backend — Render (automatic, from `main`)

The backend deploys to Render as a Node web service defined by [`render.yaml`](../render.yaml)
(a Render Blueprint). It **auto-deploys on every push to `main`**.

Public URL: **https://findeme.onrender.com** · WebSocket: **wss://findeme.onrender.com/ws**

### One-time setup
1. Render Dashboard ▸ **New ▸ Blueprint** ▸ connect this GitHub repo. Render reads
   `render.yaml` and creates the `findeme` service.
2. Ensure the environment has **`DATABASE_URL`** set to your Postgres connection string
   (already configured per your setup). `JWT_SECRET` is auto-generated; `PORT` is injected
   by Render.
3. First deploy runs the build command, which **applies the Prisma migration**
   (`prisma/migrations/0001_init`) to create the tables, then compiles and starts the server.

The service uses a **Dockerfile** (`backend/Dockerfile`), so the build is deterministic:
it installs all deps, generates the Prisma client, compiles TypeScript, then at start runs
`prisma migrate deploy && node dist/index.js`. Health: `GET /health` → `200`.

### Troubleshooting: "exited with status 127"
`127` means *command not found*. On Render it almost always means the build ran
`npm run build` (which needs `tsc`) but TypeScript wasn't installed, because a plain
`npm install` under `NODE_ENV=production` skips devDependencies. Two fixes:

- **Recommended — use the Blueprint (Docker).** Delete the manually-created service and
  recreate it via **New ▸ Blueprint**; the Dockerfile installs everything in its build
  stage, so `tsc`/`prisma` are always present. No 127.
- **If you keep a manual Node service**, set these exactly (Settings ▸ Build & Deploy):
  - **Root Directory:** `backend`
  - **Build Command:** `npm install --include=dev && npx prisma generate && npx prisma migrate deploy && npm run build`
  - **Start Command:** `npm run start`

  The `--include=dev` is the key part — it forces TypeScript/Prisma to be installed even
  with `NODE_ENV=production`.

### Redeploy
Just push to `main`. Render rebuilds automatically. Migrations are idempotent
(`migrate deploy` only applies new ones).

### Verify after deploy
```bash
curl https://findeme.onrender.com/health
# {"status":"ok"}
```
> Free-plan services sleep after inactivity; the first request after a while may take a few
> seconds to wake the instance.

## Mobile apps

Both apps are pre-pointed at `https://findeme.onrender.com`. See:
- [`downloads/android/README.md`](../downloads/android/README.md) — build/download the APK.
- [`downloads/ios/README.md`](../downloads/ios/README.md) — TestFlight / device install.

They are produced by the [`Release apps`](../.github/workflows/release.yml) workflow: push a
tag like `v1.0.0` from `main` to build the Android APK and attach it to a GitHub Release.
