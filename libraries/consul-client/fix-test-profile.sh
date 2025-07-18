#!/bin/bash

# Fix UnifiedTestProfile references to ConsulClientTestProfile

echo "Fixing UnifiedTestProfile references..."

# Fix import statements
find src -name "*.java" -type f -exec sed -i 's/import io\.pipeline\.api\.testing\.UnifiedTestProfile/import io.pipeline.consul.client.test.ConsulClientTestProfile/g' {} \;

# Fix class references
find src -name "*.java" -type f -exec sed -i 's/UnifiedTestProfile/ConsulClientTestProfile/g' {} \;

# Fix specific method calls that might be different
find src -name "*.java" -type f -exec sed -i 's/ConsulClientTestProfile\.configureFor(this\.getClass())/ConsulClientTestProfile.configureFor(this.getClass())/g' {} \;

echo "Test profile fixes completed!"