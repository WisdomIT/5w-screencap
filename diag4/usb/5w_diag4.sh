#!/system/bin/sh
# 5w_screencap probe #4: can the car's sound be recorded? (read-only, changes nothing)
#
# Play music (Bluetooth or CarPlay) before starting and keep it playing. Each test shows a
# numbered notice; note any number where the music cut out (REMOTE_SUBMIX may do that).
# Runs from a NEMESIS type=execute USB stick, as root, cwd = USB root. Needs 5w_diag4.jar.
# Writes into <USB>/5W-DIAG4-<time>/. KitKat toolbox + mksh only.

export PATH=${DIAG_PATH:-/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin}
export LD_LIBRARY_PATH=/vendor/lib:/system/lib

USB=${DIAG_USB:-$PWD}
TMP=${DIAG_TMP:-/data/local/tmp}/5wdiag4
PROC=${DIAG_PROC:-/proc}
SYS=${DIAG_SYS:-/system}

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
    *) STAMP=run$$ ;;
esac
OUT=$USB/5W-DIAG4-$STAMP
mkdir -p "$OUT" || exit 2
rm -r "$TMP" 2>/dev/null
mkdir -p "$TMP"
REPORT=$OUT/report.txt
: > "$REPORT"

sleep 3   # let the NEMESIS dialog close

R "# 5w_screencap diag4 (sound)  stamp=$STAMP"
R "product=$(getprop ro.build.product2)  platform=$(getprop ro.board.platform)"

# ---------------------------------------------------------------- 1. audio setup

section "1. audio setup"
cp "$SYS/etc/audio_policy.conf" "$OUT/audio_policy.conf" 2>/dev/null && R "audio_policy.conf copied"
ls -l "$SYS/lib/hw" 2>/dev/null | grep -iE 'audio' > "$OUT/audio_hals.txt"
while IFS= read -r line; do R "hal: $line"; done < "$OUT/audio_hals.txt"
for f in cards pcm devices; do
    cat "$PROC/asound/$f" > "$OUT/asound_$f.txt" 2>&1
done
R "asound -> asound_cards.txt, asound_pcm.txt, asound_devices.txt"
while IFS= read -r line; do R "pcm: $line"; done < "$OUT/asound_pcm.txt"
ls -l /dev/snd > "$OUT/dev_snd.txt" 2>&1
getprop 2>/dev/null | grep -iE 'audio|a2dp|bluetooth\.a2dp|media\.|af\.' | grep -viE 'addr|mac|name' > "$OUT/audio_props.txt"
R "audio props -> audio_props.txt"

# ---------------------------------------------------------------- 2. what is playing now

section "2. playback state (before the probe)"
dumpsys media.audio_flinger > "$OUT/audio_flinger_before.txt" 2>&1
dumpsys media.audio_policy > "$OUT/audio_policy_state.txt" 2>&1
dumpsys audio > "$OUT/dumpsys_audio.txt" 2>&1
R "dumpsys -> audio_flinger_before.txt, audio_policy_state.txt, dumpsys_audio.txt"
grep -iE 'Output thread|Input thread|Devices:|Standby:|active tracks|Hal stream|Sample rate|Channel count' "$OUT/audio_flinger_before.txt" > "$OUT/audio_flinger_summary.txt" 2>/dev/null
while IFS= read -r line; do R "af: $line"; done < "$OUT/audio_flinger_summary.txt"

# ---------------------------------------------------------------- 3. record each source

section "3. AudioRecord probe (keep the music playing)"
load_android_env || R "WARN: no /init.environ.rc"
cp "$USB/5w_diag4.jar" "$TMP/5w_diag4.jar" 2>/dev/null
if [ -f "$TMP/5w_diag4.jar" ]; then
    export CLASSPATH=$TMP/5w_diag4.jar
    app_process /system/bin fivew.diag.AudioProbe "$OUT" 3 > "$OUT/probe_stdout.log" 2>&1 &
    PP=$!
    _i=0
    while kill -0 "$PP" 2>/dev/null; do
        if [ "$_i" -ge 180 ]; then kill -9 "$PP" 2>/dev/null; R "probe TIMEOUT, killed"; break; fi
        sleep 1
        _i=$((_i + 1))
    done
    wait "$PP" 2>/dev/null
    R "probe rc=$?  -> audio.txt and audio_*.wav"
else
    R "5w_diag4.jar missing on USB"
fi

# ---------------------------------------------------------------- 4. after

section "4. after"
dumpsys media.audio_flinger > "$OUT/audio_flinger_after.txt" 2>&1
MSPID=$(set -- $(ps 2>/dev/null | grep /system/bin/mediaserver); echo "$2")
logcat -d -v time 2>/dev/null | grep -iE 'AudioRecord|AudioFlinger|AudioPolicy|audio_hw|submix|r_submix|tcc.*audio|AudioSource|RecordThread' > "$OUT/logcat_audio.txt" 2>&1
R "logcat (audio) -> logcat_audio.txt; mediaserver pid=$MSPID"

rm -r "$TMP" 2>/dev/null
R ""
R "done"
sync
exit 0
