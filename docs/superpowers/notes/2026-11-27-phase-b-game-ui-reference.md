# Phase B 게임형 UI 참고 조사 (2026-11-27)

## 배경

- 2026-11-27에 사용자가 지금 Compose 화면이 게임처럼 보이지 않는다고 말했다.
- 그건 의도한 것이다. 스펙 13장에 따라 Phase A의 Compose 홈은 기능 확인용 최소 화면이고, 게임 비주얼(툰 3D, 펫·보스 무대, 결과 연출)은 Phase B(Unity, M5~M7)에서 만든다.
- 사용자 결정은 **기능 구현(Phase A)을 다 끝낸 다음 Unity로 간다**이다.
- 아래는 그때 조사한 내용이다. Phase B 디자인을 시작할 때 이 메모부터 본다.
- 출처가 없는 항목이나 "확인 못 함" 항목은 추측이다. 다시 확인하고 쓴다.

## 참고 앱에서 확인한 것

| 앱 | 확인한 것 | 출처 |
|---|---|---|
| Duolingo | 주 버튼 아래에 같은 색보다 어두운 3~4px 솔리드 그림자가 있다. 누르면 약 4px 내려가고 그림자가 사라진다. 보조 버튼은 흰 바탕에 2px 연회색 테두리다. 서드파티 정리이고 공식 문서가 아니다. | https://www.shadcn.io/design/duolingo/raw , 구현 예시 https://www.joshwcomeau.com/animation/3d-button/ |
| Habitica | 헤더 왼쪽에 아바타가 있고, 오른쪽에 HP·XP·마나 바가 있다. 파티 퀘스트(보스전)가 있고 그림은 픽셀 아트다. | https://habitica.fandom.com/wiki/Header |
| Finch | 목표를 끝내면 펫의 에너지가 된다. 홈 화면 위젯으로 펫이 늘 보이게 한다. 완료하면 애니메이션으로 반응한다. | https://www.deconstructoroffun.com/blog/x0hd2ssr80y5n7gv0w967pg7hwd7tl , https://ixd.prattsi.org/2026/02/design-critique-finch-self-care-pet-ios-app/ |
| Pokémon Sleep | 홈에 "확인 안 한 수면 리포트" 버튼이 있다. 개발진이 주간 꾸준함을 평가한다고 말했다. | https://bulbapedia.bulbagarden.net/wiki/Pokémon_Sleep/Version_history , https://www.gamingbible.com/features/pokemon-sleep-preview-321285-20230707 |
| Brawl Stars / Supercell | 버튼·외곽선·폰트 규칙의 공식 출처는 **확인 못 함**. 두꺼운 외곽선과 통통한 버튼은 일반적인 인상이고 검증하지 않았다. | — |

공통점은 세 가지다. 첫째, 두툼하게 눌리는 버튼. 둘째, 홈 중앙과 위젯에 캐릭터나 펫을 둔다. 셋째, HP·XP 바를 늘 보이게 하고, 완료하면 애니메이션으로 보상한다.

## 한글 게임풍 폰트 후보 (앱에 넣기 전에 공식 배포처 원문을 다시 확인한다)

| 폰트 | 라이선스 | 출처 |
|---|---|---|
| Black Han Sans | OFL (Google Fonts METADATA에서 확인) | https://github.com/google/fonts/blob/main/ofl/blackhansans/METADATA.pb |
| Jua | OFL (확인) | https://github.com/google/fonts/blob/main/ofl/jua/METADATA.pb |
| Do Hyeon | OFL (확인) | https://github.com/google/fonts/blob/main/ofl/dohyeon/METADATA.pb |
| 여기어때 잘난체 | 자체 무료 라이선스. 라이선스 전문을 넣으면 앱 번들이 가능하다(2차 출처). | https://www.sandollcloud.com/free-font/772/Jalnan |
| 메이플스토리 서체 | 자체 무료 라이선스. 저작권 안내를 넣으면 앱 번들이 가능하고, 수정해서 재배포하면 안 된다(2차 출처). | https://www.sandollcloud.com/free-font/16195/Maplestory-OTF |
| Cafe24 Ssurround | OFL 1.1로 안내된다(2차 출처). | https://www.sandollcloud.com/font/16262/Cafe24-Ssurround-TTF |

- 폰트를 바꾸면 스펙 9장 토큰(`font`)이 바뀐다. 사용자 승인과 다운로드 승인을 먼저 받는다.
- 어떤 폰트가 더 게임 같은지는 직접 렌더링해서 비교하지 않았다.

## 무료 에셋 후보

- Kenney UI Pack(CC0, 버튼·패널·슬라이더): https://kenney.nl/assets/ui-pack
- Kenney UI Pack RPG Expansion(CC0): https://kenney.nl/assets/ui-pack-rpg-expansion
- Quaternius 몬스터 팩(Cute Animated Monsters Pack 등): https://quaternius.com/ . CC0 문구는 팩마다 확인해야 한다.
- 미리보기 그림은 아직 눈으로 확인하지 않았다. 스펙 8장대로 후보를 3~4개씩 사용자에게 보여주고 고른다.

## Compose에 남길 때 쓸 수 있는 기법 (디버그 화면을 다듬을 경우)

- 그라데이션 글자와 글자 그림자: `TextStyle(brush = …)`, `Shadow(…)`. 출처: https://developer.android.com/develop/ui/compose/text/style-text
- 외곽선 글자: `TextStyle(drawStyle = Stroke(…))`. 채움 글자와 겹쳐 그린다. 언제 도입됐는지는 공식 릴리스 노트로 확인하지 못했다.
