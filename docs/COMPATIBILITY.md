# 동작 확인 차량

이 mod가 실제 차에서 동작하는 것을 확인한 목록입니다. 여기 없는 차도 Gen5W(`S5W_M`)이고 HMG-API가 설치되어 있다면 동작할 가능성이 높습니다. 다만 확인된 것은 아래뿐입니다.

## 확인된 차량

| 차종 | 펌웨어 (SW 정보) | 지역 | HMG-API / NEMESIS | 버전 | 결과 | 확인일 | 비고 |
|---|---|---|---|---|---|---|---|
| 기아 니로 하이브리드 (DE 페이스리프트) | `DEPEHEV.KOR.S5W_M.V039.001.260902` | 한국 | 1.0.2 / 1.16.7 | 0.1.0 | ✅ 캡처·녹화·버튼·플로팅 버튼·진단 | 2026-11-19 | tcc897x, 1920×720. 녹화 표준 화질 약 12fps |

결과 표시:
- ✅ 캡처와 녹화 모두 됨
- ⚠️ 일부만 됨
- ❌ 안 됨 또는 문제 발생

## 지원하지 않는 내비

| 종류 | 이유 |
|---|---|
| SW 정보에 `S5W_L`이 있는 Gen5W | Android가 아니라 NEMESIS와 HMG-API를 설치할 수 없습니다. |
| ccNC 내비 | NEMESIS가 지원하지 않습니다. |
| NEMESIS나 HMG-API를 설치할 수 없는 차 | [NEMESIS 호환 목록](https://github.com/xwtk/hmg_nemesis#compatibility)과 HMG-API 안내를 확인하세요. |

## 내 차 결과 알려 주기

잘 되든 안 되든 [차량 호환성 보고](https://github.com/WisdomIT/5w-screencap/issues/new?template=car_report.yml)로 알려 주시면 이 목록에 추가합니다. 설정 화면의 **진단 실행** 결과(`SUMMARY.txt`)를 함께 보내 주시면 가장 정확합니다.
