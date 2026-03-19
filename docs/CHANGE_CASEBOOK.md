# Change Casebook

> 목적: 구현/리팩토링 중 나온 문제 제기와 수정 내용을 `처음 상태 -> 변경 이유 -> 변경 내용 -> 결과 -> 포트폴리오 가능성` 형식으로 누적 기록한다.

## 기록 규칙

- 이 문서는 `Phase` 전체 회고가 아니라, 개별 변경 사례를 남기는 문서다.
- 사용자 문제 제기, 코드 리뷰 포인트, 리팩토링 계기를 짧은 사례 단위로 적는다.
- 포트폴리오 점수는 `1~10`으로 적고, 왜 그 점수인지 한 줄로 설명한다.

## 템플릿

```text
## C-XXX 제목

- Date:
- Area:
- Trigger:
- Initially:
- Why Changed:
- What Changed:
- Result:
- Portfolio Suitability:
- Portfolio Score:
- Why This Score:
- Evidence:
```

## Cases

## C-001 TimeboxService 책임 분리

- Date: 2026-03-19
- Area: `recovery/planning`
- Trigger: `TimeboxService`가 orchestration, 입력 검증, overlap 판정까지 모두 들고 있어 service가 무거워졌다는 문제 제기
- Initially:
  - `TimeboxService` 하나가 첫 복귀 블록 검증, Big3 포함 여부 검증, overlap 판정, timebox 생성까지 직접 처리했다.
  - overlap 규칙이 서비스 메서드 안에 있어 독립 검증 포인트가 약했다.
- Why Changed:
  - 서비스가 "조합"보다 "세부 규칙"까지 같이 들고 있으면 책임 경계가 흐려진다.
  - overlap 판정은 독립 규칙으로 분리했을 때 테스트성과 설명력이 좋아진다.
- What Changed:
  - `TimeboxAllocationValidator`로 첫 복귀 블록/Big3 포함 여부 검증을 분리했다.
  - `TimeboxOverlapValidator`로 시간 충돌 판정을 분리했다.
  - `TimeboxCommand`를 top-level 타입으로 올려 컨트롤러/서비스/validator가 같은 입력 모델을 보게 했다.
- Result:
  - `TimeboxService`는 Big3 조회, timebox 구체화, 저장 orchestration 중심으로 축소됐다.
  - overlap 규칙과 allocation 검증을 단위 테스트로 독립 검증할 수 있게 됐다.
- Portfolio Suitability: 가능
- Portfolio Score: 6/10
- Why This Score:
  - 구조 개선 사례로는 좋지만, 사용자 가치나 시스템 성능을 크게 바꾸는 수준은 아니라서 중간 점수다.
- Evidence:
  - 코드: `TimeboxService`, `TimeboxAllocationValidator`, `TimeboxOverlapValidator`
  - 테스트: `TimeboxAllocationValidatorTest`, `TimeboxOverlapValidatorTest`
