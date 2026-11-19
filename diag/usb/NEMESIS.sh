#!/system/bin/sh
# 5w_screencap feasibility probe (read-only, changes nothing on the head unit).
#
# Runs from a NEMESIS type=execute USB stick, as root, cwd = USB root.
# Writes everything into <USB>/5W-DIAG-<time>/ and nothing anywhere else
# except a few temp files under /data/local/tmp (removed at the end).
#
# KitKat toolbox + mksh R43 only: no head/sed/awk/printf/timeout/stat/tee.
#
# Test overrides (for running off the car): DIAG_PATH, DIAG_USB, DIAG_TMP,
# DIAG_PROC, DIAG_SYS, DIAG_DEV.

export PATH=${DIAG_PATH:-/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin}
export LD_LIBRARY_PATH=/vendor/lib:/system/lib

USB=${DIAG_USB:-$PWD}
TMP=${DIAG_TMP:-/data/local/tmp}/5wdiag
PROC=${DIAG_PROC:-/proc}
SYS=${DIAG_SYS:-/system}
DEV=${DIAG_DEV:-/dev}

# ---------------------------------------------------------------- helpers

# centiseconds since boot, from /proc/uptime ("1234.56 ...")
now_cs() {
    _up=0
    IFS= read -r _up < "$PROC/uptime" 2>/dev/null
    _up=${_up%% *}
    case "$_up" in
        *.*) _int=${_up%.*}; _frac=${_up#*.}; _frac=${_frac}00; _frac=${_frac%${_frac#??}} ;;
        *)   _int=${_up:-0}; _frac=00 ;;
    esac
    # strip leading zeros of the fraction so $(( )) does not read octal
    case "$_frac" in 0?) _frac=${_frac#0} ;; esac
    echo $(( _int * 100 + _frac ))
}

# size in bytes of a file, or -1 if missing (toolbox ls -l: perms user group SIZE date time name)
fsize() {
    [ -f "$1" ] || { echo -1; return; }
    set -- $(ls -l "$1" 2>/dev/null)
    echo "${4:--1}"
}

# run_limit SECONDS LOGFILE cmd args...  -> sets RC (exit code or "timeout") and MS (elapsed ms)
run_limit() {
    _lim=$1; _log=$2; shift 2
    _t0=$(now_cs)
    "$@" > "$_log" 2>&1 &
    _p=$!
    _i=0
    RC=
    while kill -0 "$_p" 2>/dev/null; do
        if [ "$_i" -ge "$_lim" ]; then
            kill -9 "$_p" 2>/dev/null
            wait "$_p" 2>/dev/null
            RC=timeout
            break
        fi
        sleep 1
        _i=$((_i + 1))
    done
    if [ -z "$RC" ]; then wait "$_p"; RC=$?; fi
    MS=$(( ($(now_cs) - _t0) * 10 ))
}

R() { echo "$*" >> "$REPORT"; }
section() { R ""; R "===== $*"; }

# ---------------------------------------------------------------- output dir

STAMP=$(date +%Y%m%d-%H%M%S 2>/dev/null)
case "$STAMP" in
    [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]) ;;
    *) STAMP=up$(now_cs) ;;
esac
OUT=$USB/5W-DIAG-$STAMP
mkdir -p "$OUT" || exit 2
mkdir -p "$TMP"
REPORT=$OUT/report.txt
: > "$REPORT"

# Let the NEMESIS Yes/No dialog finish closing before anything is captured.
sleep 3

R "# 5w_screencap diag  stamp=$STAMP"
R "date=$(date 2>/dev/null)"

# ---------------------------------------------------------------- 1. system

section "1. system"
R "id=$(id 2>&1)"
IFS= read -r KVER < "$PROC/version" 2>/dev/null
R "kernel=$KVER"
R "usb_root=$USB"
R "nemesis_version=$(nemesis version 2>&1)"
# build/product/board props only (no serial numbers)
getprop 2>/dev/null | grep -E '^\[(ro\.build|ro\.product|ro\.board|ro\.hardware|ro\.sf|ro\.opengles|ro\.revision|sys\.boot_completed)' > "$OUT/props.txt" 2>&1
R "props -> props.txt"

# ---------------------------------------------------------------- 2. binaries

section "2. binaries"
for b in screencap screenrecord screenshot; do
    for d in "$SYS/bin" "$SYS/xbin"; do
        [ -e "$d/$b" ] && R "$(ls -l "$d/$b" 2>&1)  [$d]"
    done
    command -v "$b" > /dev/null 2>&1 && R "$b: on PATH" || R "$b: NOT on PATH"
done
R "fb: $(ls -l "$DEV/graphics" 2>&1)"

# ---------------------------------------------------------------- 3. display

section "3. display"
R "wm size: $(wm size 2>&1)"
R "wm density: $(wm density 2>&1)"
dumpsys display > "$OUT/dumpsys_display.txt" 2>&1
dumpsys SurfaceFlinger > "$OUT/dumpsys_surfaceflinger.txt" 2>&1
R "dumpsys display -> dumpsys_display.txt"
R "dumpsys SurfaceFlinger -> dumpsys_surfaceflinger.txt (HWC layer table: OVERLAY vs FB)"
grep -iE 'mDisplayId|DisplayDeviceInfo|PhysicalDisplayInfo|real [0-9]+ x [0-9]+' "$OUT/dumpsys_display.txt" > "$OUT/display_summary.txt" 2>/dev/null
R "display summary -> display_summary.txt"

# ---------------------------------------------------------------- 4. usb / storage

