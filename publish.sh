#!/usr/bin/env bash
# 用法: ./publish.sh <版本号如 1.3.1> "<中文更新说明>"   —— 自动 versionCode+1、构建、签名校验、打 tag、发 GitHub Release
set -euo pipefail
cd "$(dirname "$0")"
VER="${1:?版本号}"; NOTES="${2:-更新}"
export JAVA_HOME=/workspace/tools/jdk-17.0.21+9 ANDROID_HOME=/workspace/tools/sdk
export PATH="$JAVA_HOME/bin:/workspace/tools/gradle-8.7/bin:$PATH"
export KS_PASS="$(cat ks_pass.txt)"
CODE=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts); NEW=$((CODE+1))
sed -i -E "s/versionCode = [0-9]+; versionName = \"[^\"]+\"/versionCode = $NEW; versionName = \"$VER\"/" app/build.gradle.kts
gradle assembleRelease -q
APK="DahuaViewer-$VER.apk"; cp app/build/outputs/apk/release/app-release.apk "$APK"
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify "$APK"
"$ANDROID_HOME/build-tools/34.0.0/aapt" dump badging "$APK" | head -1
sha256sum "$APK" | awk '{print $1"  '"$APK"'"}' > "$APK.sha256"
SHA=$(cut -d' ' -f1 "$APK.sha256")
git add -A && git commit -qm "v$VER" && git push -q
gh release create "v$VER" "$APK" "$APK.sha256" --title "v$VER" --notes "$NOTES

SHA-256: $SHA"
echo "已发布 v$VER"
