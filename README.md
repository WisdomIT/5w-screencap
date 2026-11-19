# 5w-screencap

현대·기아 Gen5W(S5W_M, Android 4.4.2) 내비게이션 화면을 버튼 한 번으로 캡처하고, 주행 중 녹화해 USB에 저장하는 [HMG-API](https://github.com/Open-HMG/HMG-API) mod입니다. CarPlay·Android Auto 화면도 찍힙니다. 녹화는 영상만 저장합니다(소리는 녹음하지 않습니다).

> ⚠️ 비공식 개조입니다. 설치와 사용에 따른 책임은 차 주인에게 있습니다. 설치·설정은 **차를 안전한 곳에 세운 상태**에서 하고, 운전 중에는 화면을 보지 않고 쓸 수 있는 하드웨어 버튼으로 조작하세요.

## 개발 현황

| 항목 | 상태 |
|---|---|
| 화면 캡처 (1920×720 JPEG, 플로팅 버튼은 찍히지 않음) | ✅ 니로 HEV에서 확인 |
| 영상 녹화 (MPEG-4, 2분 단위 MP4) | ✅ 니로 HEV에서 확인 |
| 버튼 (HMG-API 버튼 연결, ☆ 메뉴, 1회·길게·연속 2회, 원래 기능 막기) | ✅ 니로 HEV에서 확인 |
| 플로팅 버튼 (드래그, 가장자리 태그, 녹화 시간, USB 연결 시만 표시) | ✅ 니로 HEV에서 확인 |
| 설정 화면 (캡처 테스트, 설정, 단계별 진단, 이슈 QR) | ✅ 니로 HEV에서 확인 |
| 소리 녹음 | ❌ 지원하지 않음 ([4차 진단 결과](diag/results/FINDINGS-20261119-niro-diag4.md)) |

동작이 확인된 차는 [동작 확인 차량](docs/COMPATIBILITY.md)에 있습니다. 화면 설계: [docs/ui-mockup.html](docs/ui-mockup.html)

## 설치

**HMG-API 1.0 이상**이 먼저 설치되어 있어야 합니다(HMG-API는 NEMESIS 위에서 동작합니다).

1. [최신 릴리스](https://github.com/WisdomIT/5w-screencap/releases/latest)에서 `5w-screencap-<버전>-usb.zip`을 받아 압축을 풀고, `NEMESIS.data`와 `packages` 폴더를 USB **맨 위**에 복사합니다.
2. 차에 꽂고 NEMESIS가 물으면 **예**를 누릅니다. 설치가 끝나면 **재부팅**합니다(HMG-API가 버튼을 다시 연결합니다).
3. 앱 메뉴의 **"화면 캡처"**에서 버튼과 녹화를 설정합니다. (NEMESIS Manager의 Launch는 HMG-API 앱을 엽니다)

| 파일 | 용도 |
|---|---|
| `5w-screencap-<버전>-usb.zip` | **대부분은 이것.** USB에 풀면 NEMESIS로 설치 |
| `5w-screencap-<버전>.zip` | HMG-API mod 파일만 (HMG-API의 USB 업데이트 `HMGAPI/updates/`, adb) |
| `5w-screencap-<버전>-nemesis-package.zip` | NEMESIS 패키지만 |

업데이트도 같은 방법으로 설치합니다. 설정은 그대로 남습니다. 지우려면 HMG-API 앱에서 5W Screencap을 제거하거나 NEMESIS Manager에서 Uninstall합니다.

시동이 꺼질 때 녹화 중이던 파일을 마저 닫는 기능은 HMG-API 1.1.0 이상에서 동작합니다. 그 전 버전에서는 녹화를 멈추지 않고 시동을 끄면 마지막 2분 이내 구간이 저장되지 않을 수 있습니다.

## 쓰는 법

| 하는 일 | 방법 |
|---|---|
| 캡처 | 고른 버튼(1회·길게·연속 2회), 차량 ☆ 메뉴의 "화면 캡처", 또는 플로팅 버튼 탭 |
| 녹화 시작 | 고른 버튼, ☆ 메뉴의 "화면 녹화 시작/정지", 또는 플로팅 버튼 길게 누르기 |
| 녹화 정지 | 녹화 버튼을 한 번 더, 녹화 중에 캡처 버튼, 또는 플로팅 버튼 탭 |
| 설정 | 앱 메뉴의 "화면 캡처" (HMG-API 앱의 mod 설정에서도 버튼·누르는 방식을 고를 수 있음) |

- 결과는 USB의 `5W-Screencap/Screenshots`, `5W-Screencap/Recordings`에 저장됩니다. 녹화는 2분 단위 MP4로 나뉩니다.
- 캡처·녹화는 소리(셔터음, 비프)와 차량 알림으로 알려 줍니다. 녹화를 멈춘 뒤 **"녹화 저장됨"** 알림을 보고 USB를 빼세요.
- 플로팅 버튼은 USB가 꽂혀 있을 때만 보이고, 사진과 영상에는 찍히지 않습니다.
- 녹화 화질: 표준(1024×384, 약 12fps), 선명(1280×480, 약 8fps), 원본(1920×720, 약 3fps).
- 후방카메라 화면은 별도 하드웨어 레이어라 찍히지 않을 수 있습니다.

## 문제가 있을 때

설정 화면의 **진단 실행**을 누르면 8단계를 차례로 확인하고 USB의 `5W-Screencap/diag/`에 `SUMMARY.txt`를 저장합니다(캡처 이미지나 개인 정보는 담지 않습니다). 오른쪽 QR을 휴대폰으로 스캔하면 이 차의 정보가 채워진 이슈 작성 화면이 열립니다. `SUMMARY.txt`를 첨부해 주세요.

## 직접 빌드 (개발자용)

JDK 17 이상과 Python 3가 필요합니다. 빌드 도구(aapt2, r8, android-all 4.4)와 HMG-API mod SDK는 처음 빌드할 때 버전과 sha256을 고정해 내려받습니다.

```bash
tests/run.sh
```

빌드, `mod.json` 검사, HMG-API 엔진 검사(mod SDK로 가짜 헤드유닛에서 설치·버튼 바인딩 확인), 단위 테스트를 한 번에 합니다. 빌드만 하려면 `tools/build.sh`, 설치 파일까지 만들려면 `tools/package.sh`를 씁니다(`dist/release/`).

개발 흐름은 [CONTRIBUTING.md](CONTRIBUTING.md), 릴리스는 [docs/RELEASING.md](docs/RELEASING.md)를 따릅니다.

## 구성

| 경로 | 내용 |
|---|---|
| `mod/5w-screencap/` | HMG-API mod: `mod.json`(버전의 출처), `settings.json` |
| `daemon/` | `screencapd`: root로 도는 캡처·녹화 서비스 |
| `app/` | 차량 앱: 버튼 수신, 소리·토스트, 플로팅 버튼, 설정 화면 |
| `tools/` | `build.sh`, `package.sh`, `release.sh`, `sdk.sh`(HMG-API mod SDK), `gen_input.py`(버튼 바인딩 생성), `mkicon.py` |
| `tests/` | `mod.json` 검사, HMG-API 엔진 검사(`engine.sh`), JVM 단위 테스트 |
| `diag/` ~ `diag4/` | 구현 가능성 검토 때 쓴 실차 진단 (`diag/results/`에 결과) |

## 함께 쓰는 것

- QR 코드: [Nayuki QR Code generator](https://github.com/nayuki/QR-Code-generator) (MIT License, `app/third_party/io/nayuki/qrcodegen/`의 파일 머리에 저작권 표시)
- 빌드 도구로만 쓰고 배포물에 넣지 않는 것: HMG-API mod SDK (AGPL-3.0), aapt2·r8 (Apache-2.0, BSD-3), android-all (Apache-2.0)
