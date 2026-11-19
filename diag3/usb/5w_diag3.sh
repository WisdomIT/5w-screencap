#!/system/bin/sh
# 5w_screencap probe #3: real recording with each software encoder (read-only, changes nothing).
#
# Runs from a NEMESIS type=execute USB stick, as root, cwd = USB root.
# Needs 5w_diag3.jar next to this script. Writes into <USB>/5W-DIAG3-<time>/.
# KitKat toolbox + mksh R43 only.

export PATH=${DIAG_PATH:-/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin}
export LD_LIBRARY_PATH=/vendor/lib:/system/lib

USB=${DIAG_USB:-$PWD}
TMP=${DIAG_TMP:-/data/local/tmp}/5wdiag3
PROC=${DIAG_PROC:-/proc}

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

R() { echo "$*" >> "$REPORT"; }
section() { R ""; R "===== $*"; }

load_android_env() {
    [ -n "$BOOTCLASSPATH" ] && return 0
    [ -r /init.environ.rc ] || return 1
    while read _cmd _name _value; do
        [ "$_cmd" = export ] && export "$_name=$_value"
    done < /init.environ.rc
}

STAMP=$(date +%Y%m%d-%H%M%S 2>/dev/null)
case "$STAMP" in
    [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]) ;;
    *) STAMP=up$(now_cs) ;;
esac
OUT=$USB/5W-DIAG3-$STAMP
mkdir -p "$OUT" || exit 2
rm -r "$TMP" 2>/dev/null
mkdir -p "$TMP"
REPORT=$OUT/report.txt
: > "$REPORT"

sleep 3   # let the NEMESIS dialog close

R "# 5w_screencap diag3  stamp=$STAMP"
R "product=$(getprop ro.build.product2)  platform=$(getprop ro.board.platform)"
MSPID=$(set -- $(ps 2>/dev/null | grep /system/bin/mediaserver); echo "$2")
R "mediaserver pid=$MSPID"

# ---------------------------------------------------------------- CPU sampler (background)

: > "$OUT/top.txt"
: > "$TMP/sampling"
(
    while [ -f "$TMP/sampling" ]; do
        echo "--- t=$(now_cs)" >> "$OUT/top.txt"
        top -n 1 -m 8 >> "$OUT/top.txt" 2>&1
        sleep 8
    done
) &
SAMPLER=$!

# ---------------------------------------------------------------- Java probe

section "Java probe"
load_android_env || R "WARN: no /init.environ.rc"
cp "$USB/5w_diag3.jar" "$TMP/5w_diag3.jar" 2>/dev/null
if [ -f "$TMP/5w_diag3.jar" ]; then
    export CLASSPATH=$TMP/5w_diag3.jar
    _t0=$(now_cs)
    app_process /system/bin fivew.diag.Probe3 "$OUT" 180 > "$OUT/probe_stdout.log" 2>&1 &
    PP=$!
    R "probe pid=$PP"
    _i=0
    while kill -0 "$PP" 2>/dev/null; do
        if [ "$_i" -ge 240 ]; then kill -9 "$PP" 2>/dev/null; R "probe TIMEOUT, killed"; break; fi
        sleep 1
        _i=$((_i + 1))
    done
    wait "$PP" 2>/dev/null
    R "probe rc=$? secs=$(( ($(now_cs) - _t0) / 100 ))  -> probe3.txt"
else
    R "5w_diag3.jar missing on USB"
fi

rm "$TMP/sampling" 2>/dev/null
wait "$SAMPLER" 2>/dev/null

# ---------------------------------------------------------------- logs

section "logcat"
# everything from mediaserver and the probe process (OMX component logs live there)
logcat -d -v time 2>/dev/null | grep -E "\( *($MSPID|$PP)\)" > "$OUT/logcat_codec_pids.txt" 2>&1
logcat -d -v time 2>/dev/null | grep -iE 'OMX|ACodec|MediaCodec|MediaMuxer|MPEG4Writer|SoftAVC|SoftMPEG4|SoftVPX|VPU|TCC_|AndroidRuntime|dalvikvm' | grep -v 'Tcc317X' > "$OUT/logcat_filtered.txt" 2>&1
R "logcat -> logcat_codec_pids.txt, logcat_filtered.txt"
R "mediaserver now: $(ps 2>/dev/null | grep mediaserver)"

rm -r "$TMP" 2>/dev/null
R ""
R "done"
sync

am broadcast -a com.hkmc.intent.action.TOAST \
    -e com.hkmc.intent.extra.TOAST_MESSAGE "5W 3차 진단 완료: USB의 5W-DIAG3-$STAMP 폴더" \
    --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true > /dev/null 2>&1
exit 0
