# 5w-screencap

현대·기아 Gen5W(S5W_M, Android 4.4.2) 헤드유닛에서 화면을 캡처·녹화해 USB에 저장하는 NEMESIS 패키지 프로젝트.

## 현재 상태: 구현 가능성 검토 완료
- 캡처: 가능 (`SurfaceControl.screenshot` → JPEG/PNG)
- 녹화: 가능 (자체 녹화기 → 소프트웨어 MPEG-4 인코더 → MP4). 내장 `screenrecord`는 불가
- 상세: [diag/results/FINDINGS-20261119-niro-diag3.md](diag/results/FINDINGS-20261119-niro-diag3.md)

## 구성
| 경로 | 내용 |
|---|---|
| `diag/` | 1차 진단 (셸): 바이너리, 디스플레이, USB 마운트, screencap/screenrecord |
| `diag2/` | 2차 진단 (셸 + Java 프로브): 인코더 라이브러리, fb0, 캡처 속도 |
| `diag3/` | 3차 진단 (Java 프로브): 인코더별 실제 녹화, 병목 측정 |
| `diag/results/` | 실차(니로 HEV, V039) 진단 결과와 분석 |

## 빌드 (Java 프로브)
JDK와 d8이 필요합니다. d8은 Google Maven에서 받아 `.tools/`에 둡니다.
```bash
mkdir -p .tools && curl -fL -o .tools/r8-8.5.35.jar https://dl.google.com/android/maven2/com/android/tools/r8/8.5.35/r8-8.5.35.jar
```
```bash
diag3/build.sh
```
