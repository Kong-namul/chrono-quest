# A1 리뷰에서 다음 계획으로 넘기는 항목

A1(판정 엔진) 최종 리뷰와 태스크 리뷰에서 나왔지만 A1 범위 밖이라 다음 계획에서 처리할 것들이다. A2~A4 계획을 쓸 때 이 문서를 먼저 읽는다.

## A2 (데이터 계층 + 수동 플레이 + Compose 임시 화면)

> **A2a에서 처리함 (2026-11-25)**
> - 중복 금지: Room 기본키
> - 미래 시각 금지, `safe`는 도착 때만: `RecordPolicy`
> - `date == dueAt.toLocalDate()`: `QuestGenerator`, `ScheduleService.addOneOffQuest`
> - 시각 저장: `Converters`(로컬 벽시계 시각, 초 단위)
> - 대표 칭호: `RepresentativeTitle`, `GameService.representativeTitle()`
> - 오늘 요약: `Today`, `GameService.today()`
> - 티어 승급 연출 키 `tier:<TIER>`: `Celebrations`
> - "정리 필요" +3시간 필터: `CleanupFilter`
> - 엔진 인스턴스 하나: `GameService`가 생성자로 받음
>
> **A2b·Phase B로 남은 것**
> - grants 범위 제한(bridge)
> - 보스 `endsAt`과 데미지 출처별 내역
> - pet/boss 키 재표시 정책 문서화

### 원장 계약 (Room → `Ledger`)
- **중복 금지**: `Quest.id`, `QuestRecord.questId`, `DayRecord.date`를 Room 기본키로 둔다. 엔진은 중복이 있으면 첫 번째만 쓰므로 결과가 입력 순서에 따라 달라진다. `Ledger` KDoc에 이 계약을 적는다.
- **미래 시각 금지**: 기록을 고칠 때 `now`보다 늦은 출발·도착 시각은 저장하지 않는다. 엔진은 미래 기록도 XP로 친다.
- **`safe`는 도착 확인 때만**: 안전 여부는 도착을 확인하면서 함께 저장한다. 엔진은 이제 `safe=false`면 도착 시각이 없어도 LATE로 판정한다.
- **`Quest.date`와 `dueAt` 일치**: `date == dueAt.toLocalDate()`를 강제하거나, 자정 넘는 일정의 "논리적 날짜" 의미를 정해 문서화한다. 주·월·기분·보스는 `date`를 쓰고 보류는 `dueAt`을 쓴다.
- **시각 저장 방식**: 로컬 벽시계 시각(`LocalDateTime`)으로 저장한다. Instant로 저장하고 현재 타임존으로 바꾸면, 시간대가 다른 곳으로 이동했을 때 과거 판정이 뒤집힌다.

### GameState 바깥에서 채울 것 (Kotlin 쪽, Unity 아님: 스펙 D8)
- 대표 칭호: 보유한 칭호 중 사용자가 고른 것. 없거나 기록 수정으로 사라지면 "미각성자"다.
- `today`: 오늘 퀘스트 목록과 `DayRecord`
- 보스 `endsAt`(다음 월요일 00:00), 보스 데미지 출처별 내역(보스 화면에서 쓴다)
- 티어 승급 연출 키: 지금은 승급 grant가 없다. `tier:<TIER>` 같은 키를 bridge에서 만들지, 엔진에 grant로 추가할지 정한다.

### 화면·브리지
- `pendingQuestIds`는 "예정 시각이 지났는데 판정이 안 된 것"이라 이동 중인 일정도 들어 있다. 홈의 "정리 필요" 카드는 별도로 예정 +3시간 기준으로 거른다(스펙 6.2).
- `grants`는 계속 늘어난다(4개월에 약 2,000건). bridge는 아직 안 본 지급만, 또는 기간을 잘라서 보낸다.
- `Engine`은 앱 전체에서 하나만 만든다. 생성할 때마다 `titles.json`을 읽는다.
- `pet:evolve:<단계>`와 `boss:<주>:result` 키는 내용이 바뀌어도 같은 키다. 예를 들어 예전 주를 휴가로 바꾸면 이후 보스 순서가 밀린다. 이미 본 연출은 다시 띄우지 않고 도감·이력만 조용히 갱신하는 것으로 정한다.

### A2b가 꼭 반영할 것 (A2a 최종 리뷰, 2026-11-25)
- **프로세스당 하나**: `ChronoDatabase`(이제 `build()`가 싱글턴), `TitleCatalog`, `Engine`, `GameService`, `ScheduleService`를 앱 컨테이너 하나에서만 꺼내 쓴다. 알림 리시버(`goAsync`)와 워커도 마찬가지다. 앱 코드가 DAO로 직접 써서 `RecordPolicy`를 우회하지 않게 한다.
- **시계**: 생성할 때 받은 `Clock`은 타임존이 고정된다. 호출할 때마다 `ZoneId.systemDefault()`를 읽는 Clock을 쓰거나, `ACTION_TIMEZONE_CHANGED`/`TIME_SET` 때 다시 만든다(스펙 10).
- **쓰기 없이 바뀌는 상태**: 예정 시각이 지나 보류가 되는 것, +3시간 정리 필요, 월요일 00:00의 주·월 보너스와 보스 결과는 기록을 쓰지 않아도 생긴다. 그래서 필요한 것:
  - 앱 복귀 시 새로고침, 홈 화면 주기적 갱신
  - 00:05 워커가 `unseenCelebrations()`를 확인해 알림
  - 연출을 "본 것"으로 표시하는 시점(알림 또는 결과 화면) 결정
