#!/usr/bin/env bash
# Linux version of genJar.bat - generates custom_spider.jar from built APK
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_DIR="$SCRIPT_DIR"
PROJECT_DIR="$SCRIPT_DIR/.."

DEX_FILE="$PROJECT_DIR/app/build/intermediates/dex/release/minifyReleaseWithR8/classes.dex"
SMALI_DIR="$JAR_DIR/Smali_classes"
SPIDER_JAR_DIR="$JAR_DIR/spider.jar"

rm -f "$JAR_DIR/custom_spider.jar"
rm -rf "$SMALI_DIR"

echo "Disassembling DEX..."
java -jar "$JAR_DIR/3rd/baksmali-2.5.2.jar" d "$DEX_FILE" -o "$SMALI_DIR"

echo "Cleaning old spider/parser smali..."
rm -rf "$SPIDER_JAR_DIR/smali/com/github/catvod/spider"
rm -rf "$SPIDER_JAR_DIR/smali/com/github/catvod/parser"

mkdir -p "$SPIDER_JAR_DIR/smali/com/github/catvod/"

echo "Moving new spider smali..."
mv "$SMALI_DIR/com/github/catvod/spider" "$SPIDER_JAR_DIR/smali/com/github/catvod/"
[ -d "$SMALI_DIR/com/github/catvod/parser" ] && mv "$SMALI_DIR/com/github/catvod/parser" "$SPIDER_JAR_DIR/smali/com/github/catvod/"

rm -rf "$SMALI_DIR"

echo "Rebuilding jar..."
java -jar "$JAR_DIR/3rd/apktool_2.4.1.jar" b "$SPIDER_JAR_DIR" -c

mv "$SPIDER_JAR_DIR/dist/dex.jar" "$JAR_DIR/custom_spider.jar"

echo "Computing MD5..."
md5sum "$JAR_DIR/custom_spider.jar" | cut -d' ' -f1 > "$JAR_DIR/custom_spider.jar.md5"

rm -rf "$SPIDER_JAR_DIR/smali/com/github/catvod/spider"
rm -rf "$SPIDER_JAR_DIR/smali/com/github/catvod/parser"
rm -rf "$SPIDER_JAR_DIR/build"
rm -rf "$SPIDER_JAR_DIR/dist"

echo "Done! custom_spider.jar generated."
echo "MD5: $(cat $JAR_DIR/custom_spider.jar.md5)"

