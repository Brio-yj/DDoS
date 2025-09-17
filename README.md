# 분산 인증 아키텍처 실험 프로젝트

대규모 트래픽 환경에서 **세션 기반 인증**과 **JWT 기반 인증**의 내구성을 비교 검증하기 위한 실험용 프로젝트입니다. 두 종류의 인증 서버와 이를 소비하는 API 서버 2종, 그리고 공통 MySQL 저장소와 Python 부하 발생 스크립트로 구성되어 있습니다.

## 구성 요소

| 모듈 | 설명 | 주요 포트 |
|------|------|-----------|
| `auth-session` | Spring Boot + Spring Security 세션 기반 로그인/회원가입 서버 | 8081 |
| `auth-jwt` | Spring Boot + Spring Security + JWT 인증 서버 (Access/Refresh 분리) | 8082 |
| `api-a` | 카탈로그 조회 API. JWT 검증만 수행 | 8083 |
| `api-b` | 주문 생성 API. JWT 검증 후 MySQL과 통신 | 8084 |
| `mysql` | 단일 MySQL 8.x 인스턴스 | 3306 |
| `loadgen.py` | Python 3.10+ 부하 스크립트 (100~500 스레드 가변) | - |

모든 Spring Boot 애플리케이션은 JVM 옵션 `-Xms1g -Xmx1g` 를 고정 적용해 동일한 자원 조건에서 비교합니다. 각 서비스에는 `RequestMetricsFilter` 가 적용되어 10초 간격으로 요청수, 에러율, 평균/95/99 퍼센타일 지연을 로그로 출력합니다.

## 데이터 모델

공통 사용자/권한/토큰, 카탈로그/주문 스키마를 단일 MySQL 스키마(`ddd`)에서 공유합니다. 엔터티/리포지토리는 `common` 모듈에 정의되어 각 서비스에서 재사용합니다.

- 사용자, 권한, 리프레시 토큰: `USERS`, `ROLES`, `USER_ROLES`, `REFRESH_TOKENS`
- 제품, 주문, 주문 항목: `PRODUCTS`, `ORDERS`, `ORDER_ITEMS`

애플리케이션 기동 시 기본 역할(`ROLE_USER`)과 샘플 상품 3종이 자동 생성됩니다.

## 사전 준비

- **Java 17**
- **Gradle 8.5+** (저장소에 wrapper가 포함되어 있지 않습니다. `gradle` 명령 사용)
- **Docker 24+**, **Docker Compose v2**
- **Python 3.10+** 및 `requests` 패키지 (`pip install requests`)

## 빌드 및 실행

### 1. 로컬 빌드 (선택)

```bash
gradle clean build
```

### 2. Docker Compose로 전체 스택 구동

```bash
docker compose up --build
```

초기 기동 시 MySQL 스키마 생성과 애플리케이션 빌드 때문에 수 분이 소요될 수 있습니다. 모든 컨테이너가 준비되면 다음 엔드포인트가 활성화됩니다.

- `http://localhost:8081/auth/...` (세션 인증)
- `http://localhost:8082/auth/...` (JWT 인증)
- `http://localhost:8083/api-a/items`
- `http://localhost:8084/api-b/orders`

각 애플리케이션 로그에서 `request-metrics` 라인을 통해 10초 단위 성능 요약을 확인할 수 있습니다. JVM 옵션 로그에 `-Xms1g -Xmx1g` 적용 여부가 표시됩니다.

## Python 부하 스크립트 사용법

`loadgen.py` 는 두 가지 시나리오를 지원합니다.

- `--target auth-session` : 세션 기반 로그인/회원가입 서버에 집중 부하
- `--target auth-jwt` : JWT 서버 로그인 + `api-a`, `api-b` 호출을 포함한 통합 부하

주요 옵션:

| 옵션 | 설명 | 기본값 |
|------|------|---------|
| `--threads` | 동시 워커 수 | 100 |
| `--duration` | 테스트 지속 시간(초) | 60 |
| `--rps` | 목표 전체 RPS (로그인/주문 요청 기준) | 200 |
| `--signup` | 테스트 전 사용자 계정 선행 생성 | 비활성 |
| `--no-prelogin` | JWT 모드에서 사전 로그인 생략 | 활성 |
| `--no-reuse-token` | JWT 모드에서 매 반복마다 로그인 | 토큰 재사용 |
| `--seed` | 난수 시드 (주문 페이로드 일관성) | 무작위 |

예시 실행:

```bash
# 세션 서버에 300 RPS, 300 스레드로 120초 부하
python loadgen.py --target auth-session --threads 300 --duration 120 --rps 300 --signup

# JWT 서버 + API에 200 스레드 부하, 기존 토큰 재사용 시나리오
python loadgen.py --target auth-jwt --threads 200 --duration 180 --rps 250 --signup
```

테스트 종료 후 콘솔에 각 엔드포인트별 요청 수, 에러율, 평균/퍼센타일 지연이 요약되어 출력됩니다. `reports/test-report-template.md` 를 활용해 결과를 정리할 수 있습니다.

## 재현 시나리오 가이드

1. **단계 A - 세션 인증 병목 관찰**
   - `loadgen.py --target auth-session --threads 500 --duration 180 --rps 400 --signup`
   - 로그인 요청이 급증하면 `auth-session` 로그에 에러율과 지연이 증가하는 것을 확인합니다.
   - 세션 생성이 지연되면서 실제 서비스(API 서버)에 대한 접근이 불가한 상황을 재현할 수 있습니다.

2. **단계 B - JWT 토큰 지속성 검증**
   - 사전에 JWT 모드로 로그인하여 Access/Refresh 토큰을 확보한 뒤 부하를 유발합니다.
   - `loadgen.py --target auth-jwt --threads 300 --duration 180 --rps 350 --signup`
   - JWT 서버가 과부하 상태여도 `api-a`, `api-b` 로그의 `errorRate` 가 낮게 유지되는지 확인합니다.
   - 필요 시 `--no-reuse-token` 옵션으로 로그인 요청까지 포함한 부하를 줄 수 있습니다.

각 단계마다 동일한 사용자 데이터/요청 패턴을 사용하고, 로그에 기록되는 RPS/지연/에러율 지표를 비교합니다.

## 프로젝트 구조

```
common/               # 공통 엔터티, JPA 리포지토리, JWT 유틸, 요청 메트릭 필터
auth-session/         # 세션 기반 인증 서버
auth-jwt/             # JWT 기반 인증 서버 (Access/Refresh, 토큰 저장)
api-a/                # 카탈로그 API (JWT 검증)
api-b/                # 주문 API (JWT 검증, 주문 생성)
reports/test-report-template.md
loadgen.py
```

## 개발 / 테스트 메모

- `gradle test` 로 각 모듈 단위 테스트를 실행할 수 있습니다.
- 로컬 환경에서 빠르게 확인하려면 `gradle :auth-jwt:bootRun` 등 개별 모듈 기동도 가능합니다.
- Docker Compose 구동 시 MySQL 데이터는 `mysql-data` 볼륨에 유지됩니다. 초기화가 필요하면 `docker volume rm ddd_mysql-data` 실행 후 재기동합니다.
- JWT 비밀키(`JWT_SECRET`)는 Docker Compose 환경 변수로 통일되어 있어, `auth-jwt`, `api-a`, `api-b` 가 동일 값을 공유합니다.

## 부하 테스트 결과 정리

`reports/test-report-template.md` 에 제공된 표를 사용하여 세션 대비 JWT 방식의 에러율, 지연, RPS를 비교하고, JVM 1GB 제약에서의 병목 지점을 분석하세요.

