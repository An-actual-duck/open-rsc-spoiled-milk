# Oracle browser alpha hosting

Prepared November 12, 2026. This is a reviewed deployment recipe, not permission
to stop or restart the public game. The manager owns Oracle operations and must
follow the shutdown gate before activating the game-server changes.

The main website stays on GitHub Pages. Only `play.rscspoiledmilk.com` points to
Oracle `132.145.137.142`. HTTPS static content uses 443; browser gameplay connects
directly to `wss://play.rscspoiledmilk.com:43505/`, subprotocol `binary`.

## Why direct WSS

The existing game's connection admission, packet limits, login checks and player
IP tracking use the socket peer. An ordinary localhost WebSocket reverse proxy
would make every browser player appear to be loopback; the existing loopback
exemption would also bypass some IP protections. This deployment never reads
`Forwarded` or `X-Forwarded-For`, changes remote addresses or disables IP limits.
The optional public policy applies only to WebSockets; native TCP 43605 remains
unchanged. Same-origin `/game` proxying would need a separately reviewed trusted
proxy design, not a quick nginx route.

The current Oracle inventory supplied by the manager has 1 A1 OCPU, 6 GB RAM,
roughly 1.81 GB game RSS, 3 GB configured Java heap and 31 GB free storage. Static
nginx sendfile uses kernel file caching, not the game's heap. No paid service,
second JVM, proxy relay or second game database is required. The curated browser
payload is about 1.3 GB; it is not uploaded to the main GitHub Pages site.

## Reviewed server policy

The public opt-in is explicit:

```
-Dspoiledmilk.publicWebSocketOrigin=https://play.rscspoiledmilk.com
-Dspoiledmilk.publicWebSocketCert=/home/ubuntu/spoiled-milk/tls/current/fullchain.pem
-Dspoiledmilk.publicWebSocketKey=/home/ubuntu/spoiled-milk/tls/current/key.pem
```

Preserve all existing JVM heap/options when adding these. With no origin flag,
ordinary WS/native settings and certificate selection remain unchanged. With it,
the startup configuration enables its existing dedicated WS listener and selects
the explicit PEM paths; it does not alter native TCP ports, database paths or
world authority. A missing/invalid configured certificate fails startup rather
than exposing the public endpoint without TLS. The existing hosted profile's WS
port is 43505. Verify the typed startup projection before activation.

Public HTTP upgrade requires successful TLS, GET `/` exactly, the single exact
HTTPS Origin above and the single `binary` subprotocol. Missing, duplicate, null
or foreign origins fail closed. Forwarding headers provide no authority. A
10-second deadline bounds incomplete handshakes, and legacy session-ID bytes are
deferred until successful upgrade. Established game idle/packet policies remain
unchanged. Text frames are rejected in public binary mode.

Certificate renewal is read at new-connection time, at most once per 30 seconds.
The candidate pair is read into bounded snapshots, checked for validity/SAN and
matching PKCS8 private key, and built completely before publication. Failed,
partial, malformed or mismatched renewal keeps the last good context. Startup
has no last-good escape hatch. Public contexts explicitly use the JDK SSL
provider: old active channels retain their context and it becomes ordinary
GC-managed memory after they close, without an OpenSSL reference-count leak.
The existing administrative reload command still works; automation does not
need an admin account or game command.

## HTTPS and immutable static files

The prepared nginx site is `config/browser-hosting/nginx-play.conf`.
The ACME webroot is `/var/lib/spoiled-milk/acme`. Root index/config/checksum files
have `Cache-Control: no-cache` so browsers may revalidate using ETag/Last-Modified.
Only versioned `/assets/<build-id>/...` URLs get a one-year immutable cache header;
that header is not attached to error responses. nginx supports single byte
Ranges (including 206 responses), HEAD, correct module/JAR MIME types and finite
header/body/keepalive/send timeouts. No directory listing, Java output tree or
private server directory is exposed. It is a static server, with no `/game`
proxy. CheerpJ's `/app` virtual filesystem maps to origin-root, so the public
packager explicitly versions both the Java VFS/classpath and frontend URLs.

