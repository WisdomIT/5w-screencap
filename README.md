# 5w-screencap

현대·기아 Gen5W(S5W_M, Android 4.4.2) 내비게이션 화면을 버튼 한 번으로 캡처하고, 주행 중 녹화해 USB에 저장하는 [HMG-API](https://github.com/Open-HMG/HMG-API) mod입니다. CarPlay·Android Auto 화면도 찍힙니다(소리는 녹음하지 않습니다).

> ⚠️ 개발 중입니다. 아직 실차에 설치해 확인하지 않았습니다.

## 개발 현황

| 항목 | 상태 |
|---|---|
| 구현 가능성 검토 (니로 HEV, V039) | ✅ 캡처·녹화 가능 확인 ([결과](diag/results/FINDINGS-20261119-niro-diag3.md)) |
| 캡처·녹화 서비스 (`screencapd`) | 🛠 구현, 실차 확인 전 |
| 버튼 (HMG-API 바인딩, ☆ 메뉴) | 🛠 구현, 실차 확인 전 |
| 플로팅 버튼 | 🛠 구현, 실차 확인 전 |
| Launch 화면 (캡처 테스트·설정·진단·QR) | 🛠 구현, 실차 확인 전 |
| 패키징·릴리스 | ⏳ 첫 릴리스 전에 추가 |

화면 설계: [docs/ui-mockup.html](docs/ui-mockup.html)

## 쓰는 법 (예정)

| 하는 일 | 방법 |
|---|---|
| 캡처 | 고른 버튼 1회·길게·연속 2회, 차량 ☆ 메뉴의 "화면 캡처", 또는 플로팅 버튼 탭 |
| 녹화 시작/정지 | 고른 버튼, ☆ 메뉴의 "화면 녹화 시작/정지", 또는 플로팅 버튼 길게 누르기 |
| 설정 | NEMESIS Manager → 5W Screencap → Launch, 또는 앱 메뉴의 "화면 캡처" |

결과는 USB의 `5W-Screencap/Screenshots`, `5W-Screencap/Recordings`에 저장됩니다. 녹화는 2분 단위 MP4로 나뉘어 저장됩니다.

## 직접 빌드 (개발자용)

JDK 17 이상과 Python 3가 필요합니다. 빌드 도구(aapt2, r8, android-all 4.4)는 처음 빌드할 때 버전과 sha256을 고정해 내려받습니다.

```bash
tests/run.sh
```

빌드, `mod.json` 검사, 단위 테스트를 한 번에 합니다. 빌드만 하려면 `tools/build.sh`를 씁니다. 결과물은 `mod/5w-screencap/app/`과 `mod/5w-screencap/svc/`에 생깁니다.

개발 흐름은 [CONTRIBUTING.md](CONTRIBUTING.md), 릴리스는 [docs/RELEASING.md](docs/RELEASING.md)를 따릅니다.

## 구성

| 경로 | 내용 |
|---|---|
| `mod/5w-screencap/` | HMG-API mod: `mod.json`(버전의 출처), `settings.json` |
| `daemon/` | `screencapd`: root로 도는 캡처·녹화 서비스 |
| `app/` | 차량 앱: 버튼 수신, 소리·토스트, 플로팅 버튼, Launch 화면 |
| `tools/` | `build.sh`, `gen_input.py`(버튼 바인딩 생성) |
| `tests/` | `mod.json` 검사, JVM 단위 테스트 |
| `diag/`, `diag2/`, `diag3/` | 구현 가능성 검토 때 쓴 실차 진단 (`diag/results/`에 결과) |
