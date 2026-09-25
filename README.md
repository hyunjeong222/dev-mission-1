## 실행 방법

### 요구 사항
- JDK 25 (build.gradle.kts 의 toolchain 설정과 일치하는지 확인)
- 별도의 DB 설치는 필요 없음 (H2 파일 DB를 사용하며, 실행하면 프로젝트 루트에 `db_dev.mv.db`가 생성됨)

### 실행
```bash
cp .env.example .env   # JWT_SECRET 값을 32자 이상 임의 문자열로 채우기
./gradlew bootRun      # Windows: .\gradlew bootRun
```

로그에 `Started DevMission2Application`이 출력되면 실행이 완료된 것입니다. 서버는 `http://localhost:8080`에서 동작하며, 종료는 `Ctrl + C`입니다.