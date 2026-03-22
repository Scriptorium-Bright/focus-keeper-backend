from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.bash import BashOperator


DEFAULT_ARGS = {
    "owner": "rebootfocus",
    "depends_on_past": False,
    "retries": 1,
    "retry_delay": timedelta(minutes=10),
}


with DAG(
    dag_id="backfill_reprocess",
    default_args=DEFAULT_ARGS,
    start_date=datetime(2026, 3, 22),
    schedule=None,
    catchup=False,
    tags=["phase14", "watchtower", "rough"],
) as dag:
    run_backfill = BashOperator(
        task_id="run_backfill",
        execution_timeout=timedelta(minutes=20),
        bash_command="""
        curl -fsS -X POST "${API_BASE_URL:-http://host.docker.internal:8080}/api/v1/recovery/analytics/kpis/daily/backfill" \
          -H "Content-Type: application/json" \
          -d "{\"userId\":\"${API_USER_ID:-demo-analytics-user}\",\"startDate\":\"${API_START_DATE:-2026-03-17}\",\"endDate\":\"${API_END_DATE:-2026-03-21}\"}"
        """,
    )

    fetch_alerts = BashOperator(
        task_id="fetch_alerts",
        execution_timeout=timedelta(minutes=5),
        bash_command="""
        curl -fsS "${API_BASE_URL:-http://host.docker.internal:8080}/api/v1/ops/alerts?userId=${API_USER_ID:-demo-analytics-user}&activeOnly=true"
        """,
    )

    run_backfill >> fetch_alerts
