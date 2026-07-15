# Deterministic build for Render (and anywhere). Avoids the "exited with status 127"
# class of errors caused by devDependencies (tsc/prisma) being stripped under
# NODE_ENV=production: the build stage always installs everything.

# ---- build stage: full deps, generate client, compile TS ----
FROM node:20-slim AS build
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends openssl && rm -rf /var/lib/apt/lists/*
COPY package*.json ./
RUN npm ci
COPY . .
RUN npx prisma generate && npm run build

# ---- runtime stage ----
FROM node:20-slim AS runtime
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends openssl && rm -rf /var/lib/apt/lists/*
ENV NODE_ENV=production
# Bring the whole tree from build (incl. node_modules with the Prisma CLI + generated
# client) so `prisma migrate deploy` and the server run without any extra install.
COPY --from=build /app ./
EXPOSE 3000
# Apply migrations, then start. Render injects PORT; the server binds to it.
CMD ["sh", "-c", "npx prisma migrate deploy && node dist/index.js"]
