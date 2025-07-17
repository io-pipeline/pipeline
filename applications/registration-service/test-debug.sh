#!/bin/bash
set -e

echo "Running tests with debug output to diagnose missing classes..."
echo ""

# Clean first to ensure fresh state
echo "1. Cleaning build directory..."
cd ../..
./gradlew :applications:registration-service:clean

echo ""
echo "2. Compiling main sources..."
./gradlew :applications:registration-service:compileJava --info | grep -E "(Compiling|Generated)"

echo ""
echo "3. Listing generated sources..."
echo "Generated sources directory:"
ls -la applications/registration-service/build/classes/java/quarkus-generated-sources/ 2>/dev/null || echo "No generated sources found"

echo ""
echo "4. Compiling test sources..."
./gradlew :applications:registration-service:compileTestJava --info | grep -E "(Compiling|FAILED)"

echo ""
echo "5. Running tests with full stacktrace..."
./gradlew :applications:registration-service:test --stacktrace --info

echo ""
echo "If tests are still failing, check:"
echo "- Are all dependencies in build.gradle.kts?"
echo "- Is @QuarkusTest annotation present on test classes?"
echo "- Are generated sources being created properly?"