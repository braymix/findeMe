#!/usr/bin/env bash
# Runs the full backend verification: build, lint, tests. No DB required (tests use
# in-memory repos).
set -euo pipefail
cd "$(dirname "$0")/../backend"
npm install
npx prisma generate
npm run build
npm run lint
npm test
