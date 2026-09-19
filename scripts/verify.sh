#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p verification
./gradlew :core:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace 2>&1 | tee verification/gradle.log
if [[ "${1:-}" == "--device" ]]; then
  ./gradlew :app:connectedDebugAndroidTest --stacktrace 2>&1 | tee verification/instrumented.log
fi
cp app/build/outputs/apk/debug/app-debug.apk verification/Dehpilot-0.2.0-debug.apk
sha256sum verification/Dehpilot-0.2.0-debug.apk > verification/apk.sha256
printf '%s\n' 'Build gerado. Consulte logs e execute testes Android/QA antes de aprovar a versão.'
