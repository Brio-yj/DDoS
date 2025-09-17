# DDos에서 살아남기

DDos 공격 환경에서 **세션 기반 인증**과 **JWT 기반 인증**의 내구성을 비교 검증하기 위한 실험용 프로젝트입니다. 두 종류의 인증 서버와 이를 소비하는 API 서버 2종, 그리고 공통 MySQL 저장소와 Python 부하 발생 스크립트로 구성되어 있습니다.

## 아키텍처
<img width="1131" height="757" alt="아키텍처" src="https://github.com/user-attachments/assets/bedae22f-b73d-48f2-acf3-e92581364858" />

## 구성 요소

| 모듈 | 설명 | 주요 포트 |
|------|------|-----------|
| `auth-session` | Spring Boot + Spring Security 세션 기반 로그인/회원가입 서버 | 8081 |
| `auth-jwt` | Spring Boot + Spring Security + JWT 인증 서버 (Access/Refresh 분리) | 8082 |
| `api-a` | 카탈로그 조회 API. JWT 검증만 수행 | 8083 |
| `api-b` | 주문 생성 API. JWT 검증 후 MySQL과 통신 | 8084 |
| `mysql` | 단일 MySQL 인스턴스 | 3306 |
| `loadgen.py` | Python 부하 스크립트 (100~500 스레드 가변) | - |
