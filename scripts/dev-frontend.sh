#!/bin/bash

echo "🚀 Starting Pipeline Frontend Development Environment"

# Start Verdaccio if not running
if ! curl -s http://localhost:4873 > /dev/null; then
    echo "📦 Starting Verdaccio npm registry..."
    docker compose -f docker-compose.dev-node.yml up verdaccio -d
    sleep 2
fi

# Configure npm
echo "🔧 Configuring npm for local registry..."
npm config set registry http://localhost:4873
npm config set //localhost:4873/:_authToken="fake-token-for-dev"

echo "
🎯 Development Environment Ready!

To start developing:

1. Terminal 1 - Shared Library Watch:
   cd libraries/shared-ui
   npm run dev  # Builds on file changes

2. Terminal 2 - Auto-publish Watch:
   cd libraries/shared-ui
   nodemon --watch dist --exec 'npm version patch && npm publish'

3. Terminal 3 - Module Dev Server:
   cd modules/chunker/src/main/ui-vue
   npm run dev

Now when you edit shared components, they'll auto-rebuild and publish!
Your module dev servers will hot-reload with the changes.

🔧 Or use the simple approach:
   cd libraries/shared-ui && npm run dev &
   cd modules/chunker/src/main/ui-vue && npm run dev
"