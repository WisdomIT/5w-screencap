# 4차 실차 진단 결과: 소리 녹음 (니로, 2026-11-19 22:08)

차량: `DEPEHEV.KOR.S5W_M.V039.001.260902`, tcc897x, Android 4.4.2. 진단 중 음악 재생.

## 결론
| 방법 | 결과 |
|---|---|
| 재생 중인 소리를 디지털로 그대로 녹음 (REMOTE_SUBMIX) | ❌ 열리지 않음. `audio_policy.conf`에 `r_submix` 모듈이 없고 HAL(`audio.r_submix.*.so`)도 없음 |
| 통화 계열 소스 (VOICE_CALL / UPLINK / DOWNLINK / COMMUNICATION) | ❌ 열리지 않음 (통화 중에만 쓰이는 경로) |
| 실내 마이크 (MIC, DEFAULT, CAMCORDER, VOICE_RECOGNITION) | ⚠️ 열림. 같은 마이크 하나: 모노(좌우 100% 동일), 실제 대역 약 8kHz 이하(16kHz 수준 마이크를 48kHz로 올린 것), 레벨 -43 ~ -47 dBFS. MIC에서 스피커 음악의 리듬과 화음이 보임 |

→ **순정 상태로는 차량 소리를 깨끗하게 녹음할 경로가 없음.** 녹음할 수 있는 것은 실내 마이크로 들어오는 소리(스피커 음악 + 대화)뿐이고 음질이 낮음.

## 재생 경로
- 음악은 **Android 안에서 섞임**: AudioFlinger primary 출력에 활성 트랙(mediaserver, music 스트림, 44.1kHz 스테레오)이 있음
- primary 출력 장치는 `AUDIO_DEVICE_OUT_SPDIF_PCM`: Android가 섞은 소리를 디지털로 외부 앰프에 보냄
- 따라서 이론상 가로챌 지점은 있음: 오디오 HAL(`audio.primary.tcc897x.so`)의 출력 쓰기(`out_write`)
- 입력 장치: BUILTIN_MIC, LINE, USB, BLUETOOTH_DTMF, (a2dp 모듈) BLUETOOTH_A2DP. 표준 AudioRecord 소스로는 마이크만 선택됨
- ALSA: `00-00 i2s-dai-dummy-0`, `00-01 IEC958(spdif)`, `00-02 i2s-dai-dummy-2`, 모두 capture 1. 기기에 `tinycap`이 없어 직접 시험하지 못함

## 남은 선택지
| 방법 | 난이도 | 위험 |
|---|---|---|
| A. 마이크 녹음 옵션 (AAC로 영상과 함께 MP4) | 낮음 | 음질 낮음, 대화가 녹음됨(기본 꺼짐 필요) |
| B. 오디오 HAL 출력 가로채기 (HMG-API의 네이티브 패치 기능으로 `out_write`에서 PCM 복사) | 높음 (리버스 엔지니어링, NDK) | 소리 끊김·mediaserver 재시작 위험, 펌웨어마다 다를 수 있음 |
| C. `r_submix` HAL 추가 | 중간 | KitKat에서는 녹음 중 스피커 소리가 녹음 쪽으로 넘어가 차에서 안 들림: 부적합 |
| D. ALSA capture 장치 직접 (`tinycap` 동봉) | 중간 (NDK) | 어떤 장치가 출력 루프백인지 불명. 진단 필요 |
