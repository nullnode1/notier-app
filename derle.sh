#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
project="$PWD"
main="$project/app/src/main"
out="$project/build"
cache="${NOTIER_CACHE:-$out/cache}"
mkdir -p "$out" "$cache"

die() { printf '%s\n' "$*" >&2; exit 1; }

aapt2_command="${NOTIER_AAPT2:-aapt2}"
sign_command="${NOTIER_APKSIGNER:-apksigner}"
d8_command="${NOTIER_D8:-d8}"
r8_command="${NOTIER_R8:-r8}"

for program in java keytool curl unzip sha256sum python3 "$aapt2_command" "$sign_command" "$d8_command"; do
    command -v "$program" >/dev/null 2>&1 || die "Araç bulunamadı: $program. Termux: pkg install openjdk-21 aapt2 d8 apksigner python curl unzip"
done

download() {
    local checksum=$1 destination=$2 url=$3
    if [[ -f "$destination" ]] && printf '%s  %s\n' "$checksum" "$destination" | sha256sum --check --status; then
        return
    fi
    local transfer
    transfer=$(mktemp "$cache/download.XXXXXX")
    if ! curl -fLsS --retry 2 --connect-timeout 15 "$url" -o "$transfer"; then
        rm -f -- "$transfer"
        die "İndirme başarısız: $url"
    fi
    if ! printf '%s  %s\n' "$checksum" "$transfer" | sha256sum --check --status; then
        rm -f -- "$transfer"
        die "İndirilen dosyanın sağlama toplamı eşleşmedi: $url"
    fi
    mv -f -- "$transfer" "$destination"
}

printf '%s\n' 'Android SDK ve Kotlin bağımlılıkları kontrol ediliyor…'
platform="${NOTIER_ANDROID_JAR:-}"
if [[ -z "$platform" ]]; then
    for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/android-sdk" "${PREFIX:-/usr}/opt/android-sdk"; do
        if [[ -n "$sdk" && -f "$sdk/platforms/android-36/android.jar" ]]; then
            platform="$sdk/platforms/android-36/android.jar"
            break
        fi
    done
fi
if [[ -z "$platform" ]]; then
    read -r platform_hash platform_name platform_url < tools/android-sdk36.tsv
    platform="$cache/$platform_name"
    if ! [[ -f "$platform" ]] || ! printf '%s  %s\n' "$platform_hash" "$platform" | sha256sum --check --status; then
        sdk_transfer=$(mktemp -d "$cache/platform.XXXXXX")
        trap 'rm -rf -- "$sdk_transfer"' EXIT
        curl -fLsS --retry 2 --connect-timeout 15 "$platform_url" -o "$sdk_transfer/platform.zip"
        unzip -p "$sdk_transfer/platform.zip" 'android-36/android.jar' > "$sdk_transfer/android.jar"
        printf '%s  %s\n' "$platform_hash" "$sdk_transfer/android.jar" | sha256sum -c --status ||
            die "Android SDK dosyasının sağlama toplamı eşleşmedi."
        mv -f -- "$sdk_transfer/android.jar" "$platform"
        rm -rf -- "$sdk_transfer"
        trap - EXIT
    fi
fi
[[ -f "$platform" ]] || die "Android platform dosyası bulunamadı: $platform"

compiler_jars=()
runtime_jars=()
while read -r checksum filename url; do
    jar="$cache/$filename"
    download "$checksum" "$jar" "$url"
    case "$filename" in
        kotlin-stdlib-*) compiler_jars+=("$jar"); runtime_jars+=("$jar") ;;
        commonmark-*|autolink-*) runtime_jars+=("$jar") ;;
        *) compiler_jars+=("$jar") ;;
    esac
