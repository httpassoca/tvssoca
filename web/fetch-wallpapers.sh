#!/usr/bin/env sh
# Downloads the prototype's wallpapers into web/wall-dotfiles/ (gitignored: third-party artwork).
# Source: github.com/httpassoca/dotfiles, macOS/wallpapers. Each picture is saved as <name>.jpg at
# 1600x900, the names web/index.html expects. Needs curl, python3 and ImageMagick (magick).
set -eu
cd "$(dirname "$0")"
out=wall-dotfiles
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$out"
curl -fsSL "https://api.github.com/repos/httpassoca/dotfiles/contents/macOS/wallpapers?ref=main" -o "$tmp/list.json"
python3 -I -c '
import json, sys
for f in json.load(open(sys.argv[1])):
    n = f["name"]
    if f["type"] == "file" and n.lower().endswith((".jpg", ".jpeg", ".png", ".webp")):
        print(n + "\t" + f["download_url"])
' "$tmp/list.json" | while IFS="$(printf '\t')" read -r name url; do
  base=${name%.*}
  curl -fsSL "$url" -o "$tmp/$name"
  magick "$tmp/$name" -resize 1600x900^ -gravity center -extent 1600x900 -quality 85 "$out/$base.jpg"
  echo "$out/$base.jpg"
done