Keep this layout outside every Git checkout and live game directory:

```
/srv/spoiled-milk-browser/
  releases/<build-id>/       # complete immutable verified public package
  assets/<build-id> -> ../releases/<build-id>/assets/<build-id>
  current -> releases/<build-id>
```

Copy to a new release directory, verify the complete received manifest/hash
again, then create the stable asset symlink. Never overwrite an existing build
ID. Only after verification, atomically replace `current` using a newly created
symlink plus rename. Retain old release directories and their asset links so
already-running sessions finish against their original immutable resources.
Rollback replaces `current` with a preserved older release; it does not overwrite
old assets, change the game database or restart the game. No pruning is included.

The local read-only verifier emits a deployment plan and exact nginx/hook copies:

```
python3 scripts/browser-gameplay/prepare-oracle-hosting.py \
  --web /path/to/verified/public-package/web \
  --manifest-sha256 <external-exact-public-release-json-sha256> \
  --out output/reviews/browser-oracle-hosting/prepare-1
```

Resolve the output category with workspace-layout.py before running it. It checks
endpoint/preset/source binding, every file byte count and hash, checksum-file
binding, duplicate/traversal paths, unversioned payloads, extra files and symlinks.
It never copies a key, touches Oracle, changes nginx, publishes a link or starts
anything. The manager repeats this verification on the received host copy.

## Certificate lifecycle and operator sequence

1. Confirm the Namecheap A record and Oracle ingress permit TCP 80/443/43505.
   Preserve the existing SSH/game firewall rules and their backups. Public IPv6
   is not configured by this recipe; do not add an AAAA record speculatively.
2. Install a minimal HTTP ACME bootstrap with the webroot above. Obtain a real
   certificate with Certbot webroot for `play.rscspoiledmilk.com`. A test/staging
   certificate is not acceptable for player connections.
3. Prepare `/home/ubuntu/spoiled-milk/tls` and `tls/releases` root-owned, group
   ubuntu, mode 0750. Install the reviewed hook root-owned/executable under
   `/etc/letsencrypt/renewal-hooks/deploy/`. The hook only acts when
   `RENEWED_LINEAGE` exactly matches the play domain's expected Certbot lineage.
4. Run that hook for the initial certificate as well. It validates hostname,
   remaining lifetime and key pair, converts the key to unencrypted PKCS8 and
   publishes a root-owned/group-ubuntu mode-0640 immutable pair by atomic symlink.
   Older copied pairs remain available for rollback. The hook runs `nginx -t`
   and reloads nginx only; it never stops, restarts or signals the game.
5. Install the verified static release and final nginx site, run `nginx -t`,
   then reload nginx. Run Certbot's renewal dry-run with the operator's selected
   validation mode; do not publish a staging lineage into production key copies.
6. Prepare and review the isolated server source/build diff against actual live
   v0.3.17. Obtain fresh explicit public-game shutdown permission, back up the
   live DB, use the full authorized in-game warning countdown, then activate the
   tested server artifact and explicit JVM flags using the Oracle runbook.
7. Verify public HTTPS/Range/cache behavior, certificate chain/hostname, WSS
   upgrade and binary packet exchange, and distinct client-IP accounting using
   a real external browser. Confirm native TCP clients still connect. Check
   `ss`, service logs and certificate expiry without displaying private keys.

Do not blindly replace the existing Java service unit/options or tracked game
configuration. Configurable path/port defaults are not evidence of a listener;
the manager observed WS disabled on the actual live process before this work.
Renewal failure remains visible in logs and retains last good material, but an
operator must still monitor certificate expiry; it cannot make an expired
certificate valid.

## Tests and remaining acceptance

