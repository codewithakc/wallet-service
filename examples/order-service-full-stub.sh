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

field = os.environ["FIELD_NAME"]
payload = json.loads(os.environ["PAYLOAD"])
value = payload.get(field)
if value is None:
    raise SystemExit(1)
print(value)
PY
}

api_call_with_status() {
  local method="$1"
  local path="$2"
  local token="$3"
  local body="${4:-}"

  if [[ -n "$body" ]]; then
    curl --silent --show-error --location \
      --request "$method" "${BASE_URL}${path}" \
      --header "Authorization: Bearer ${token}" \
      --header "Content-Type: application/json" \
      --data "$body" \
      --write-out $'\nHTTP_STATUS:%{http_code}'
  else
    curl --silent --show-error --location \
      --request "$method" "${BASE_URL}${path}" \
      --header "Authorization: Bearer ${token}" \
      --write-out $'\nHTTP_STATUS:%{http_code}'
  fi
}

print_http_result() {
  local result="$1"
  local status
  status="$(printf '%s' "$result" | sed -n 's/^HTTP_STATUS://p')"
  local body
  body="$(printf '%s' "$result" | sed '/^HTTP_STATUS:/d')"

  echo "HTTP status: ${status}"
  pretty_json "$body"
}

setup_wallet() {
  local initial_balance="$1"
  local response
  response="$(api_call_with_status "POST" "/wallets" "${CUSTOMER_TOKEN}" "{
    \"initialBalance\": ${initial_balance}
  }")"

  local status
  status="$(printf '%s' "$response" | sed -n 's/^HTTP_STATUS://p')"
  if [[ "$status" != "201" ]]; then
    echo "Failed to create wallet for setup."
    print_http_result "$response"
    exit 1
  fi

  printf '%s' "$response" | sed '/^HTTP_STATUS:/d'
}

place_order() {
  local wallet_id="$1"
  local order_id="$2"
  local reference_id="$3"

  api_call_with_status "POST" "/wallets/${wallet_id}/deduct" "${ORDER_SERVICE_TOKEN}" "{
    \"idempotencyKey\": \"${order_id}\",
    \"referenceId\": \"${reference_id}\"
  }"
}

get_wallet() {
  local wallet_id="$1"
  api_call_with_status "GET" "/wallets/${wallet_id}" "${CUSTOMER_TOKEN}"
}

get_transactions() {
  local wallet_id="$1"
  api_call_with_status "GET" "/wallets/${wallet_id}/transactions" "${CUSTOMER_TOKEN}"
}

concurrent_same_order_retry() {
  local wallet_id="$1"
  local order_id="$2"
  local parallelism="$3"
  local temp_dir
  temp_dir="$(mktemp -d)"
  local pids=()

  for worker in $(seq 1 "$parallelism"); do
    (
      place_order "$wallet_id" "$order_id" "${order_id}-retry-${worker}" > "${temp_dir}/response-${worker}.txt"
    ) &
    pids+=("$!")
  done

  for pid in "${pids[@]}"; do
    wait "$pid"
  done

  for worker in $(seq 1 "$parallelism"); do
    echo "Concurrent retry response ${worker}:"
    print_http_result "$(cat "${temp_dir}/response-${worker}.txt")"
  done

  rm -rf "${temp_dir}"
}

concurrent_distinct_orders() {
  local wallet_id="$1"
  local order_prefix="$2"
  local parallelism="$3"
  local temp_dir
  temp_dir="$(mktemp -d)"
  local pids=()

  for worker in $(seq 1 "$parallelism"); do
    (
      place_order "$wallet_id" "${order_prefix}-${worker}" "${order_prefix}-${worker}" > "${temp_dir}/response-${worker}.txt"
    ) &
    pids+=("$!")
  done

  for pid in "${pids[@]}"; do
    wait "$pid"
  done

  local success_count=0
  local reject_count=0

  for worker in $(seq 1 "$parallelism"); do
    local response
    response="$(cat "${temp_dir}/response-${worker}.txt")"
    local status
    status="$(printf '%s' "$response" | sed -n 's/^HTTP_STATUS://p')"
    if [[ "$status" == "200" ]]; then
      success_count=$((success_count + 1))
    else
      reject_count=$((reject_count + 1))
    fi

    echo "Distinct order response ${worker}:"
    print_http_result "$response"
  done

  echo "Accepted orders: ${success_count}"
  echo "Rejected orders: ${reject_count}"

  rm -rf "${temp_dir}"
}

print_section "Order service happy-path setup"
HAPPY_WALLET_RESPONSE="$(setup_wallet 300)"
pretty_json "$HAPPY_WALLET_RESPONSE"
HAPPY_WALLET_ID="$(extract_json_field "$HAPPY_WALLET_RESPONSE" "walletId")"

print_section "Place order: ORDER-1001"
print_http_result "$(place_order "${HAPPY_WALLET_ID}" "ORDER-1001" "ORDER-1001")"

print_section "Retry same order: ORDER-1001"
print_http_result "$(place_order "${HAPPY_WALLET_ID}" "ORDER-1001" "ORDER-1001-retry")"

print_section "Wallet after order and retry"
print_http_result "$(get_wallet "${HAPPY_WALLET_ID}")"

print_section "Ledger after order and retry"
print_http_result "$(get_transactions "${HAPPY_WALLET_ID}")"

print_section "Order service insufficient-funds setup"
LOW_BALANCE_WALLET_RESPONSE="$(setup_wallet 50)"
pretty_json "$LOW_BALANCE_WALLET_RESPONSE"
LOW_BALANCE_WALLET_ID="$(extract_json_field "$LOW_BALANCE_WALLET_RESPONSE" "walletId")"

print_section "Place order that should be rejected: ORDER-2001"
print_http_result "$(place_order "${LOW_BALANCE_WALLET_ID}" "ORDER-2001" "ORDER-2001")"

print_section "Retry rejected order: ORDER-2001"
print_http_result "$(place_order "${LOW_BALANCE_WALLET_ID}" "ORDER-2001" "ORDER-2001-retry")"

print_section "Concurrent retry storm setup"
CONCURRENT_RETRY_WALLET_RESPONSE="$(setup_wallet 500)"
pretty_json "$CONCURRENT_RETRY_WALLET_RESPONSE"
CONCURRENT_RETRY_WALLET_ID="$(extract_json_field "$CONCURRENT_RETRY_WALLET_RESPONSE" "walletId")"

print_section "Concurrent retries for the same order ID"
concurrent_same_order_retry "${CONCURRENT_RETRY_WALLET_ID}" "ORDER-3001" 5

print_section "Wallet after concurrent same-order retries"
print_http_result "$(get_wallet "${CONCURRENT_RETRY_WALLET_ID}")"

print_section "Concurrent distinct-order setup"
CONCURRENT_DISTINCT_WALLET_RESPONSE="$(setup_wallet 200)"
pretty_json "$CONCURRENT_DISTINCT_WALLET_RESPONSE"
CONCURRENT_DISTINCT_WALLET_ID="$(extract_json_field "$CONCURRENT_DISTINCT_WALLET_RESPONSE" "walletId")"

print_section "Concurrent different orders against limited balance"
concurrent_distinct_orders "${CONCURRENT_DISTINCT_WALLET_ID}" "ORDER-4001" 5

print_section "Wallet after concurrent distinct orders"
print_http_result "$(get_wallet "${CONCURRENT_DISTINCT_WALLET_ID}")"

print_section "Ledger after concurrent distinct orders"
print_http_result "$(get_transactions "${CONCURRENT_DISTINCT_WALLET_ID}")"

echo
echo "Full order-service stub completed."
