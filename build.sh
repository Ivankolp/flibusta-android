#!/bin/bash
set -e
echo "🚀 Запуск сверхбыстрой сборки Android Hello World..."
./gradlew assembleDebug --build-cache
echo ""
echo "🎉 Сборка успешно завершена!"
ls -lh build/outputs/apk/debug/*.apk 2>/dev/null || true
