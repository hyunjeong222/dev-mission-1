## 실행 방법

### 요구 사항
- JDK 25 (build.gradle.kts 의 toolchain 설정과 일치하는지 확인)
- 별도의 DB 설치는 필요 없음 (H2 파일 DB를 사용하며, 실행하면 프로젝트 루트에 `db_dev.mv.db`가 생성됨)

### 실행
```bash
./gradlew bootRun      # Windows: .\gradlew bootRun
```

`dev` 프로필이 기본으로 적용되어 별도 옵션 없이 실행하면 됩니다.
로그에 `Started DevMission2Application`이 출력되면 실행이 완료된 것입니다. 서버는 `http://localhost:8080`에서 동작하며, 종료는 `Ctrl + C`입니다.

### DB 확인
- H2 콘솔: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:./db_dev;MODE=MySQL`
- 계정: `sa` / 비밀번호 없음
- 앱을 껐다 켜도 초기 데이터가 중복 생성되지 않습니다 (회원 존재 여부를 먼저 확인하고 초기화를 건너뜀).

### 보안 팁 API 단독 확인
```
GET http://localhost:8080/api/v1/member/members/randomSecureTip
```

## 2. 구조 설명

### 2-1. 모듈 구성 (패키지 기준)

```
com.back
 ├─ boundedContext
 │   ├─ member          # 회원 모듈
 │   │   ├─ in          # controller, eventListener
 │   │   ├─ app         # facade, usecase
 │   │   ├─ domain      # 엔티티, 정책(MemberPolicy)
 │   │   └─ out         # repository
 │   └─ post            # 게시판 모듈
 │       ├─ in / app / domain / out (member와 동일 구조)
 ├─ shared
 │   ├─ member          # 회원 모듈이 공개하는 계약 (DTO, Event, ApiClient)
 │   └─ post
 └─ global              # 어느 모듈에도 속하지 않는 공통 코드 (BaseEntity, RsData, DomainException 등)
```

같은 모듈 안에서는 `in → app(facade → usecase) → domain → out` 순서로 요청이 흐르도록 통일했습니다. 새 기능을 추가할 때 "이건 어디에 두지?"라는 고민을 줄이기 위한 구조입니다.

### 2-2. 이벤트와 HTTP API를 구분한 기준

두 모듈이 서로의 클래스를 직접 참조하지 않도록, 모듈 간 통신은 아래 두 가지로만 제한했습니다.

| 상황 | 방법 | 이 프로젝트의 예 |
|---|---|---|
| "이런 일이 일어났다"를 알리기만 하면 될 때 (결과를 돌려받을 필요 없음) | Spring Event | 글 작성 → `PostCreatedEvent` → 회원 활동점수 증가 / 회원 가입 → `MemberJoinedEvent` → `PostMember` 복제 생성 |
| 지금 당장 값을 받아야 다음 로직을 진행할 수 있을 때 (조회) | HTTP API 호출 | 글 작성 시 회원 모듈에서 "보안 팁" 문자열을 받아와 응답 메시지에 포함 → `MemberApiClient`가 `GET /api/v1/member/members/randomSecureTip` 호출 |

이벤트로 처리하면 안 되는 경우(즉시 응답이 필요한 조회, 반드시 같이 성공/실패해야 하는 작업)는 같은 트랜잭션 안에서 직접 처리하거나 API 호출로 뺐습니다.

### 2-3. 회원 복제(Replica) 흐름

게시판 모듈이 작성자 정보를 알아야 하지만, 회원 모듈의 엔티티를 직접 참조하면 두 모듈이 같은 코드베이스/DB에 묶이게 됩니다. 그래서 게시판 모듈은 자신만의 회원 복제 테이블 `POST_MEMBER`를 둡니다.

```
[member 모듈]                         [post 모듈]
Member 가입 완료
   └─ MemberJoinedEvent 발행 (password 제외 MemberDto)
                                          └─ PostEventListener 수신
                                                └─ PostMember 신규 생성 (id·createDate는 원본과 동일)

