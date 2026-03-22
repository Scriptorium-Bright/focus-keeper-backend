from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.bash import BashOperator


DEFAULT_ARGS = {
    "owner": "rebootfocus",
    "depends_on_past": False,
    "retries": 2,
    "retry_delay": timedelta(minutes=5),
}


with DAG(
    dag_id="daily_kpi_pipeline",
    default_args=DEFAULT_ARGS,
    start_date=datetime(2026, 3, 22),
    schedule="0 1 * * *",
    catchup=False,
    tags=["phase14", "watchtower", "rough"],
) as dag:
    generate_daily_kpi = BashOperator(
        task_id="generate_daily_kpi",
        execution_timeout=timedelta(minutes=10),
        bash_command="""
        curl -fsS -X POST "${API_BASE_URL:-http://host.docker.internal:8080}/api/v1/recovery/analytics/kpis/daily" \
          -H "Content-Type: application/json" \
          -d "{\"userId\":\"${API_USER_ID:-demo-analytics-user}\",\"metricDate\":\"{{ ds }}\"}"
        """,
    )

    fetch_watermark = BashOperator(
        task_id="fetch_watermark",
        execution_timeout=timedelta(minutes=5),
        bash_command="""
        curl -fsS "${API_BASE_URL:-http://host.docker.internal:8080}/api/v1/recovery/analytics/kpis/daily/watermark?userId=${API_USER_ID:-demo-analytics-user}"
        """,
    )

    generate_daily_kpi >> fetch_watermark
