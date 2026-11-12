#!/usr/bin/env bash
# Root-owned Certbot deploy hook; does not stop, restart or signal the game.
set -euo pipefail
lineage=/etc/letsencrypt/live/play.rscspoiledmilk.com
[[ "${RENEWED_LINEAGE:-}" == "$lineage" ]] || exit 0
base=/home/ubuntu/spoiled-milk/tls
[[ -d "$base/releases" && ! -L "$base/releases" ]] || { echo 'Prepare root-owned TLS release directory first' >&2; exit 1; }
[[ ! -e "$base/current" || -L "$base/current" ]] || { echo 'Refusing to replace non-symlink TLS current' >&2; exit 1; }
umask 077
stage="$(mktemp -d "$base/releases/.renew.XXXXXXXX")"
link="$base/.current.$$"
cleanup() { rm -f -- "$link"; if [[ -n "$stage" ]]; then rm -rf -- "$stage"; fi; }
trap cleanup EXIT
openssl x509 -in "$lineage/fullchain.pem" -checkhost play.rscspoiledmilk.com -noout
openssl x509 -in "$lineage/fullchain.pem" -checkend 3600 -noout
cp -- "$lineage/fullchain.pem" "$stage/fullchain.pem"
openssl pkcs8 -topk8 -nocrypt -in "$lineage/privkey.pem" -out "$stage/key.pem"
openssl x509 -in "$stage/fullchain.pem" -pubkey -noout > "$stage/cert-public.pem"
openssl pkey -in "$stage/key.pem" -pubout > "$stage/key-public.pem"
cmp -s "$stage/cert-public.pem" "$stage/key-public.pem"
rm -- "$stage/cert-public.pem" "$stage/key-public.pem"
identity="$(sha256sum "$stage/fullchain.pem" | cut -d' ' -f1)"
final="$base/releases/$identity"
chown root:ubuntu "$stage" "$stage/fullchain.pem" "$stage/key.pem"
chmod 0750 "$stage"; chmod 0640 "$stage/fullchain.pem" "$stage/key.pem"
if [[ -e "$final" ]]; then
    cmp -s "$stage/fullchain.pem" "$final/fullchain.pem"
    cmp -s "$stage/key.pem" "$final/key.pem"
else
    mv -- "$stage" "$final"
    stage=''
fi
ln -s -- "releases/$identity" "$link"
mv -Tf -- "$link" "$base/current"
# nginx reads its own Certbot lineage; the game reads the validated copied pair
# on a future connection (at most one refresh attempt per30s). No game command.
nginx -t
systemctl reload nginx
