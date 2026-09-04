#!/usr/bin/env bash
# Creates the release signing key and prints what to paste into the four
# GitHub secrets. The key never leaves this machine; only its base64 form is
# copied, and that goes into an encrypted secret.
#
# Keep release.keystore somewhere safe and backed up: losing it means no
# future build can update an already installed app.
set -euo pipefail

KEYSTORE=${1:-release.keystore}
ALIAS=${2:-inspection}

if [ -f "$KEYSTORE" ]; then
  echo "refusing to overwrite an existing $KEYSTORE" >&2
  exit 1
fi

read -r -s -p "Password for the new keystore (remember it): " PASSWORD
echo
read -r -s -p "Repeat the password: " CONFIRM
echo
[ "$PASSWORD" = "$CONFIRM" ] || { echo "passwords differ" >&2; exit 1; }
[ ${#PASSWORD} -ge 6 ] || { echo "use at least six characters" >&2; exit 1; }

keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -storetype PKCS12 \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASSWORD" -keypass "$PASSWORD" \
  -dname "CN=Ilam Electricity Distribution, OU=Crypto inspection, O=Tavanir, C=IR"

echo
echo "Done. Add these four repository secrets:"
echo "  Settings > Secrets and variables > Actions > New repository secret"
echo
echo "RELEASE_KEYSTORE_BASE64  = contents of ${KEYSTORE}.base64.txt"
echo "RELEASE_KEYSTORE_PASSWORD = the password you just chose"
echo "RELEASE_KEY_ALIAS        = $ALIAS"
echo "RELEASE_KEY_PASSWORD     = the same password"
echo
base64 -w0 "$KEYSTORE" > "${KEYSTORE}.base64.txt" 2>/dev/null \
  || base64 "$KEYSTORE" | tr -d '\n' > "${KEYSTORE}.base64.txt"
echo "base64 written to ${KEYSTORE}.base64.txt"
echo "Delete that text file once the secret is saved; keep $KEYSTORE backed up."