`tests/myworld/browser-oracle-hosting/run-network.py` builds ephemeral ignored
certificates, uses real in-memory TLS engines and the production Netty decoder,
HTTP/WebSocket upgrade and packet handlers without sockets, listeners or a DB.
It checks Origin/TLS/path/subprotocol refusals, forwarding-header non-authority,
per-IP filter limits, handshake deadline/activation ordering, real 101/binary
upgrade, native/binary protocol parity and renewal last-good behavior. Generated
fixture keys are not deployment credentials and never enter Git.
`test-hosting.py` covers inventory and path/hash/config negatives. Shell syntax
is checked separately. Full server Ant compilation is required.

These CPU tests do not prove Oracle ARM/JDK17 behavior, actual nginx parsing,
external OCI ingress, real certificate renewal, browser user-agent policy,
throughput or a production login. The manager must run those checks before
calling the deployment complete. No public shutdown, deployment or host operation
was performed by this worker.

Protocol and operation references used for this design:

- RFC6455 server opening handshake / Origin handling:
  https://datatracker.ietf.org/doc/html/rfc6455#section-4.2
- Netty OptionalSslHandler intentionally supports SSL and non-SSL, which is why
  public HTTP admission additionally requires completed TLS:
  https://netty.io/4.1/api/io/netty/handler/ssl/OptionalSslHandler.html
- nginx static byte range and module directives:
  https://nginx.org/en/docs/http/ngx_http_core_module.html
- Certbot deployment hooks:
  https://eff-certbot.readthedocs.io/en/stable/using.html#renewing-certificates

### Frozen worker evidence

Production Java checkpoint: `daed56e3722576250808fc0d67bf0720bdb97e45`.
The isolated full-source server build is preserved at
`/home/justin/Core-Framework-ai-3/output/builds/browser-oracle-hosting/review-daed56/`.
Its receipt binds all maintained server/plugin Java sources, both complete JARs,
archive-entry manifests, build command/log and clean source commit; no class
transplants were used. `core.jar` SHA-256 is
`e0e50a0f57234048df0b817cbb82f7a72271d57c4dd4423e4a9a85eaa97715bc`.
All maintained server Java hashes still match that frozen build.

Run from the worker checkout with fresh output names:

```
nice -n 10 python3 tests/myworld/browser-oracle-hosting/run-network.py \
  --root . --jar output/builds/browser-oracle-hosting/review-daed56/core.jar \
  --out output/tests/browser-oracle-hosting/network-repeat
nice -n 10 python3 tests/myworld/browser-oracle-hosting/run-config.py \
  --root . --jar output/builds/browser-oracle-hosting/review-daed56/core.jar \
  --out output/tests/browser-oracle-hosting/config-repeat
python3 tests/myworld/browser-oracle-hosting/test-hosting.py \
  --out output/tests/browser-oracle-hosting/hosting-repeat
bash -n config/browser-hosting/certbot-deploy-hook.sh
```

Preserved `network-frozen-2` passed 51 checks per public ON/OFF process, using real
RSA and EC TLS handshakes, production dynamic decoder/upgrade insertion, masked
binary packet exchange and unchanged native TCP decoding. `config-frozen-1`
passed actual hosted parser and immutable projection ON/OFF with the exact
installed package explicitly supplied. `hosting-final-1` passed 16 negative and
configuration checks. `ip-consumer-source-proof.json` proves every maintained
socket remote-address consumer file remains byte-identical to the pre-change
baseline. The output-layout check reports only the 40 inherited unclassified
legacy entries, which were preserved.

The older `test-server-r2-configuration-views.py` passes seven of eight cases.
Its isolated hosted-profile case copies the selected native-map profile without
the required installed package path and fails before the new WSS options. The
new focused parser fixture supplies that real path and passes both policies.
This limitation is preserved in `configuration-parity.log`; it is not presented
as a completely green broad suite or silently repaired by changing map authority.
