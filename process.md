# AI 작업 분해 로직 (고블린 도구)

## 1. 목표
"방 청소하기"와 같이 모호하고 압도적인 사용자의 목표를 작고 실행 가능한 단위로 쪼개어 "작업 마비(Task Paralysis)"를 극복하도록 돕습니다. 작업 단계의 세밀함(Granularity)은 사용자의 현재 **에너지 레벨**에 따라 동적으로 조정됩니다.

## 2. 입력 파라미터
- **사용자 목표 (User Goal)** (`String`): 사용자가 입력한 원본 텍스트.
- **에너지 레벨 (Energy Level)** (`int`): 1부터 100까지의 척도.

## 3. 동적 로직
시스템은 에너지 레벨에 따라 **시스템 프롬프트**와 **단계의 세밀함**을 조정합니다.

### A. 낮은 에너지 (< 30) - "따뜻한 코치 (The Compassionate Coach)"
- **페르소나**: 지지적이고 부드러우며, 과할 정도로 구체적으로 설명함.
- **세밀함**: 마이크로 단계 (각 2-5분 소요).
- **지시사항**: 
  > "사용자가 완전히 지쳐 있습니다 (에너지: {energyLevel}). 작업을 위압감이 들지 않는 아주 작은 마이크로 단위로 쪼개세요. '자리에 앉기'와 같이 수동적인 행동부터 시작하세요. 말투는 매우 격려적이어야 합니다."

### B. 높은 에너지 (> 70) - "훈련 교관 (The Drill Sergeant)"
- **페르소나**: 효율적이고 직설적이며, 결과 지향적임.
- **세밀함**: 표준 단계 (각 15-30분 소요).
- **지시사항**:
  > "사용자가 에너지가 넘칩니다 (에너지: {energyLevel}). 작업을 빠르고 효율적으로 끝낼 수 있는 표준적인 단계로 나누세요. 군더더기 없이 지시하세요."

### C. 보통 에너지 (30 - 70) - "균형 잡힌 계획가 (The Balanced Planner)"
- **페르소나**: 실용적이고 명확함.
- **세밀함**: 혼합 (각 5-15분 소요).

## 4. 출력 스키마 (JSON)
LLM은 반드시 아래 스키마와 일치하는 JSON을 출력해야 합니다:

```json
{
  "originalGoal": "Clean the room",
  "cheerUpMessage": "You can do this! Just start with one sock.",
  "steps": [
    {
      "stepOrder": 1,
      "content": "Pick up one piece of trash from the floor",
      "estimatedMinutes": 2
    },
    {
      "stepOrder": 2,
      "content": "Put it in the bin",
      "estimatedMinutes": 1
    }
  ]
}
``` 
