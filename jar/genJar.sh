#!/usr/bin/env bash
# Linux version of genJar.bat - generates custom_spider.jar from built APK
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_DIR="$SCRIPT_DIR"
PROJECT_DIR="$SCRIPT_DIR/.."

APK_FILE="$PROJECT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
SMALI_DIR="$JAR_DIR/Smali_classes"
SPIDER_JAR_DIR="$JAR_DIR/spider.jar"

rm -f "$JAR_DIR/custom_spider.jar"
rm -rf "$SMALI_DIR"

echo "Decoding APK..."
java -jar "$JAR_DIR/3rd/apktool_2.11.0.jar" d -f --only-main-classes "$APK_FILE" -o "$SMALI_DIR"

echo "Cleaning old smali dirs..."
rm -rf "$SPIDER_JAR_DIR/smali/com/github/catvod/spider"
rm -rf "$SPIDER_JAR_DIR/smali/com/github/catvod/js"
rm -rf "$SPIDER_JAR_DIR/smali/org/slf4j"

mkdir -p "$SPIDER_JAR_DIR/smali/com/github/catvod/"
mkdir -p "$SPIDER_JAR_DIR/smali/org/slf4j/"

echo "Moving new smali..."
mv "$SMALI_DIR/smali/com/github/catvod/spider" "$SPIDER_JAR_DIR/smali/com/github/catvod/"
[ -d "$SMALI_DIR/smali/com/github/catvod/js" ] && mv "$SMALI_DIR/smali/com/github/catvod/js" "$SPIDER_JAR_DIR/smali/com/github/catvod/"
[ -d "$SMALI_DIR/smali/org/slf4j" ] && mv "$SMALI_DIR/smali/org/slf4j" "$SPIDER_JAR_DIR/smali/org/slf4j/"

rm -rf "$SMALI_DIR"

echo "Rebuilding jar..."
java -jar "$JAR_DIR/3rd/apktool_2.11.0.jar" b "$SPIDER_JAR_DIR" -c

mv "$SPIDER_JAR_DIR/dist/dex.jar" "$JAR_DIR/custom_spider.jar"

echo "Computing MD5..."
md5sum "$JAR_DIR/custom_spider.jar" | cut -d' ' -f1 > "$JAR_DIR/custom_spider.jar.md5"

rm -rf "$SPIDER_JAR_DIR/build"
rm -rf "$SPIDER_JAR_DIR/smali"
rm -rf "$SPIDER_JAR_DIR/dist"

echo "Done! custom_spider.jar generated."
echo "MD5: $(cat $JAR_DIR/custom_spider.jar.md5)"
