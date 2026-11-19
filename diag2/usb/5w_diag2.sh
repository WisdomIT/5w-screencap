#!/system/bin/sh
# 5w_screencap probe #2: what can replace screenrecord (read-only, changes nothing).
#
# Runs from a NEMESIS type=execute USB stick, as root, cwd = USB root.
# Needs 5w_diag2.jar next to this script. Writes into <USB>/5W-DIAG2-<time>/.
# Temp files go to /data/local/tmp/5wdiag2 and are removed at the end.
#
# KitKat toolbox + mksh R43 only.

export PATH=${DIAG_PATH:-/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin}
export LD_LIBRARY_PATH=/vendor/lib:/system/lib

USB=${DIAG_USB:-$PWD}
TMP=${DIAG_TMP:-/data/local/tmp}/5wdiag2
PROC=${DIAG_PROC:-/proc}
SYS=${DIAG_SYS:-/system}
DEV=${DIAG_DEV:-/dev}
SYSFS=${DIAG_SYSFS:-/sys}

# ---------------------------------------------------------------- helpers

now_cs() {
    _up=0
    IFS= read -r _up < "$PROC/uptime" 2>/dev/null
    _up=${_up%% *}
    case "$_up" in
        *.*) _int=${_up%.*}; _frac=${_up#*.}; _frac=${_frac}00; _frac=${_frac%${_frac#??}} ;;
        *)   _int=${_up:-0}; _frac=00 ;;
    esac
    case "$_frac" in 0?) _frac=${_frac#0} ;; esac
    echo $(( _int * 100 + _frac ))
}

fsize() {
    [ -f "$1" ] || { echo -1; return; }
    set -- $(ls -l "$1" 2>/dev/null)
    echo "${4:--1}"
}

# run_limit SECONDS LOGFILE cmd args...  -> RC (exit code or "timeout"), MS (elapsed ms, ~1 s steps)
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

load_android_env() {
    [ -n "$BOOTCLASSPATH" ] && return 0
    [ -r /init.environ.rc ] || return 1
    while read _cmd _name _value; do
        [ "$_cmd" = export ] && export "$_name=$_value"
    done < /init.environ.rc
}

# ---------------------------------------------------------------- output dir

STAMP=$(date +%Y%m%d-%H%M%S 2>/dev/null)
case "$STAMP" in
    [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]) ;;
    *) STAMP=up$(now_cs) ;;
esac
OUT=$USB/5W-DIAG2-$STAMP
mkdir -p "$OUT" || exit 2
rm -r "$TMP" 2>/dev/null
mkdir -p "$TMP"
REPORT=$OUT/report.txt
: > "$REPORT"

sleep 3   # let the NEMESIS dialog close

R "# 5w_screencap diag2  stamp=$STAMP"
R "product=$(getprop ro.build.product2)  platform=$(getprop ro.board.platform)"

# ---------------------------------------------------------------- 1. encoder libraries

section "1. libraries / devices"
ls -l "$SYS/lib" 2>/dev/null | grep -iE 'omx|tcc|venc|enc|vpu|stagefright|codec' > "$OUT/libs_system.txt"
ls -l "$SYS/vendor/lib" 2>/dev/null | grep -iE 'omx|tcc|venc|enc|vpu|stagefright|codec' > "$OUT/libs_vendor.txt"
R "libs -> libs_system.txt, libs_vendor.txt"
ls -l "$DEV" 2>/dev/null | grep -iE 'vpu|venc|vdec|jpu|tcc|graphics|pmap|admem|mem' > "$OUT/dev_nodes.txt"
while IFS= read -r line; do R "dev: $line"; done < "$OUT/dev_nodes.txt"
ls "$SYS/etc" 2>/dev/null | grep -iE 'media|omx|codec' > "$OUT/etc_media_files.txt"
cp "$SYS/etc/media_profiles.xml" "$OUT/media_profiles.xml" 2>/dev/null && R "media_profiles.xml copied"

# OMX component names baked into the libraries (finds encoders not listed in media_codecs.xml)
: > "$OUT/omx_names.txt"
for f in "$SYS"/lib/*.so "$SYS"/vendor/lib/*.so; do
    [ -f "$f" ] || continue
    grep -q 'OMX\.TCC' "$f" 2>/dev/null || continue
    for n in $(grep -aoE 'OMX\.TCC\.[A-Za-z0-9_.]+' "$f" 2>/dev/null); do
        echo "${f##*/}:$n" >> "$OUT/omx_names.txt"
    done
