#!/usr/bin/env bash
set -Eeuo pipefail

# No secrets are baked into the image. Keep shell tracing disabled.
if [[ "${TAILSCALE_ENABLED:-false}" != "true" ]]; then
    exec java -jar /app/app.jar --server.port="${PORT:-10000}"
fi
: "${TS_AUTHKEY:?Set TS_AUTHKEY as a secret in Render}"
: "${MONGODB_URI:?Set MONGODB_URI as a secret in Render}"
# Require an explicit SOCKS route, rather than silently connecting elsewhere.
if [[ "$MONGODB_URI" != *"proxyHost=127.0.0.1"* || "$MONGODB_URI" != *"proxyPort=1055"* ]]; then
    echo 'MONGODB_URI must use proxyHost=127.0.0.1 and proxyPort=1055.' >&2
    exit 1
fi

umask 077
runtime_dir=$(mktemp -d /tmp/staycontrol-tailscale.XXXXXX)
vpn_pid=''
app_pid=''
cleanup() {
    trap - EXIT TERM INT
    [[ -z "$app_pid" ]] || kill "$app_pid" 2>/dev/null || true
    [[ -z "$vpn_pid" ]] || kill "$vpn_pid" 2>/dev/null || true
    wait 2>/dev/null || true
    # Only the private temporary key file is removed; no repository data.
    rm -f -- "$runtime_dir/authkey"
}
trap cleanup EXIT
trap 'exit 143' TERM
trap 'exit 130' INT

# Do not pass the enrollment secret to Java or tailscaled's environment.
printf '%s' "$TS_AUTHKEY" > "$runtime_dir/authkey"
unset TS_AUTHKEY
tailscaled --tun=userspace-networking --state=mem: \
    --socket="$runtime_dir/tailscaled.sock" \
    --socks5-server=127.0.0.1:1055 &
vpn_pid=$!

for ((attempt=0; attempt<30; attempt++)); do
    [[ ! -S "$runtime_dir/tailscaled.sock" ]] || break
    kill -0 "$vpn_pid" 2>/dev/null || { echo 'Tailscale stopped during startup.' >&2; exit 1; }
    sleep 1
done
[[ -S "$runtime_dir/tailscaled.sock" ]] || { echo 'Tailscale socket timeout.' >&2; exit 1; }
tailscale --socket="$runtime_dir/tailscaled.sock" up \
    --auth-key="file:$runtime_dir/authkey" \
    --hostname="${TS_HOSTNAME:-staycontrol-render}" \
    --accept-dns=false --accept-routes=false --timeout=60s
rm -f -- "$runtime_dir/authkey"

java -jar /app/app.jar --server.port="${PORT:-10000}" &
app_pid=$!
# Stop the container if either process exits, instead of serving with a dead VPN.
set +e
wait -n "$vpn_pid" "$app_pid"
status=$?
set -e
[[ "$status" -ne 0 ]] || status=1
exit "$status"
