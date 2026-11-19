# 4차 진단: 소리 녹음 가능성

녹화 영상에 차량 소리(블루투스, CarPlay 음악)를 넣을 수 있는지 확인합니다. 아무것도 바꾸지 않습니다. 결과는 USB의 `5W-DIAG4-<시각>/`에 저장됩니다.

| 단계 | 확인 내용 |
|---|---|
| 1 | 오디오 구성: `audio_policy.conf`, 오디오 HAL 목록, ALSA 카드·PCM 장치(`/proc/asound`) |
| 2 | 지금 재생 중인 상태: `dumpsys media.audio_flinger`, `media.audio_policy`, `audio` (재생 스레드가 Android 안에 있는지) |
| 3 | `AudioRecord` 입력 소스 9가지(DEFAULT, MIC, CAMCORDER, VOICE_RECOGNITION, VOICE_COMMUNICATION, VOICE_DOWNLINK, VOICE_UPLINK, VOICE_CALL, REMOTE_SUBMIX)를 하나씩 3초 녹음해 소리가 들어오는지(RMS, 최고값, 0이 아닌 샘플 비율) 측정하고 WAV로 저장 |
| 4 | 진단 뒤의 재생 상태, 오디오 관련 logcat |

## 실행
1. 정차 상태에서 **블루투스나 CarPlay로 음악을 틀어 둡니다.** 진단이 끝날 때까지 계속 재생합니다.
2. USB를 꽂고 **Yes**를 누릅니다. 약 1분 걸립니다.
3. 테스트마다 "소리 진단 3/9: CAMCORDER" 같은 안내가 뜹니다. **음악이 끊기거나 작아지는 번호가 있으면 기억해 주세요.** 마지막 REMOTE_SUBMIX는 소리를 녹음 쪽으로 돌려 스피커가 3초 정도 조용해질 수 있습니다(진단이 끝나면 돌아옵니다).
4. "소리 진단 끝" 안내가 뜨면 끝입니다.

MIC 등 마이크 계열 소스는 실내 소리(대화 포함)가 3초씩 녹음되어 USB에 저장됩니다. 공유 전에 들어 보고, 원치 않으면 해당 WAV는 빼고 보내 주세요.

## 빌드
```bash
diag4/build.sh
```
`build/toolchain`의 android-all 4.4와 r8을 씁니다(`tools/build.sh`를 한 번 실행하면 받아집니다).
