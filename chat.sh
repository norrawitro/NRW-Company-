#!/usr/bin/env bash
# NRW Company chat: encrypted messages in a public repo.
# Each message = one line of base64 (AES-256-CBC, PBKDF2, OpenSSL).
# Key: env NRW_CHAT_KEY, or file ~/.nrw_chat_key (never commit the key).
set -euo pipefail
cd "$(dirname "$0")"

key() {
  if [ -n "${NRW_CHAT_KEY:-}" ]; then printf '%s' "$NRW_CHAT_KEY"
  elif [ -f "$HOME/.nrw_chat_key" ]; then tr -d '\n' < "$HOME/.nrw_chat_key"
  else echo "no key: set NRW_CHAT_KEY or create ~/.nrw_chat_key" >&2; exit 2; fi
}
enc() { openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -a -A -pass "pass:$(key)"; }
dec() { openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -a -A -pass "pass:$(key)"; }

room_file() {
  local f="rooms/$1.chat"
  [ -f "$f" ] || { echo "no room: $1 (see: ./chat.sh rooms)" >&2; exit 3; }
  printf '%s' "$f"
}

case "${1:-}" in
  rooms)
    ls rooms | sed 's/\.chat$//' ;;
  read)   # ./chat.sh read <room> [last N]
    key >/dev/null; f=$(room_file "${2:?room}"); n=${3:-0}
    if [ "$n" -gt 0 ]; then lines=$(grep -v '^#' "$f" | tail -n "$n" || true); else lines=$(grep -v '^#' "$f" || true); fi
    while IFS= read -r l; do
      [ -n "$l" ] || continue
      if out=$(printf '%s' "$l" | dec 2>/dev/null); then printf '%s\n' "$out"; else echo "[cannot decrypt: wrong key?]"; fi
    done <<< "$lines" ;;
  post)   # ./chat.sh post <room> <from> <message...>
    key >/dev/null; f=$(room_file "${2:?room}"); from=${3:?from}; shift 3
    [ -n "${from// /}" ] || { echo "sender name is required" >&2; exit 4; }
    msg="[$(TZ=Asia/Bangkok date '+%Y-%m-%d %H:%M')] $from : $*"
    printf '%s' "$msg" | enc >> "$f"; echo >> "$f"
    echo "posted to ${f}. Now: git add $f && git commit -m 'chat' && git push" ;;
  *)
    echo "usage: ./chat.sh rooms | read <room> [N] | post <room> <from> <message>" ; exit 1 ;;
esac