done
grep -iE 'enc' "$OUT/omx_names.txt" > "$OUT/omx_encoder_names.txt" 2>/dev/null
while IFS= read -r line; do R "omx enc: $line"; done < "$OUT/omx_encoder_names.txt"

# ---------------------------------------------------------------- 2. framebuffer

section "2. /dev/graphics/fb0"
for k in name virtual_size bits_per_pixel stride modes pan; do
    v=$(cat "$SYSFS/class/graphics/fb0/$k" 2>&1)
    R "fb0 $k: $v"
done
run_limit 15 "$OUT/fb0_dd.log" dd if="$DEV/graphics/fb0" of="$TMP/fb0_full.raw" bs=1048576 count=24
R "fb0 read (up to 24 MiB) rc=$RC ms=$MS size=$(fsize "$TMP/fb0_full.raw")"
cp "$TMP/fb0_full.raw" "$OUT/fb0_full.raw" 2>/dev/null
rm "$TMP/fb0_full.raw" 2>/dev/null
# 10 single-frame reads (1920*720*4) to /dev/null
_t=$(now_cs)
for n in 1 2 3 4 5 6 7 8 9 10; do
    dd if="$DEV/graphics/fb0" of=/dev/null bs=5529600 count=1 > /dev/null 2>&1
done
R "fb0 10 frame reads ms=$(( ($(now_cs) - _t) * 10 ))"

# ---------------------------------------------------------------- 3. /system/bin/screenshot

section "3. /system/bin/screenshot"
cp "$SYS/bin/screenshot" "$OUT/bin_screenshot" 2>/dev/null && R "binary copied -> bin_screenshot"
for d in /sdcard /sdcard/Pictures /sdcard/Pictures/Screenshots /data/local/tmp "$TMP"; do
    R "before $d: $(ls "$d" 2>&1)"
done
cd "$TMP"
run_limit 10 "$OUT/screenshot_help.log" screenshot -h
R "screenshot -h rc=$RC ms=$MS"
run_limit 15 "$OUT/screenshot_run.log" screenshot
R "screenshot (no args) rc=$RC ms=$MS"
run_limit 15 "$OUT/screenshot_file.log" screenshot "$TMP/ss_test.png"
R "screenshot FILE rc=$RC ms=$MS size=$(fsize "$TMP/ss_test.png")"
cd "$USB"
for d in /sdcard /sdcard/Pictures /sdcard/Pictures/Screenshots /data/local/tmp "$TMP"; do
    R "after  $d: $(ls "$d" 2>&1)"
done
cp "$TMP"/*.png "$OUT/" 2>/dev/null

# ---------------------------------------------------------------- 4. Java probe

section "4. Java probe (screenshot speed, encoders, MP4)"
load_android_env || R "WARN: no /init.environ.rc"
cp "$USB/5w_diag2.jar" "$TMP/5w_diag2.jar" 2>/dev/null
if [ -f "$TMP/5w_diag2.jar" ]; then
    export CLASSPATH=$TMP/5w_diag2.jar
    run_limit 220 "$OUT/probe_stdout.log" \
        app_process /system/bin fivew.diag.Probe "$OUT" "$OUT/omx_names.txt" 170
    R "probe rc=$RC ms=$MS  -> probe.txt"
else
    R "5w_diag2.jar missing on USB"
fi

# ---------------------------------------------------------------- 5. logs

section "5. logcat"
logcat -d -v time 2>/dev/null | grep -iE 'fivew|Probe|ACodec|OMXCodec|OMXNode|MediaCodec|MediaMuxer|TCC.*(enc|ENC)|SurfaceFlinger|AndroidRuntime|dalvikvm|screenshot' | grep -v 'Tcc317X' > "$OUT/logcat_filtered.txt" 2>&1
R "logcat (filtered) -> logcat_filtered.txt"
R "mediaserver now: $(ps 2>/dev/null | grep mediaserver)"

rm -r "$TMP" 2>/dev/null
R ""
R "done"
sync

am broadcast -a com.hkmc.intent.action.TOAST \
    -e com.hkmc.intent.extra.TOAST_MESSAGE "5W 2차 진단 완료: USB의 5W-DIAG2-$STAMP 폴더" \
    --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true > /dev/null 2>&1
exit 0