done < tools/dependencies.tsv
join_paths() { local IFS=:; printf '%s' "$*"; }
compiler_classpath=$(join_paths "${compiler_jars[@]}")
work=$(mktemp -d "$out/compile.XXXXXX")
trap 'rm -rf -- "$work"' EXIT
mkdir -p "$work/dex" "$work/generated-res"
google_res=()
google_symbols=()
google_rules=()
while read -r checksum filename url; do
    aar="$cache/$filename"
    download "$checksum" "$aar" "$url"
    if [[ "$filename" == *.jar ]]; then
        runtime_jars+=("$aar")
        continue
    fi
    library="$work/${filename%.aar}"
    unzip -qo "$aar" -d "$library"
    if [[ -f "$library/classes.jar" ]]; then runtime_jars+=("$library/classes.jar"); fi
    if [[ -f "$library/proguard.txt" ]]; then google_rules+=(--pg-conf "$library/proguard.txt"); fi
    if [[ -d "$library/res" && -n "$(find "$library/res" -type f -print -quit)" ]]; then
        resource="$work/${filename%.aar}-res.zip"
        "$aapt2_command" compile --dir "$library/res" -o "$resource"
        google_res+=(-R "$resource")
        google_symbols+=("$library")
    fi
done < tools/google-drive.tsv
runtime_classpath=$(join_paths "${runtime_jars[@]}")
manifest="$main/AndroidManifest.xml"
package_args=()
if [[ "${NOTIER_SIDE_BY_SIDE:-0}" == 1 ]]; then
    package_args=(--rename-manifest-package app.notier.standalone)
fi
printf '%s\n' 'Android kaynakları paketleniyor…'
"$aapt2_command" compile --dir "$main/res" -o "$work/resources.zip"
"$aapt2_command" link -I "$platform" --manifest "$manifest" "${package_args[@]}" \
    -A "$main/assets" --output-text-symbols "$work/R.txt" \
    --auto-add-overlay -o "$work/resources.apk" "${google_res[@]}" -R "$work/resources.zip"
python3 tools/google_resources.py "$work/R.txt" "$work/generated-res" "${google_symbols[@]}"
awk '
    BEGIN { print "package app.notier\ninternal object R {" }
    $1 == "int" {
        if (group != $2) {
            if (group != "") print "    }"
            group = $2
            print "    object " group " {"
        }
        print "        const val " $3 " = " $4
    }
    END {
        if (group != "") print "    }"
        print "}"
    }
' "$work/R.txt" > "$work/R.kt"

printf '%s\n' 'Kotlin kaynakları derleniyor…'
java -Xmx640m -cp "$compiler_classpath" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -jvm-target 1.8 -classpath "$platform:$runtime_classpath" \
    -d "$work/app.jar" "$main"/kotlin/app/notier/*.kt "$work/R.kt" "$work"/generated-res/*.kt

if command -v "$r8_command" >/dev/null 2>&1; then
    printf '%s\n' 'APK boyutu küçültülüyor…'
    "$r8_command" --release --min-api 26 --no-data-resources --lib "$platform" --pg-conf tools/r8.pro \
        "${google_rules[@]}" --output "$work/dex" "$work/app.jar" "${runtime_jars[@]}"
else
    printf '%s\n' 'Android bytecode hazırlanıyor…'
    "$d8_command" --release --min-api 26 --lib "$platform" --output "$work/dex" \
        "$work/app.jar" "${runtime_jars[@]}"
fi
[[ -f "$work/dex/classes.dex" ]] || die 'DEX oluşturulamadı.'
python3 tools/package_apk.py "$work/resources.apk" "$work/dex" "$work/unsigned.apk"

key_dir="${NOTIER_SIGNING_DIR:-$HOME/.notier-signing}"
mkdir -p "$key_dir"
key_dir=$(cd "$key_dir" && pwd)
key="$key_dir/debug.p12"
if [[ ! -f "$key" ]]; then
    ( umask 077
      keytool -genkeypair -keystore "$key" -storetype PKCS12 -storepass android \
          -alias notier-debug -keyalg RSA -keysize 2048 -validity 10000 \
          -dname 'CN=Notier Local Build' )
fi
printf '%s\n' 'APK imzalanıyor ve doğrulanıyor…'
"$sign_command" sign --ks "$key" --ks-key-alias notier-debug --ks-pass pass:android \
    --out "$work/Notier.apk" "$work/unsigned.apk"
"$sign_command" verify "$work/Notier.apk"
mv -f -- "$work/Notier.apk" "$out/Notier.apk"
printf 'APK hazır: %s\n' "$out/Notier.apk"
