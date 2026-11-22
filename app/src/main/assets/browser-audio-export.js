(() => {
    "use strict";
    if (window !== window.top || window.__profynusExport) return;
    const config = __EXPORT_CONFIG__;
    const bridge = window.ProfynusAudio;
    if (!bridge || !window.MediaSource || !window.SourceBuffer) return;
    const MAX_QUEUE = 8 * 1024 * 1024;
    const CHUNK = 48 * 1024;
    let active = true, selected = null, queue = [], queued = 0;
    let sequence = 0, awaiting = null, lastAck = Date.now(), finishing = false;
    let duration = 0, covered = 0, lastProgress = Date.now(), lastSeek = 0;
    let lastReport = 0, video = null, original = null, pendingBytes = 0;
    const buffers = new WeakMap();
    const candidates = [];
    const ranges = [];
    const send = (type, fields = {}) => bridge.postMessage(JSON.stringify({
        token: config.token, type, ...fields
    }));
    function stop() {
        active = false;
        queue = [];
        queued = 0;
        for (const state of candidates) state.pending = [];
        clearInterval(timer);
        if (video && original) {
            video.pause();
            video.muted = original.muted;
            video.playbackRate = original.rate;
        }
    }
    function fail(reason) {
        if (!active) return;
        send("error", {reason});
        stop();
    }
    window.__profynusExport = {stop};
    function pump() {
        if (!active || awaiting !== null || !queue.length) return;
        const item = queue[0];
        const part = item.subarray(0, CHUNK);
        let binary = "";
        for (let i = 0; i < part.length; i += 8192) {
            binary += String.fromCharCode.apply(null, part.subarray(i, i + 8192));
        }
        awaiting = sequence++;
        lastAck = Date.now();
        send("chunk", {sequence: awaiting, data: btoa(binary)});
        queued -= part.length;
        if (part.length === item.length) queue.shift();
        else queue[0] = item.subarray(part.length);
    }
    bridge.onmessage = event => {
        if (!active) return;
        let reply;
        try { reply = JSON.parse(event.data); } catch (_) { return; }
        if (reply.token !== config.token || reply.sequence !== awaiting) return;
        awaiting = null;
        pump();
        finishIfDrained();
    };
    function enqueue(bytes) {
        if (!active) return;
        if (queued + bytes.length > MAX_QUEUE) {
            fail("Audio transfer could not keep up. Retry the export.");
            return;
        }
        queue.push(bytes);
        queued += bytes.length;
        pump();
    }
    function identity() {
        const player = document.getElementById("movie_player");
        const response = player && typeof player.getPlayerResponse === "function"
            ? player.getPlayerResponse() : window.ytInitialPlayerResponse;
        const details = response && response.videoDetails;
        return {
            id: details && details.videoId,
            title: details && details.title,
            live: details && details.isLiveContent && !(Number(details.lengthSeconds) > 0),
            seconds: Number(details && details.lengthSeconds)
        };
    }
    function mergeRange(start, end) {
        if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return;
        ranges.push([start, end]);
        ranges.sort((a, b) => a[0] - b[0]);
        for (let i = 1; i < ranges.length;) {
            if (ranges[i][0] <= ranges[i - 1][1] + 0.08) {
                ranges[i - 1][1] = Math.max(ranges[i - 1][1], ranges[i][1]);
                ranges.splice(i, 1);
            } else i++;
        }
        const next = ranges.length && ranges[0][0] <= 0.15 ? ranges[0][1] : 0;
        if (next > covered + 0.01) lastProgress = Date.now();
        covered = next;
    }
    function finishIfDrained() {
        if (!active || !finishing || queue.length || awaiting !== null) return;
        send("finish", {duration, title: identity().title || config.videoId, chunks: sequence});
        stop();
    }
    const add = MediaSource.prototype.addSourceBuffer;
    const append = SourceBuffer.prototype.appendBuffer;
    MediaSource.prototype.addSourceBuffer = function(mime) {
        const buffer = add.call(this, mime);
        if (active && /^audio\/(webm|mp4)(;|$)/i.test(mime)) {
            const state = {buffer, mime, pending: [], bytes: 0};
            buffers.set(buffer, state);
            candidates.push(state);
            buffer.addEventListener("error", () => fail("The browser rejected an audio segment."));
            buffer.addEventListener("updateend", () => {
                if (!active || selected !== state) return;
                for (let i = 0; i < buffer.buffered.length; i++) {
                    mergeRange(buffer.buffered.start(i), buffer.buffered.end(i));
                }
            });
        }
        return buffer;
    };
    SourceBuffer.prototype.appendBuffer = function(data) {
        // Keep the original call/return semantics. No request URLs or cookies cross the bridge.
        const result = append.call(this, data);
        if (!active || finishing) return result;
        const state = buffers.get(this);
        if (!state) return result;
        if (document.querySelector(".ad-showing, .ad-interrupting")) {
            fail("An ad interrupted capture. Retry after it finishes.");
            return result;
        }
        if (selected && selected !== state) {
            fail("The audio track changed during capture. Retry the export.");
            return result;
        }
        const bytes = (ArrayBuffer.isView(data)
            ? new Uint8Array(data.buffer, data.byteOffset, data.byteLength)
            : new Uint8Array(data)).slice();
        if (selected === state) enqueue(bytes);
        else {
            state.pending.push(bytes);
            state.bytes += bytes.length;
            pendingBytes += bytes.length;
            if (pendingBytes > MAX_QUEUE) fail("Could not identify the audio stream.");
        }
        return result;
    };
    const changeType = SourceBuffer.prototype.changeType;
    if (changeType) SourceBuffer.prototype.changeType = function(mime) {
        if (active && buffers.has(this)) fail("Audio format changed during capture.");
        return changeType.call(this, mime);
    };
    const timer = setInterval(() => {
        if (!active) return;
        try {
            if (new URL(location.href).searchParams.get("v") !== config.videoId) {
                fail("The video changed. Start a new export."); return;
            }
            if (Date.now() - lastProgress > 90000) {
                fail("Audio download stalled. Check playback and retry."); return;
            }
            if (awaiting !== null && Date.now() - lastAck > 15000) {
                fail("Audio transfer timed out."); return;
            }
            const info = identity();
            if (info.live) { fail("Live streams cannot be exported as complete files."); return; }
            if (info.id !== config.videoId) return;
            video = document.querySelector("video");
            if (!video) return;
            if (video.mediaKeys) { fail("Encrypted media cannot be exported."); return; }
            if (!original) original = {muted: video.muted, rate: video.playbackRate};
            video.muted = true;
            if (video.paused && !finishing) video.play().catch(() => {});
            if (Number.isFinite(video.duration) && video.duration > 0) duration = video.duration;
            if (!duration || !Number.isFinite(info.seconds) || info.seconds <= 0 ||
                Math.abs(duration - info.seconds) > 3) return;
            if (duration > 10800) { fail("Export supports videos up to three hours."); return; }
            if (!selected) {
                selected = candidates.find(s => s.bytes > 0);
                if (!selected) return;
                send("begin", {mime: selected.mime, duration});
                for (const bytes of selected.pending) enqueue(bytes);
                selected.pending = [];
                pendingBytes = 0;
                if (!active) return;
                for (let i = 0; i < selected.buffer.buffered.length; i++) {
                    mergeRange(selected.buffer.buffered.start(i), selected.buffer.buffered.end(i));
                }
            }
            if (Date.now() - lastReport > 500) {
                send("progress", {covered, duration});
                lastReport = Date.now();
            }
            if (covered >= duration - 0.15 && !selected.buffer.updating) {
                finishing = true;
                video.pause();
                finishIfDrained();
                return;
            }
            // Seek within downloaded contiguous audio; never jump over a missing range.
            // This asks the site's own player to refill, using its valid SABR session.
            if (covered > 2 && queued < MAX_QUEUE / 2 && Date.now() - lastSeek > 800) {
                const target = Math.min(covered - 1.5, duration - 0.2);
                if (target > video.currentTime + 1 || video.currentTime > covered) {
                    video.currentTime = target;
                    lastSeek = Date.now();
                }
            }
        } catch (_) { fail("The browser audio pipeline became unavailable."); }
    }, 250);
    send("ready");
})();