- **관찰**: 아직 Flow/StateFlow API가 없다. 쓰기 뒤 갱신하는 `StateFlow<GameState>`를 두거나 Room 무효화를 관찰한다.
- **스레드**: `Engine.compute`와 `TitleCatalog.load()`는 호출한 스레드에서 돈다. `Dispatchers.Default`로 옮긴다.
- **워커**: `enqueueUniquePeriodicWork`(KEEP)로 매일 생성하고, 앱 시작 때도 한 번 생성한다.
- **화면**:
  - `ConfirmEarly` 결과가 오면 "일정 시각을 바꿀래?" 창을 띄운다. [일정 수정]은 `ScheduleService.reschedule` 뒤 기록, [그대로 기록]은 `allowEarly = true`로 다시 호출한다.
  - 음수 `xpGained`를 처리한다.
  - 기록 없음으로 정리한 일정은 [출발]을 숨긴다.
  - 칭호창 NEW 배지: `newTitleIds()`로 그리고, 확인하면 `markTitlesViewed()`
  - 이미 판정된 일정의 취소나 지난 날짜 휴가 지정을 허용할지, 되돌리기를 둘지 정한다
- **티어·펫·보스 키**: `tier:<TIER>`는 티어를 잃었다가 다시 얻어도 이미 본 것으로 남는다. 펫·보스 키도 내용이 바뀌어도 같은 키다. 다시 띄우지 않는 정책으로 문서화한다.
- **설정·백업**: `allowBackup`을 켜고(스펙 4장), 파괴적 마이그레이션은 쓰지 않는다. enum 상수 이름은 바꾸지 않는다(문자열로 저장됨).
- **DayRecord 부분 갱신**: A3에서 알람·퀴즈 필드를 쓰는 곳이 늘어나므로, 하루 기록은 읽고 고쳐서 쓰는 방식을 트랜잭션 안에서만 한다.
- **수정 재리뷰의 사소한 것**:
  - `representativeTitle()`은 읽을 때마다 쓰기 잠금을 잡는다. 홈 화면이 자주 읽으면 잠금 없이 먼저 판단하고, 바꿀 때만 잠그도록 고친다.
  - `ChronoDatabase.build()`는 이제 "있으면 꺼내고 없으면 만든다"는 뜻이다. `getInstance` 같은 이름으로 바꾸는 것을 검토한다.
  - `ScheduleService.reschedule`은 지난 시각이나 시즌 이전 날짜로 바꾸는 것을 막지 않는다. 화면에서 막거나 검증을 추가한다.

## A3 (기상 알람)
- 퀴즈를 알람보다 먼저 푼 기록이 나오지 않게 저장 시 검증한다. 엔진은 이미 실패로 처리하지만 테스트가 없다.

## A4 (위치 감지)
- **정리안 먼저 제안**(스펙 6.2, 사용자 아이디어): 앱을 열 때 확인 안 된 감지 기록이나 그 시간대의 지오펜스 감지 시각으로 도착 시각을 먼저 제안한다.

## A4 이후 아이디어: 회사 PC 출근 신호 (2026-11-25 사용자 제안)
- 회사 PC에 작은 프로그램을 깔아 그날 처음 로그인하거나 잠금을 푼 시각을 출근 근거로 남긴다. 마우스 입력을 감시하는 것보다 OS 로그인·잠금 해제 기록을 쓰는 게 낫다(권한이 덜 필요하고 보안 도구에 덜 걸린다).
- 이 시각은 단독 판정이 아니라 A4 "정리안 먼저 제안"의 근거로 쓴다. 회사 입구 도착보다 몇 분 늦기 때문이다.
- 정해야 할 것:
  - PC에서 폰으로 전달하는 방법: 서버가 없으므로 무료 푸시 중계, 같은 와이파이 직접 전송, QR 중 하나
  - 회사 PC의 OS(macOS/Windows)
  - 사내 보안 정책상 설치가 가능한지
  - 원안의 "사람별 이메일 링크 배포"는 여러 사람이 쓰는 것을 전제로 한다. 개인용 결정(D1)과 충돌하므로 범위를 다시 정해야 한다. 혼자 쓴다면 공개 레포의 Release 링크로 충분하다.

## Phase B (Unity)
- `titles.json`은 jar 안의 Java 리소스이고, 그 jar가 .aar 안에 들어간다. Unity Gradle 빌드를 거친 뒤에도 남는지 실기기에서 확인한다.
- kotlinx-serialization 런타임 jar를 함께 넣어야 한다. Unity는 Maven 전이 의존성을 자동으로 풀지 않는다.

## 환경 메모
- 이 맥은 **Intel(x86_64) + macOS 26**이다. Homebrew가 openjdk 사전 빌드 패키지를 주지 않아서 JDK는 Temurin 21을 `~/Library/Java/JavaVirtualMachines/temurin-21.jdk`에 직접 설치했다. Android SDK, 에뮬레이터, Unity를 설치할 때도 Intel 지원 여부를 먼저 확인한다.
- 네트워크가 느리다(약 400KB/s). 큰 다운로드는 시간 제한을 넉넉히 두고 이어받기(`curl -C -`)를 쓴다. Gradle은 의존성이 캐시된 뒤 `--offline`으로 돌린다.

## 사소한 것 (선택)
- `gradle-wrapper.properties`에 `distributionSha256Sum`을 추가한다.
