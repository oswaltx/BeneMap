#!/usr/bin/env bash
# Builds the app fresh and deploys it to the Uberspace server.
# Usage: ./deploy.sh          (builds, tests, uploads, restarts)
#        SKIP_TESTS=1 ./deploy.sh   (skip the backend test suite for a faster deploy)
set -euo pipefail
cd "$(dirname "$0")"

SSH_HOST="oswalt@volans.uberspace.de"
SSH_KEY="$HOME/.ssh/uberspace"
REMOTE_DIR="~/benemap"

echo "==> Building frontend..."
(cd frontend && npm install && npm run build)

echo "==> Bundling frontend into backend static resources..."
rm -rf backend/src/main/resources/static
mkdir -p backend/src/main/resources/static
cp -r frontend/dist/* backend/src/main/resources/static/

echo "==> Building backend jar..."
if [ "${SKIP_TESTS:-}" = "1" ]; then
    (cd backend && ./gradlew.bat bootJar -x test)
else
    (cd backend && ./gradlew.bat bootJar)
fi

JAR=$(ls backend/build/libs/*.jar | grep -v -- -plain.jar | head -1)
echo "==> Uploading $JAR ..."
scp -i "$SSH_KEY" "$JAR" "$SSH_HOST:$REMOTE_DIR/app.jar.new"

echo "==> Swapping in new jar and restarting service..."
ssh -i "$SSH_KEY" "$SSH_HOST" "mv $REMOTE_DIR/app.jar.new $REMOTE_DIR/app.jar && supervisorctl restart benemap"

echo "==> Waiting for startup..."
sleep 5
ssh -i "$SSH_KEY" "$SSH_HOST" "supervisorctl status benemap && curl -s -o /dev/null -w 'HTTP %{http_code}\n' http://localhost:48100/"

echo "==> Deploy complete: https://benemap.org"
