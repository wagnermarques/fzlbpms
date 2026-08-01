#!/usr/bin/env bash
#
# Checks that the DNS servers Docker hands to *build* containers actually
# answer, and offers to pin public resolvers in /etc/docker/daemon.json.
#
# Why this exists
# ---------------
# Docker copies the host's nameserver list into containers and into BuildKit
# build sandboxes *verbatim* — without the failover logic systemd-resolved has.
# When the first nameserver is unreachable (a campus/VPN DNS while you are off
# that network, a router that changed address, ...), the symptom is confusing:
#
#   - 'docker pull' keeps working, because dockerd itself resolves through the
#     host resolver, which fails over to the second server;
#   - every 'apt-get update' inside a build dies after ~75s with
#     "Temporary failure resolving 'deb.debian.org'".
#
# Usage:
#   ./bin/docker-dns-preflight.sh          # check, warn, always exit 0
#   ./bin/docker-dns-preflight.sh --strict # exit 1 when the first server fails
#   ./bin/docker-dns-preflight.sh --fix    # pin public DNS in daemon.json (sudo)
#
set -uo pipefail

DAEMON_JSON=/etc/docker/daemon.json
PUBLIC_DNS=("8.8.8.8" "1.1.1.1")

MODE=check
for arg in "$@"; do
    case "$arg" in
        --fix)    MODE=fix ;;
        --strict) MODE=strict ;;
        --help|-h)
            sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *) echo "ERROR: unknown option: $arg (see --help)" >&2; exit 1 ;;
    esac
done

if [ "$MODE" = "fix" ]; then
    echo "This will pin ${PUBLIC_DNS[*]} as the DNS servers Docker gives to"
    echo "containers and builds, by editing $DAEMON_JSON, and restart docker."
    read -r -p "Proceed? [y/N] " answer
    case "$answer" in
        [yY]*) ;;
        *) echo "Aborted."; exit 0 ;;
    esac

    tmp="$(mktemp)"
    python3 - "$DAEMON_JSON" "${PUBLIC_DNS[@]}" > "$tmp" <<'PYEOF'
import json, os, sys
path, *servers = sys.argv[1:]
cfg = {}
if os.path.exists(path):
    with open(path) as fh:
        text = fh.read().strip()
    if text:
        cfg = json.loads(text)
cfg["dns"] = servers
print(json.dumps(cfg, indent=2))
PYEOF
    if [ $? -ne 0 ]; then
        echo "ERROR: could not build the new $DAEMON_JSON (is the current one valid JSON?)" >&2
        rm -f "$tmp"
        exit 1
    fi

    echo "--- new $DAEMON_JSON ---"
    cat "$tmp"
    sudo install -m 0644 "$tmp" "$DAEMON_JSON"
    rm -f "$tmp"
    sudo systemctl restart docker
    echo "Done. Re-run this script without --fix to verify."
    exit 0
fi

python3 - "$DAEMON_JSON" <<'PYEOF'
import json, os, random, socket, struct, sys

daemon_json = sys.argv[1]

# 1. An explicit "dns" in daemon.json overrides everything else: trust it.
try:
    with open(daemon_json) as fh:
        text = fh.read().strip()
    cfg = json.loads(text) if text else {}
except (OSError, ValueError):
    cfg = {}
if cfg.get("dns"):
    print("DNS preflight: docker daemon pins DNS %s — ok." % ", ".join(cfg["dns"]))
    sys.exit(0)

# 2. Otherwise Docker uses the host resolv.conf, skipping one that only lists
#    loopback resolvers (the systemd-resolved stub) in favour of the real one.
def nameservers():
    for path in ("/etc/resolv.conf", "/run/systemd/resolve/resolv.conf"):
        found = []
        try:
            with open(path) as fh:
                for line in fh:
                    if line.startswith("nameserver"):
                        parts = line.split()
                        if len(parts) > 1:
                            found.append(parts[1])
        except OSError:
            continue
        if found and not all(n.startswith("127.") for n in found):
            return path, found
    return None, []

def answers(server, name="deb.debian.org", timeout=2.0):
    """Send one A query straight at the server; True if it replies."""
    qid = random.randint(0, 0xFFFF)
    packet = (struct.pack("!HHHHHH", qid, 0x0100, 1, 0, 0, 0)
              + b"".join(bytes([len(p)]) + p.encode() for p in name.split("."))
              + b"\x00" + struct.pack("!HH", 1, 1))
    family = socket.AF_INET6 if ":" in server else socket.AF_INET
    sock = socket.socket(family, socket.SOCK_DGRAM)
    sock.settimeout(timeout)
    try:
        sock.sendto(packet, (server, 53))
        reply, _ = sock.recvfrom(512)
        return reply[:2] == packet[:2]
    except OSError:
        return False
    finally:
        sock.close()

path, servers = nameservers()
if not servers:
    print("DNS preflight: could not read any nameserver — skipping check.")
    sys.exit(0)

results = [(s, answers(s)) for s in servers]
for server, ok in results:
    print("DNS preflight: %-18s %s" % (server, "ok" if ok else "NO ANSWER (2s)"))

first_ok = results[0][1]
any_ok = any(ok for _, ok in results)

if first_ok:
    sys.exit(0)

print("")
print("WARNING: the first nameserver Docker will give to build containers")
print("         (%s, from %s) did not answer." % (results[0][0], path))
if any_ok:
    print("         Image pulls will still work (dockerd resolves through the host),")
    print("         but 'apt-get update' inside a build will fail with")
    print("         \"Temporary failure resolving 'deb.debian.org'\".")
else:
    print("         No nameserver answered at all — check the network first.")
print("")
print("  Fix (pins public DNS for containers and builds, needs sudo):")
print("      ./bin/docker-dns-preflight.sh --fix")
print("")
sys.exit(3)
PYEOF
status=$?

if [ "$MODE" = "strict" ] && [ "$status" -ne 0 ]; then
    exit 1
fi
exit 0
