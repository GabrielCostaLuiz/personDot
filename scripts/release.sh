#!/usr/bin/env bash
# Publica uma versão nova: ./scripts/release.sh 0.2.0
# Cria a tag vX.Y.Z e envia; o GitHub Actions gera o APK e a release.
set -euo pipefail

VERSION="${1:?Uso: ./scripts/release.sh X.Y.Z}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Versão deve ser X.Y.Z (ex.: 0.2.0)"; exit 1; }

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Há mudanças sem commit. Faça commit antes de publicar."; exit 1
fi

git push origin HEAD
git tag "v$VERSION"
git push origin "v$VERSION"
echo "Tag v$VERSION enviada. Acompanhe em: https://github.com/GabrielCostaLuiz/personDot/actions"
