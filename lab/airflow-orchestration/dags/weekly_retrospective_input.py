from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.bash import BashOperator


DEFAULT_ARGS = {
    "owner": "rebootfocus",
    "depends_on_past": False,
    "retries": 1,
    "retry_delay": timedelta(minutes=5),
}


with DAG(
    dag_id="weekly_retrospective_input",
    default_args=DEFAULT_ARGS,
    start_date=datetime(2026, 3, 22),
    schedule="0 6 * * MON",
    catchup=False,
    tags=["phase14", "watchtower", "rough"],
) as dag:
    generate_weekly_retrospective = BashOperator(
        task_id="generate_weekly_retrospective",
        execution_timeout=timedelta(minutes=10),
        bash_command="""
        curl -fsS -X POST "${API_BASE_URL:-http://host.docker.internal:8080}/api/v1/recovery/retrospectives/weekly" \
          -H "Content-Type: application/json" \
          -d "{\"userId\":\"${API_USER_ID:-demo-analytics-user}\",\"weekStart\":\"{{ ds }}\"}"
        """,
    )

    fetch_batch_overview = BashOperator(
        task_id="fetch_batch_overview",
        execution_timeout=timedelta(minutes=5),
        bash_command="""
        curl -fsS "${API_BASE_URL:-http://host.docker.internal:8080}/api/v1/ops/overview/batch?userId=${API_USER_ID:-demo-analytics-user}&metricDate={{ ds }}"
        """,
    )

    generate_weekly_retrospective >> fetch_batch_overview
