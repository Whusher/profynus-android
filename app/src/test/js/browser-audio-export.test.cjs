// Runs on the device's actual JavaScript engine via BrowserAudioScriptTest.
// Also runnable with: node app/src/test/js/browser-audio-export.test.cjs
function runBrowserAudioTests(script) {
    const results = [];
    const assert = {
        equal(a, b) { if (a !== b) throw Error(JSON.stringify(a) + " != " + JSON.stringify(b)); },
        deepEqual(a, b) { this.equal(JSON.stringify(a), JSON.stringify(b)); },
        ok(value) { if (!value) throw Error("Assertion failed"); },
        match(value, regex) { if (!regex.test(value)) throw Error(value + " did not match " + regex); }
    };
    function test(name, action) {
        try { action(); results.push({name, passed: true}); }
        catch (error) { results.push({name, passed: false, error: String(error)}); }
    }
function browser() {
    let tick, time = 1000;
    const messages = [];
    class SourceBuffer {
        constructor() { this.ranges = []; this.events = {}; this.updating = false; }
        get buffered() {
            return {length: this.ranges.length, start: i => this.ranges[i][0], end: i => this.ranges[i][1]};
        }
        addEventListener(type, callback) { this.events[type] = callback; }
        appendBuffer() {}
        changeType() {}
        update(ranges) { this.ranges = ranges; this.events.updateend?.(); }
    }
    class MediaSource { addSourceBuffer() { return new SourceBuffer(); } }
    const video = {
        duration: 100, currentTime: 0, paused: true, muted: false, playbackRate: 1,
        play() { this.paused = false; return Promise.resolve(); },
        pause() { this.paused = true; }
    };
    const details = {videoId: "M7lc1UVf-VE", lengthSeconds: "100", title: "Test"};
    const bridge = {postMessage: raw => messages.push(JSON.parse(raw))};
    const window = {MediaSource, SourceBuffer, ProfynusAudio: bridge, ytInitialPlayerResponse: {videoDetails: details}};
    window.top = window;
    const context = {
        window, MediaSource, SourceBuffer, URL, Uint8Array, ArrayBuffer,
        document: {getElementById: () => null, querySelector: selector => selector === "video" ? video : null},
        location: {href: "https://m.youtube.com/watch?v=M7lc1UVf-VE"},
        Date: {now: () => time},
        setInterval: callback => { tick = callback; return 1; }, clearInterval: () => {},
        btoa
    };
    new Function(...Object.keys(context), script.replace("__EXPORT_CONFIG__", JSON.stringify({token: "session", videoId: details.videoId})))(...Object.values(context));
    return {
        messages, video, details, context,
        source: new MediaSource(),
        tick(ms = 1000) { time += ms; tick(); },
        ack(sequence, token = "session") { bridge.onmessage({data: JSON.stringify({token, sequence})}); }
    };
}

test("captures typed-array slices, ignores video, and waits for disk acknowledgement before finishing", () => {
    const b = browser();
    b.details.author = "Channel";
    b.details.thumbnail = {thumbnails: [
        {url: "https://i.ytimg.com/vi/test/small.jpg", width: 120},
        {url: "https://i.ytimg.com/vi/test/large.jpg", width: 1280}
    ]};
    const video = b.source.addSourceBuffer("video/webm");
    video.appendBuffer(new Uint8Array([99]));
    const audio = b.source.addSourceBuffer('audio/webm; codecs="opus"');
    audio.appendBuffer(new Uint8Array([99, 1, 2, 3, 99]).subarray(1, 4));
    audio.update([[0, 100]]);
    b.tick();
    const chunks = b.messages.filter(m => m.type === "chunk");
    assert.equal(chunks.length, 1);
    assert.deepEqual([...atob(chunks[0].data)].map(c => c.charCodeAt(0)), [1, 2, 3]);
    assert.equal(b.messages.some(m => m.type === "finish"), false);
    b.ack(0, "old-session");
    assert.equal(b.messages.some(m => m.type === "finish"), false);
    b.ack(0);
    assert.equal(b.messages.find(m => m.type === "finish").chunks, 1);
    assert.equal(b.messages.find(m => m.type === "finish").author, "Channel");
    assert.equal(b.messages.find(m => m.type === "finish").thumbnail, "https://i.ytimg.com/vi/test/large.jpg");
    assert.equal(b.video.paused, true);
    assert.equal(b.video.muted, false);
});

test("does not seek across missing audio or export a capture that starts late", () => {
    for (const ranges of [[[0, 20], [30, 100]], [[10, 100]]]) {
        const b = browser();
        const audio = b.source.addSourceBuffer("audio/mp4");
        audio.appendBuffer(new Uint8Array([1]));
        audio.update(ranges);
        b.tick();
        b.ack(0);
        assert.equal(b.messages.some(m => m.type === "finish"), false);
        assert.ok(b.video.currentTime <= 20);
    }
});

test("accumulates contiguous coverage after the player evicts old buffered ranges", () => {
    const b = browser();
    const audio = b.source.addSourceBuffer("audio/mp4");
    audio.appendBuffer(new Uint8Array([1]));
    audio.update([[0, 50]]);
    b.tick();
    b.ack(0);
    audio.appendBuffer(new Uint8Array([2]));
    audio.update([[49.99, 100]]);
    b.tick();
    b.ack(1);
    assert.equal(b.messages.find(m => m.type === "finish").chunks, 2);
});

test("bounds messages and has only one outstanding audio chunk", () => {
    const b = browser();
    const audio = b.source.addSourceBuffer("audio/webm");
    audio.appendBuffer(new Uint8Array(100000));
    b.tick();
    assert.equal(b.messages.filter(m => m.type === "chunk").length, 1);
    b.ack(0);
    assert.equal(b.messages.filter(m => m.type === "chunk").length, 2);
    for (const chunk of b.messages.filter(m => m.type === "chunk")) {
        assert.ok(atob(chunk.data).length <= 48 * 1024);
    }
    b.tick(16000);
    assert.match(b.messages.find(m => m.type === "error").reason, /timed out/);
});

test("cancellation stops transfer and format changes fail explicitly", () => {
    const b = browser();
    const audio = b.source.addSourceBuffer("audio/webm");
    audio.appendBuffer(new Uint8Array([1]));
    b.tick();
    b.context.window.__profynusExport.stop();
    const count = b.messages.length;
    audio.appendBuffer(new Uint8Array([2]));
    b.tick();
    b.ack(0);
    assert.equal(b.messages.length, count);
    const other = browser();
    other.source.addSourceBuffer("audio/webm").changeType("audio/mp4");
    assert.match(other.messages.find(m => m.type === "error").reason, /format changed/);
});

test("missing duration metadata cannot select an audio stream", () => {
    const b = browser();
    delete b.details.lengthSeconds;
    b.source.addSourceBuffer("audio/webm").appendBuffer(new Uint8Array([1]));
    b.tick();
    assert.equal(b.messages.some(m => m.type === "begin"), false);
});

    return results;
}
if (typeof module !== "undefined" && module.exports) {
    const fs = require("node:fs");
    const path = require("node:path");
    const results = runBrowserAudioTests(fs.readFileSync(path.join(__dirname, "../../main/assets/browser-audio-export.js"), "utf8"));
    console.log(results);
    if (results.some(result => !result.passed)) process.exitCode = 1;
}