section "4. usb"
grep -E 'usb|media_rw|fuse|vold|/storage' "$PROC/mounts" > "$OUT/mounts.txt" 2>&1
while IFS= read -r line; do R "mount: $line"; done < "$OUT/mounts.txt"
R "ls /storage: $(ls /storage 2>&1)"
R "ls /mnt/media_rw: $(ls /mnt/media_rw 2>&1)"
R "df: $(df "$USB" 2>&1)"
echo "write-test" > "$OUT/write_test.txt" 2>/dev/null
IFS= read -r back < "$OUT/write_test.txt" 2>/dev/null
[ "$back" = "write-test" ] && R "usb_write=ok" || R "usb_write=FAIL"
cp "$SYS/etc/media_codecs.xml" "$OUT/media_codecs.xml" 2>/dev/null && R "media_codecs.xml copied"
grep -iE 'encoder|ENC' "$SYS/etc/media_codecs.xml" > "$OUT/encoders.txt" 2>/dev/null
while IFS= read -r line; do R "codec: $line"; done < "$OUT/encoders.txt"

# ---------------------------------------------------------------- 5. screencap

section "5. screencap"

run_limit 15 "$OUT/cap_direct.log" screencap -p "$OUT/cap_direct.png"
R "cap_direct      rc=$RC ms=$MS size=$(fsize "$OUT/cap_direct.png")  (PNG straight to USB)"

run_limit 15 "$OUT/cap_tmp.log" screencap -p "$TMP/cap.png"
R "cap_tmp         rc=$RC ms=$MS size=$(fsize "$TMP/cap.png")  (PNG to /data/local/tmp)"
cp "$TMP/cap.png" "$OUT/cap_via_tmp.png" 2>/dev/null
R "cap_via_tmp copy size=$(fsize "$OUT/cap_via_tmp.png")"

# raw dump: 12-byte header (w, h, format) + pixels -> tells real resolution/format
run_limit 15 "$OUT/cap_raw.log" screencap "$TMP/cap.raw"
R "cap_raw         rc=$RC ms=$MS size=$(fsize "$TMP/cap.raw")  (raw, = 12 + w*h*bpp)"
cp "$TMP/cap.raw" "$OUT/cap.raw" 2>/dev/null

# 3 shots in a row: rough upper bound for a screencap-based frame rate
_t=$(now_cs)
for n in 1 2 3; do screencap "$TMP/burst.raw" > /dev/null 2>&1; done
R "cap_raw_x3      ms=$(( ($(now_cs) - _t) * 10 ))"

# other display ids (cluster / secondary panels, if any)
for d in 1 2 3; do
    run_limit 10 "$OUT/cap_d$d.log" screencap -d $d -p "$OUT/cap_display$d.png"
    R "cap_display$d    rc=$RC ms=$MS size=$(fsize "$OUT/cap_display$d.png")"
done

# ---------------------------------------------------------------- 6. screenrecord

section "6. screenrecord"
screenrecord --help > "$OUT/rec_help.txt" 2>&1
R "help -> rec_help.txt"

# a) default size, 5 s, straight to USB
run_limit 25 "$OUT/rec_default.log" screenrecord --verbose --time-limit 5 "$OUT/rec_default.mp4"
R "rec_default     rc=$RC ms=$MS size=$(fsize "$OUT/rec_default.mp4")  (display size, USB)"

# b) 1280x720 / 4 Mbps, 5 s, to /data/local/tmp then copied
run_limit 25 "$OUT/rec_720p.log" screenrecord --verbose --size 1280x720 --bit-rate 4000000 --time-limit 5 "$TMP/rec_720p.mp4"
R "rec_720p_tmp    rc=$RC ms=$MS size=$(fsize "$TMP/rec_720p.mp4")  (1280x720, tmp)"
cp "$TMP/rec_720p.mp4" "$OUT/rec_720p.mp4" 2>/dev/null

# c) stop by signal: start a 60 s recording, ask it to stop after ~6 s
screenrecord --verbose --time-limit 60 "$OUT/rec_sigint.mp4" > "$OUT/rec_sigint.log" 2>&1 &
RP=$!
sleep 6
STOPPED_BY=none
for sig in INT HUP; do
    kill -0 "$RP" 2>/dev/null || break
    kill -$sig "$RP" 2>/dev/null
    _i=0
    while kill -0 "$RP" 2>/dev/null && [ "$_i" -lt 8 ]; do sleep 1; _i=$((_i + 1)); done
    kill -0 "$RP" 2>/dev/null || { STOPPED_BY=$sig; break; }
done
if kill -0 "$RP" 2>/dev/null; then kill -9 "$RP" 2>/dev/null; STOPPED_BY=KILL; fi
wait "$RP" 2>/dev/null
R "rec_sigint      stopped_by=$STOPPED_BY size=$(fsize "$OUT/rec_sigint.mp4")  (INT, then HUP, then KILL)"

# ---------------------------------------------------------------- 7. logs

section "7. logcat"
logcat -d -v time 2>/dev/null | grep -iE 'screenrecord|screencap|ACodec|OMXCodec|MediaCodec|OMX|TCC.*ENC|VirtualDisplay|SurfaceFlinger|mediaserver|MediaMuxer' > "$OUT/logcat_filtered.txt" 2>&1
R "logcat (filtered) -> logcat_filtered.txt"
R "mediaserver now: $(ps 2>/dev/null | grep mediaserver)"

# ---------------------------------------------------------------- done

rm -r "$TMP" 2>/dev/null
R ""
R "done"
sync

am broadcast -a com.hkmc.intent.action.TOAST \
    -e com.hkmc.intent.extra.TOAST_MESSAGE "5W 진단 완료: USB의 5W-DIAG-$STAMP 폴더" \
    --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true > /dev/null 2>&1
exit 0