Member 활동점수 변경
   └─ MemberModifiedEvent 발행 (변경량 0이면 미발행)
                                          └─ PostEventListener 수신
                                                └─ PostMember 갱신 (save = update)
```

- 원본과 복제본의 `id`는 항상 같습니다. (복제본은 `ReplicaMember`를 상속해 id/생성일시를 자동 생성하지 않고 원본 값을 그대로 생성자로 받습니다.)
- `password`는 복제하지 않습니다 (`MemberDto`에 필드 자체가 없음).
- 원본이 바뀐 시점과 복제본이 반영되는 시점 사이에는 짧은 지연이 있을 수 있습니다 (최종 일관성, eventual consistency).
- 최종적으로 `Post.author`, `PostComment.author`의 타입은 `Member`가 아닌 `PostMember`이며, `boundedContext.post` 패키지에서 `boundedContext.member`를 import하는 코드는 0건입니다 (역방향도 동일).

## 3. 확인 결과

### 3-1. 초기 데이터 개수
```sql
SELECT * FROM MEMBER_MEMBER;
SELECT * FROM POST_MEMBER;
SELECT * FROM POST_POST;
SELECT * FROM POST_POST_COMMENT;
```

```
SELECT * FROM MEMBER_MEMBER;
ID  ACTIVITY_SCORE  NICKNAME  PASSWORD  USERNAME  CREATE_DATE                 MODIFY_DATE
1   0                시스템     ****      system    2026-09-28 01:13:08.713198  2026-09-28 01:13:08.713198
2   0                홀딩       ****      holding   2026-09-28 01:13:08.742198  2026-09-28 01:13:08.742198
3   0                관리자     ****      admin     2026-09-28 01:13:08.744198  2026-09-28 01:13:08.744198
4   11               유저1     ****      user1     2026-09-28 01:13:08.747209  2026-09-28 01:13:09.01024
5   9                유저2     ****      user2     2026-09-28 01:13:08.749208  2026-09-28 01:13:08.999242
6   6                유저3     ****      user3     2026-09-28 01:13:08.751208  2026-09-28 01:13:09.007241
(6 rows)

SELECT * FROM POST_MEMBER;
ID  ACTIVITY_SCORE  NICKNAME  USERNAME  CREATE_DATE                 MODIFY_DATE
1   0                시스템     system    2026-09-28 01:13:08.713198  2026-09-28 01:13:08.713198
2   0                홀딩       holding   2026-09-28 01:13:08.742198  2026-09-28 01:13:08.742198
3   0                관리자     admin     2026-09-28 01:13:08.744198  2026-09-28 01:13:08.744198
4   11               유저1     user1     2026-09-28 01:13:08.747209  2026-09-28 01:13:08.98524
5   9                유저2     user2     2026-09-28 01:13:08.749208  2026-09-28 01:13:08.996239
6   6                유저3     user3     2026-09-28 01:13:08.751208  2026-09-28 01:13:09.00324
(6 rows)

SELECT * FROM POST_POST;
ID  CREATE_DATE                 MODIFY_DATE                 CONTENT  TITLE  AUTHOR_ID
1   2026-09-28 01:13:08.782724  2026-09-28 01:13:08.782724  내용1     제목1   4
2   2026-09-28 01:13:08.888188  2026-09-28 01:13:08.888188  내용2     제목2   4
3   2026-09-28 01:13:08.892189  2026-09-28 01:13:08.892189  내용3     제목3   4
4   2026-09-28 01:13:08.896189  2026-09-28 01:13:08.896189  내용4     제목4   5
5   2026-09-28 01:13:08.899189  2026-09-28 01:13:08.899189  내용5     제목5   5
6   2026-09-28 01:13:08.903189  2026-09-28 01:13:08.903189  내용6     제목6   6
(6 rows)

