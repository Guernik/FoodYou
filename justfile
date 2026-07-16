# Resolve the Android SDK location from local.properties (falling back to
# $ANDROID_HOME), then expose zipalign/apksigner from the latest build-tools.
export ANDROID_HOME := env_var_or_default('ANDROID_HOME', `sed -n 's/^sdk.dir=//p' local.properties`)
build_tools := ANDROID_HOME / 'build-tools' / shell('ls "$1/build-tools" 2>/dev/null | sort -V | tail -1', ANDROID_HOME)
export PATH := build_tools + ':' + env_var('PATH')

default:
    @just --list

# $KTFMT_JAR - path to the ktfmt jar file
format:
    @find . -type f \( -name "*.kt" -o -name "*.kts" \) -not -path "*/build/*" | xargs java -jar $KTFMT_JAR --kotlinlang-style

release:
    @./gradlew --no-daemon --no-build-cache clean
    @./gradlew --no-daemon --no-build-cache app:assembleRelease
    @zipalign -f -p -v 4 \
      app/build/outputs/apk/release/app-release-unsigned.apk \
      app/build/outputs/apk/release/aligned.apk
    @apksigner sign \
      --alignment-preserved \
      --ks foodyou.keystore \
      --ks-key-alias foodyou \
      --out ./release-signed.apk \
      app/build/outputs/apk/release/aligned.apk

preview:
    @./gradlew --no-daemon --no-build-cache clean
    @./gradlew --no-daemon --no-build-cache app:assemblePreview
    @zipalign -f -p -v 4 \
      app/build/outputs/apk/preview/app-preview-unsigned.apk \
      app/build/outputs/apk/preview/aligned.apk
    @apksigner sign \
      --alignment-preserved \
      --ks foodyou.keystore \
      --ks-key-alias foodyou \
      --out ./preview-signed.apk \
      app/build/outputs/apk/preview/aligned.apk

[working-directory: 'docs']
serve:
    zensical serve
