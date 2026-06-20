#!/usr/bin/env bash
set -euo pipefail

app_metrics_url="${APP_METRICS_URL:-http://localhost:8080/actuator/prometheus}"
prometheus_url="${PROMETHEUS_URL:-http://localhost:9090}"
grafana_url="${GRAFANA_URL:-http://localhost:3000}"

echo "[1/4] Spring Boot Prometheus endpoint"
metrics="$(curl --fail --silent --show-error "${app_metrics_url}")"
grep --quiet '^reboot_expiration_running ' <<<"${metrics}"
grep --quiet '^jvm_memory_used_bytes' <<<"${metrics}"
grep --quiet '^hikaricp_connections' <<<"${metrics}"

echo "[2/4] Prometheus readiness"
curl --fail --silent --show-error "${prometheus_url}/-/ready"
echo

echo "[3/4] RebootFocus scrape target"
curl --fail --silent --show-error \
  "${prometheus_url}/api/v1/query?query=up%7Bjob%3D%22rebootfocus-api%22%7D%20%3D%3D%201" \
  | grep --fixed-strings '"value":['

echo "[4/4] Grafana health"
curl --fail --silent --show-error "${grafana_url}/api/health"
echo

echo "Observability stack is reachable."
