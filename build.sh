#!/bin/bash
# Helper script for building jellyfin-androidtv in Docker

set -e

cd "$(dirname "$0")"

# Build the Docker image if needed
build_image() {
    echo "Building Docker image..."
    docker compose build
}

# Run a gradle command
gradle_cmd() {
    docker compose run --rm android-build ./gradlew "$@"
}

case "${1:-build}" in
    build|assemble)
        build_image
        echo "Building debug APK..."
        gradle_cmd assembleDebug
        echo ""
        echo "APK built at: app/build/outputs/apk/debug/"
        ;;
    release)
        build_image
        echo "Building release APK..."
        gradle_cmd assembleRelease
        echo ""
        echo "APK built at: app/build/outputs/apk/release/"
        ;;
    test)
        build_image
        echo "Running tests..."
        gradle_cmd test
        ;;
    clean)
        echo "Cleaning build artifacts..."
        gradle_cmd clean
        ;;
    shell)
        echo "Opening shell in container..."
        docker compose run --rm android-build bash
        ;;
    *)
        echo "Usage: $0 {build|release|test|clean|shell}"
        echo ""
        echo "Commands:"
        echo "  build   - Build debug APK (default)"
        echo "  release - Build release APK"
        echo "  test    - Run unit tests"
        echo "  clean   - Clean build artifacts"
        echo "  shell   - Open bash shell in container"
        exit 1
        ;;
esac