SELECT * FROM POST_POST_COMMENT;
ID  CREATE_DATE                 MODIFY_DATE                 CONTENT  AUTHOR_ID  POST_ID
1   2026-09-28 01:13:08.973239  2026-09-28 01:13:08.973239  댓글1     4          1
2   2026-09-28 01:13:08.975239  2026-09-28 01:13:08.975239  댓글2     5          1
3   2026-09-28 01:13:08.976241  2026-09-28 01:13:08.976241  댓글3     6          1
4   2026-09-28 01:13:08.977239  2026-09-28 01:13:08.977239  댓글4     5          2
5   2026-09-28 01:13:08.977239  2026-09-28 01:13:08.977239  댓글5     5          2
6   2026-09-28 01:13:08.97824   2026-09-28 01:13:08.97824   댓글6     6          3
7   2026-09-28 01:13:08.98024   2026-09-28 01:13:08.98024   댓글7     6          3
8   2026-09-28 01:13:08.981239  2026-09-28 01:13:08.981239  댓글8     4          4
(8 rows)
```

회원 6행, 게시글 6행, 댓글 8행으로 미션 기준과 일치하고, `POST_MEMBER`의 id/username도 `MEMBER_MEMBER`와 동일하게 복제되었습니다.

### 3-2. 회원별 글 수·댓글 수에 따른 활동점수

`POST_POST`, `POST_POST_COMMENT`를 기준으로 계산하면:

| 회원 | 글 수 | 댓글 수 | 계산 점수 (글×3 + 댓글×1) | MEMBER_MEMBER.ACTIVITY_SCORE |
|---|---|---|---|---|
| user1 | 3 | 2 | 11 | 11 |
| user2 | 2 | 3 | 9  | 9  |
| user3 | 1 | 3 | 6  | 6  |

계산값과 저장된 점수가 일치합니다. (교안 원문의 기준 값 12/9/5가 아니라 11/9/6인 이유: 댓글7의 작성자가 예제 커밋 기준으로 user1이 아닌 user3이기 때문 — 3강 "확인할 점" 참고.)

### 3-3. 원본(MEMBER_MEMBER)과 복제본(POST_MEMBER) 일치 확인

위 3-1 결과를 비교하면:

| id | MEMBER_MEMBER.ACTIVITY_SCORE | POST_MEMBER.ACTIVITY_SCORE | 일치 |
|---|---|---|---|
| 1 | 0  | 0  | ✅ |
| 2 | 0  | 0  | ✅ |
| 3 | 0  | 0  | ✅ |
| 4 | 11 | 11 | ✅ |
| 5 | 9  | 9  | ✅ |
| 6 | 6  | 6  | ✅ |

id·username·activity_score 모두 원본과 복제본이 일치합니다. (create_date는 동일하며, modify_date는 각 모듈이 별도 트랜잭션에서 갱신하기 때문에 밀리초 단위로 약간 차이가 있을 수 있습니다 — 최종 일관성 특성.)

### 3-4. 재실행 시 중복 생성 없음
1차 실행과 재실행 사이에 `db_dev.mv.db` 파일을 삭제하지 않고 앱만 재시작한 뒤 각 테이블의 행 수를 비교했습니다.

```sql
SELECT COUNT(*) FROM MEMBER_MEMBER;
SELECT COUNT(*) FROM POST_MEMBER;
SELECT COUNT(*) FROM POST_POST;
SELECT COUNT(*) FROM POST_POST_COMMENT;
```

| 테이블 | 1차 실행 | 재실행 |
|---|---|---|
| MEMBER_MEMBER | 6 | 6 |
| POST_MEMBER | 6 | 6 |
| POST_POST | 6 | 6 |
| POST_POST_COMMENT | 8 | 8 |

재실행 후에도 행 수가 그대로여서, 회원·게시글·댓글 초기 데이터뿐 아니라 이벤트로 생성되는 회원 복제본(`POST_MEMBER`)까지 중복 생성되지 않음을 확인했습니다.

### 3-5. 보안 팁 API 호출 결과
```
GET http://localhost:8080/api/v1/member/members/randomSecureTip

200 OK
비밀번호의 유효기간은 90일 입니다.
```
