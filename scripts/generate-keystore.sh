#!/usr/bin/env bash
# Создаёт новый PKCS12-ключ подписи (нужен JDK с keytool).
# Использование: scripts/generate-keystore.sh [файл.p12] [алиас]
set -euo pipefail
OUT="${1:-spoty-new.p12}"
ALIAS="${2:-spoty}"
PASS="$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)"
keytool -genkeypair -v -storetype PKCS12 -keystore "$OUT" -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10950 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=Spoty, OU=Android, O=Spoty Music" >/dev/null
echo "Хранилище: $OUT"
echo "Алиас:     $ALIAS"
echo "Пароль:    $PASS   (сохраните! восстановить его невозможно)"
echo "Для GitHub Secrets:  base64 -w0 $OUT"
keytool -list -v -keystore "$OUT" -storepass "$PASS" | grep -E 'SHA256:' | head -1
