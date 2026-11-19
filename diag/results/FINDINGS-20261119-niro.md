# 1차 실차 진단 결과: 니로 (2026-11-19)

## 차량
- `ro.build.product2` = `DEPEHEV.KOR.S5W_M.V039.001.260902` (니로 HEV, S5W_M, V039)
- `ro.board.platform` = `tcc897x`, Android 4.4.2 (SDK 19), kernel 3.18.24-tcc
- 디스플레이: 1920×720 @60Hz, 240dpi, 디스플레이 1개 (`Display Devices: size=1`)
- NEMESIS 1.16.7, 스크립트는 uid 0으로 실행됨

## 캡처: 가능 ✅
- `screencap -p`로 1920×720 PNG를 정상 저장함. USB에 바로 저장해도 됨
- CarPlay(DIPO) 투영 화면까지 그대로 찍힘. 이 시점에는 HWC 레이어가 전부 GLES이고 OVERLAY 레이어가 없었음
- 걸리는 시간: PNG는 약 4~5초(PNG 압축 비용), raw는 1초 미만, raw 3장 연속은 1.3초(장당 약 0.43초)
- raw 포맷: 12바이트 헤더(w=1920, h=720, fmt=1 RGBA_8888) + 픽셀 4바이트
- `-d 1~3`은 의미 없음. 존재하지 않는 ID라서 기본 화면으로 대체되고, `-d 1`은 R/B 채널이 뒤바뀌어 나옴
- 후방카메라 화면은 이번에 확인하지 않음 (direct display 경로라 캡처되지 않을 가능성이 있음)

## 녹화 (`screenrecord`): 불가 ❌
- 세 번 시도 모두 `ERROR: unable to create encoder input surface (err=-38)`로 실패하고 0바이트 파일만 남음
- 원인: `media_codecs.xml`에 등록된 video/avc 인코더가 `OMX.google.h264.encoder`(소프트웨어) 하나뿐임. 이 인코더는 KitKat에서 Surface 입력(AndroidOpaque)을 지원하지 않음
  - `createInputSurface requires COLOR_FormatSurface (AndroidOpaque) color format`
- 하드웨어 AVC 인코더(`OMX.TCC.*enc`)는 xml에 등록돼 있지 않음. 라이브러리가 존재하는지는 아직 확인하지 않음
- 소프트웨어 H.264 인코더 자체는 로드됨 (`setupVideoEncoder succeeded`). ByteBuffer 입력 방식이면 쓸 수 있음

## USB
- vfat `/mnt/media_rw/usb0`, 그 위에 FUSE `/storage/usb0` (`sync` 옵션), root로 쓰기 정상
- `ro.product.ntfsexfat.rw=true`

## 다음 확인 사항 (녹화 대안)
1. `/system/lib`에 TCC 하드웨어 인코더 라이브러리가 있는지, 이름으로 생성할 수 있는지 (`OMX.TCC.ENC.avc` 등)
2. 프로세스 안에서 반복 캡처하는 속도 (`SurfaceControl.screenshot` via `app_process`)
3. `/dev/graphics/fb0`를 직접 읽었을 때 합성된 화면이 나오는지와 그 속도
4. `/system/bin/screenshot`(42KB, 제조사 도구로 추정)의 동작
5. 소프트웨어 인코더(ByteBuffer)로 1920×720 또는 축소 해상도에서 낼 수 있는 fps
