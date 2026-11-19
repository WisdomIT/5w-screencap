# 2차 실차 진단 결과: 니로 (2026-11-19 20:42)

차량: `DEPEHEV.KOR.S5W_M.V039.001.260902`, tcc897x, 1920×720

## 캡처 경로 (모두 동작)
| 방법 | 결과 | 속도 |
|---|---|---|
| `SurfaceControl.screenshot` (Java, app_process root) | ✅ 1920×720, 색상 정상 | **15ms** (1280×480: 10ms, 640×240: 7ms) |
| └ `Bitmap.getPixels` | 느림 | 1920×720: 257ms, 1280×480: 114ms. 다음엔 `copyPixelsToBuffer`로 측정할 것 |
| └ JPEG(92) 압축+저장 | ✅ 244KB | **495ms** (PNG는 4193ms) |
| `/dev/graphics/fb0` 직접 읽기 | ✅ 합성된 최종 화면 (BGRA, 1920×2160 = 버퍼 3장) | dd로 장당 약 0.9초. 비캐시 메모리라 느림 |
| `/system/bin/screenshot FILE` | ✅ PNG 생성 (AOSP의 구형 fb0 기반 도구, `-s` 사운드, `-i` 자동 번호) | 약 5초 (PNG 압축) |

→ **캡처는 `SurfaceControl.screenshot` + JPEG 조합이 가장 좋음.** PNG보다 8배 이상 빠름.

## 녹화용 인코더
### 하드웨어 AVC (`OMX.TCC.ENC.avc`): 존재하지만 생성 실패
- `libOMX.TCC.VideoEnc.so`에 `OMX.TCC.ENC.avc`/`mpeg4`/`h263`이 있고, `/dev/vpu_venc`도 있음
- TCC_OMXCore가 라이브러리 로드까지는 했으나 `Failed to allocate component instance` (0x80001003 ComponentNotFound)
- 컴포넌트 내부 로그는 이번 필터에 걸리지 않아 원인 미상 → 다음 진단에서 mediaserver(pid 164) 로그 전체를 수집해야 함

### 소프트웨어 H.264 (`OMX.google.h264.encoder`): 해상도 제한으로 실패
- start() 직후 `PVAVCEncInitialize`가 실패하고 `OMX_ErrorUndefined` → 이후 `getOutputBuffers`에서 IllegalStateException
- 원인: KitKat `SoftAVCEncoder`가 **Level 2까지만 허용** (Google이 의도적으로 `#if 0`). 최대 396 MB/프레임(CIF), 11880 MB/s, 2Mbps
- 1280×480(2400 MB), 640×240(600 MB), 1920×720(5400 MB) 모두 초과
- 쓸 수 있는 최대치는 예를 들어 512×192(384 MB) @ ≤30fps

### 소프트웨어 MPEG-4 Part 2 (`OMX.google.mpeg4.encoder`): 미시험, 유력
- Core Profile Level 2 고정: **23760 MB/s**, 최대 2Mbps
- 1024×384(1536 MB) → ≤15fps / 1280×480(2400 MB) → ≤9fps / 1920×720(5400 MB) → ≤4fps
- MediaMuxer(API 19)로 MP4에 담을 수 있음

### 소프트웨어 VP8 (`OMX.google.vp8.encoder`): 미시험
- libvpx realtime, 레벨 제한 없음, 멀티스레드
- 단, API 19의 MediaMuxer는 VP8을 지원하지 않음 → IVF나 WebM 기록기를 직접 만들어야 함

## 기타
- 프로브 코드 결함: 인코더의 비동기 오류를 감지하지 못해서 다른 색 포맷이나 파라미터로 재시도하지 않았음. 3차에서 보완 필요

## 3차 진단 후보
1. MPEG-4 소프트웨어 인코더: 1024×384@15, 1280×480@9, 1920×720@4 → 실제 fps와 MP4 재생 확인
2. H.264 소프트웨어 인코더 (profile=Baseline, level=2 명시): 512×192
3. VP8 소프트웨어 인코더 + IVF 기록: 1280×480, 1920×720
4. `copyPixelsToBuffer` + Java YUV 변환 속도
5. JPEG 연속 저장(MJPEG) 속도: 960×360, 1280×480
6. `OMX.TCC.ENC.avc` 생성을 다시 시도하면서 전체 logcat 수집 (하드웨어 인코더 실패 원인)
