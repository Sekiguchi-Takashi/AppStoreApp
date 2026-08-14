#!/data/data/com.termux/files/usr/bin/bash
cd "$(dirname "$0")"
TOKEN=$(git config --global github.token)
GHUSER=Sekiguchi-Takashi
REPO=AppStoreApp
curl -s -o /dev/null -H "Authorization: token ${TOKEN}" -d "{\"name\":\"${REPO}\",\"private\":true}" https://api.github.com/user/repos
if [ ! -d .git ]; then git init -b main; fi
git remote remove origin 2>/dev/null
git remote add origin "https://${GHUSER}:${TOKEN}@github.com/${GHUSER}/${REPO}.git"
git add -A
git commit -m "${1:-update}"
git push -u origin main
