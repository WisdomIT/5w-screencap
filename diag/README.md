# 화면 캡처·녹화 가능성 진단 (실차용)

NEMESIS `type=execute` USB로 실행하는 **읽기 전용** 진단 스크립트입니다. 헤드유닛 설정이나 `/system`은 건드리지 않습니다. 결과는 모두 USB의 `5W-DIAG-<시각>/` 폴더에 저장되고, 작업 중에만 `/data/local/tmp/5wdiag`를 잠시 사용한 뒤 지웁니다.

## 준비

1. exFAT 또는 FAT32로 포맷한 빈 USB를 준비합니다.
2. `usb/` 폴더 **안의 두 파일**(`NEMESIS.data`, `NEMESIS.sh`)을 USB 루트에 복사합니다. 폴더째 넣으면 안 됩니다.
3. 차량은 NEMESIS가 설치돼 있고 Android 부팅이 끝난 상태여야 합니다. `NEMESIS.emergency` 파일은 넣지 마세요.

## 실행

1. 캡처하고 싶은 화면을 띄웁니다. 내비 지도 화면을 권장합니다.
2. USB를 꽂고 실행 확인 팝업에서 **Yes**를 누릅니다.
3. 약 1~2분 동안 **헤드유닛을 조작하지 마세요.** 테스트 순서는 화면 캡처 → 5초 녹화 2회 → 6초 녹화 후 신호로 중지입니다.
4. "5W 진단 완료" 토스트가 뜨고 NEMESIS 성공 팝업이 나오면 USB를 뽑습니다.
5. 다시 실행하려면 USB를 뺐다가 꽂습니다.

> 녹화 테스트 중 인코더 문제로 `mediaserver`가 재시작되면 잠깐 소리가 끊기거나 후방카메라가 늦게 뜰 수 있습니다. **정차 중에만** 실행하세요.

## 결과 폴더

| 파일 | 확인 내용 |
|---|---|
| `report.txt` | 요약. 각 테스트의 `rc`(종료코드), `ms`(소요시간), `size`(바이트) |
| `cap_direct.png` / `cap_via_tmp.png` | USB에 바로 저장한 캡처 / 내부에 저장 후 복사한 캡처 |
| `cap.raw` | 원시 프레임. 앞 12바이트가 폭·높이·포맷 |
| `cap_display1~3.png` | 보조 디스플레이(계기판 등)가 있으면 생성됨 |
| `rec_default.mp4` | 디스플레이 해상도 그대로 5초 녹화 (**핵심 테스트**) |
| `rec_720p.mp4` | 1280×720, 4Mbps로 5초 녹화 |
| `rec_sigint.mp4` | 녹화 중 신호로 중지했을 때 파일이 정상인지 확인 |
| `*.log` | 각 명령의 stdout/stderr |
| `logcat_filtered.txt` | OMX/MediaCodec/SurfaceFlinger 관련 로그 |
| `dumpsys_surfaceflinger.txt` | HWC 레이어 표. OVERLAY 레이어는 캡처에서 빠질 수 있음 |
| `media_codecs.xml`, `encoders.txt` | 인코더 목록 |
| `mounts.txt` | USB 마운트 구조 (FUSE/vfat) |
| `props.txt` | 빌드·보드 정보. 시리얼번호는 제외 |

## 판정 기준

- **캡처 가능:** `cap_direct` rc=0, PNG가 PC에서 정상으로 열림, 화면 내용이 맞음
- **녹화 가능:** `rec_default` 또는 `rec_720p`가 rc=0이고 MP4가 PC에서 재생됨
- **녹화 제어 가능:** `rec_sigint`의 `stopped_by=INT` 또는 `HUP`, 그리고 MP4가 재생됨 (`KILL`이면 파일이 손상됐을 가능성이 높음)
- **녹화 불가:** 두 녹화가 모두 실패하고 로그에 `createInputSurface`나 OMX 오류가 있음 → screencap + 인코더 직접 사용 방식으로 전환 검토

PC로 결과 폴더를 통째로 가져와 공유해 주면 분석하겠습니다.
