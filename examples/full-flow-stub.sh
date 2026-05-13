#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
CUSTOMER_ID="${CUSTOMER_ID:-demo-customer}"
CUSTOMER_TOKEN_PREFIX="${CUSTOMER_TOKEN_PREFIX:-customer-token}"
CUSTOMER_TOKEN="${CUSTOMER_TOKEN:-${CUSTOMER_TOKEN_PREFIX}:${CUSTOMER_ID}}"
ORDER_SERVICE_TOKEN="${ORDER_SERVICE_TOKEN:-order-service-token}"
PYTHON_BIN="${PYTHON_BIN:-python3}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1"
    exit 1
  fi
}

require_command curl
require_command "$PYTHON_BIN"

print_section() {
  echo
  echo "================================================================"
  echo "$1"
  echo "================================================================"
}

pretty_json() {
  local payload="$1"
  PAYLOAD="$payload" "$PYTHON_BIN" - <<'PY'
import json
import os
import sys

payload = os.environ.get("PAYLOAD", "").strip()
if not payload:
    print(payload)
    raise SystemExit(0)

try:
    parsed = json.loads(payload)
except json.JSONDecodeError:
    print(payload)
else:
    print(json.dumps(parsed, indent=2))
PY
}

extract_json_field() {
  local payload="$1"
  local field="$2"
  PAYLOAD="$payload" FIELD_NAME="$field" "$PYTHON_BIN" - <<'PY'
import json
import os
import sys

field = os.environ["FIELD_NAME"]
payload = json.loads(os.environ["PAYLOAD"])
value = payload.get(field)
if value is None:
    raise SystemExit(1)
print(value)
PY
}

api_call() {
  local method="$1"
  local path="$2"
  local token="$3"
  local body="${4:-}"

  if [[ -n "$body" ]]; then
    curl --silent --show-error --location \
      --request "$method" "${BASE_URL}${path}" \
      --header "Authorization: Bearer ${token}" \
      --header "Content-Type: application/json" \
      --data "$body"
  else
    curl --silent --show-error --location \
      --request "$method" "${BASE_URL}${path}" \
      --header "Authorization: Bearer ${token}"
  fi
}

concurrent_same_key_test() {
  local wallet_id="$1"
  local order_id="$2"
  local amount="$3"
  local parallelism="$4"

  print_section "Concurrent idempotency test for wallet ${wallet_id}"
  local temp_dir
  temp_dir="$(mktemp -d)"
  local pids=()

  for i in $(seq 1 "$parallelism"); do
    (
      api_call "POST" "/wallets/${wallet_id}/deduct" "${ORDER_SERVICE_TOKEN}" "{
        \"idempotencyKey\": \"${order_id}\",
        \"amount\": ${amount},
        \"referenceId\": \"${order_id}-worker-${i}\"
      }" > "${temp_dir}/response-${i}.json"
    ) &
    pids+=("$!")
  done

  for pid in "${pids[@]}"; do
    wait "$pid"
  done

  for i in $(seq 1 "$parallelism"); do
    echo "Response ${i}:"
    pretty_json "$(cat "${temp_dir}/response-${i}.json")"
  done

  rm -rf "${temp_dir}"
}

print_section "Create primary wallet"
CREATE_RESPONSE="$(api_call "POST" "/wallets" "${CUSTOMER_TOKEN}" '{
  "initialBalance": 500
}')"
pretty_json "$CREATE_RESPONSE"
WALLET_ID="$(extract_json_field "$CREATE_RESPONSE" "walletId")"

print_section "Get wallet"
pretty_json "$(api_call "GET" "/wallets/${WALLET_ID}" "${CUSTOMER_TOKEN}")"

print_section "Get wallet balance"
pretty_json "$(api_call "GET" "/wallets/${WALLET_ID}/balance" "${CUSTOMER_TOKEN}")"

print_section "Top up wallet"
pretty_json "$(api_call "POST" "/wallets/${WALLET_ID}/topup" "${CUSTOMER_TOKEN}" '{
  "amount": 300,
  "referenceId": "topup-e2e-1"
}')"

print_section "Get wallet after top-up"
pretty_json "$(api_call "GET" "/wallets/${WALLET_ID}" "${CUSTOMER_TOKEN}")"

print_section "Deduct once"
pretty_json "$(api_call "POST" "/wallets/${WALLET_ID}/deduct" "${ORDER_SERVICE_TOKEN}" '{
  "idempotencyKey": "order-e2e-1",
  "amount": 125,
  "referenceId": "order-e2e-1"
}')"

print_section "Replay same deduct request"
pretty_json "$(api_call "POST" "/wallets/${WALLET_ID}/deduct" "${ORDER_SERVICE_TOKEN}" '{
  "idempotencyKey": "order-e2e-1",
  "amount": 125,
  "referenceId": "order-e2e-1-retry"
}')"

print_section "Get wallet transactions"
pretty_json "$(api_call "GET" "/wallets/${WALLET_ID}/transactions" "${CUSTOMER_TOKEN}")"

print_section "Create wallet for concurrent same-key idempotency test"
CONCURRENT_CREATE_RESPONSE="$(api_call "POST" "/wallets" "${CUSTOMER_TOKEN}" '{
  "initialBalance": 500
}')"
pretty_json "$CONCURRENT_CREATE_RESPONSE"
CONCURRENT_WALLET_ID="$(extract_json_field "$CONCURRENT_CREATE_RESPONSE" "walletId")"

concurrent_same_key_test "$CONCURRENT_WALLET_ID" "order-concurrent-1" 100 5

print_section "Concurrent wallet final state"
pretty_json "$(api_call "GET" "/wallets/${CONCURRENT_WALLET_ID}" "${CUSTOMER_TOKEN}")"

print_section "Concurrent wallet transactions"
pretty_json "$(api_call "GET" "/wallets/${CONCURRENT_WALLET_ID}/transactions" "${CUSTOMER_TOKEN}")"

echo
echo "Full flow completed."
