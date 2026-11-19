# 개발 흐름

## 브랜치

| 브랜치 | 용도 | 어디서 만들고 어디로 합치나 |
|---|---|---|
| `main` | **릴리스된 코드만.** 릴리스 이후 main의 모든 커밋은 릴리스 태그(`v<버전>`)와 짝을 이룹니다. | 직접 커밋하지 않습니다. `dev`(또는 `hotfix/*`)에서 PR로만 합칩니다. |
| `dev` | 다음 릴리스를 모으는 통합 브랜치 | 기능·버그 브랜치를 PR로 합칩니다. |
| `feature/<주제>` | 기능 추가 (예: `feature/floating-button`) | `dev`에서 만들고 `dev`로 PR |
| `fix/<주제>` | 버그 수정 (예: `fix/usb-removed-while-recording`) | `dev`에서 만들고 `dev`로 PR |
| `release/<버전>` | 릴리스 준비: 버전 올리기, 변경 내역 쓰기 ([RELEASING.md](docs/RELEASING.md)) | `dev`에서 만들고 `dev`로 PR |
| `hotfix/<주제>` | 릴리스된 버전의 급한 수정 | `main`에서 만들고 `main`으로 PR, 릴리스 후 `main`을 `dev`에 합침 |

GitHub 저장소의 기본 브랜치는 `main`으로 둡니다. 처음 오는 사람이 릴리스된 README를 보게 하려는 것입니다. 그래서 **PR을 열 때 대상(base)을 `dev`로 바꾸는 것**을 잊지 마세요.

## 작업 순서

1. `dev`를 최신으로 받습니다.
   ```bash
   git switch dev
   ```
   ```bash
   git pull
   ```
2. 작업 브랜치를 만듭니다.
   ```bash
   git switch -c feature/<주제>
   ```
3. 작업하고 커밋합니다. 커밋 메시지는 무엇을 왜 바꿨는지 적습니다.
4. 빌드와 테스트를 돌립니다. 전부 통과해야 합니다(JDK 17 이상, Python 3. 처음 한 번은 빌드 도구와 HMG-API mod SDK를 내려받습니다).
   ```bash
   tests/run.sh
   ```
5. 푸시하고 **`dev` 대상**으로 PR을 엽니다.
   ```bash
   gh pr create --base dev
   ```
6. 실차에서 확인한 내용이 있으면 PR 설명에 적습니다. 차종, 펌웨어, HMG-API 버전, 결과, 진단 수치를 남겨 주세요.

## 구조

| 경로 | 내용 |
|---|---|
| `mod/5w-screencap/` | HMG-API mod 폴더. `mod.json`(버전의 출처), `settings.json`. 빌드 결과물(`app/`, `svc/`)은 커밋하지 않습니다. |
| `daemon/` | `screencapd`: root로 도는 캡처·녹화 서비스 (app_process, `mod.json`의 `services`) |
| `app/` | 차량 앱: 버튼 수신, 소리·토스트, 플로팅 버튼, Launch 화면 |
| `tools/` | 빌드(`build.sh`), 패키징(`package.sh`), 릴리스(`release.sh`), HMG-API mod SDK(`sdk.sh`), 버튼 바인딩 생성(`gen_input.py`) |
| `tests/` | `mod.json` 검사, HMG-API 엔진 검사(`engine.sh`), JVM 단위 테스트 |
| `diag*/` | 구현 가능성 검토 때 쓴 실차 진단 스크립트와 결과 |

## 지켜야 할 것

- **HMG-API mod입니다.** 버튼(원래 기능 막기 포함), ★ 키 메뉴, USB 폴더, 차량 이벤트는 HMG-API가 맡습니다. HMG-API가 이미 하는 일을 따로 만들지 않습니다.
- **HMG-API 코드를 가져오지 않습니다.** HMG-API는 AGPL입니다. 앱과 서비스는 `hmgapid` 명령으로만 대화하고 `hmgapi-client.jar`를 넣지 않습니다.
- **`/system`에 쓰지 않습니다.** 지우면 흔적이 남지 않아야 합니다. 결과물은 USB에만 저장하고, USB가 없으면 내부 저장소에 쓰지 않습니다.
- **녹화는 운전 중 화면 조작 없이 끝나야 합니다.** 기본 조작은 하드웨어 버튼과 소리입니다. 화면을 봐야만 알 수 있는 상태를 만들지 않습니다.
- **기기 스크립트는 KitKat toolbox와 mksh에서 돌아야 합니다.** `sed`, `awk`, `printf`, `sort`, `head`, `tail`, `find`는 기기에 없습니다. 복잡한 일은 Java(서비스·앱)로 합니다.
- **진단 결과에 개인 정보를 남기지 않습니다.** 시리얼, BT·Wi-Fi·MAC 주소, 사용자 파일 이름, logcat 원본, 캡처 이미지가 해당합니다.
- **OEM 파일과 실차 화면을 커밋하지 않습니다.** 헤드유닛에서 꺼낸 바이너리, 원시 화면 덤프, 진단 폴더(`5W-DIAG*`, `5W-Screencap/`)가 해당합니다. `.gitignore`가 막고 있습니다. `diag/results/`는 검토 기록으로 예외입니다.
- 앱과 서비스는 Android 4.4(API 19)에서 돌아야 합니다. 빌드 때 r8의 `--min-api 19`로 확인합니다.

## 버전

- 버전은 `mod/5w-screencap/mod.json`의 `version` 한 곳에서 정합니다. 앱 버전, 릴리스 파일 이름, 태그가 모두 여기서 나옵니다.
- HMG-API 규칙에 따라 `MAJOR.MINOR.PATCH` 형식입니다(`0.1.0`, `0.2.0`, `1.0.0`).
- 태그는 `v<version>`입니다(예: `v0.1.0`).
- 버전을 올리는 시점은 릴리스 준비 때입니다([docs/RELEASING.md](docs/RELEASING.md)). 기능 브랜치에서는 올리지 않습니다.

## 릴리스

[docs/RELEASING.md](docs/RELEASING.md)를 따릅니다.
