#!/bin/sh
# Копирует игру в папку docs/ для GitHub Pages (версия для iPhone/браузера).
set -e
cd "$(dirname "$0")"
mkdir -p docs
for f in index.html manifest.webmanifest sw.js apple-touch-icon.png icon-192.png icon-512.png; do
  cp app/src/main/assets/$f docs/$f
done
touch docs/.nojekyll
echo "docs/ обновлена"
