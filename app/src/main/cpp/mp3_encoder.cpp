#include <jni.h>
#include <lame.h>
#include <cstdint>

static void fail(JNIEnv* env, const char* message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_profynus_music_media_NativeMp3_create(JNIEnv* env, jobject, jint rate, jint channels) {
    if (rate < 8000 || rate > 48000 || (channels != 1 && channels != 2)) {
        fail(env, "Unsupported MP3 input format"); return 0;
    }
    lame_t encoder = lame_init();
    if (!encoder) { fail(env, "Could not allocate MP3 encoder"); return 0; }
    lame_set_in_samplerate(encoder, rate);
    lame_set_out_samplerate(encoder, rate);
    lame_set_num_channels(encoder, channels);
    lame_set_mode(encoder, channels == 1 ? MONO : JOINT_STEREO);
    lame_set_brate(encoder, channels == 1 ? 128 : 192);
    lame_set_quality(encoder, 5);
    lame_set_VBR(encoder, vbr_off);
    lame_set_bWriteVbrTag(encoder, 0);
    lame_set_write_id3tag_automatic(encoder, 0);
    if (lame_init_params(encoder) < 0) {
        lame_close(encoder); fail(env, "Could not initialize MP3 encoder"); return 0;
    }
    return reinterpret_cast<jlong>(encoder);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_profynus_music_media_NativeMp3_encode(JNIEnv* env, jobject, jlong handle,
        jobject pcm, jint offset, jint frames, jbyteArray output) {
    auto encoder = reinterpret_cast<lame_t>(handle);
    auto address = static_cast<uint8_t*>(env->GetDirectBufferAddress(pcm));
    const auto capacity = env->GetDirectBufferCapacity(pcm);
    if (!encoder || !address || frames <= 0 || frames > 8192 || offset < 0 || offset % 2 ||
        static_cast<int64_t>(offset) + frames * lame_get_num_channels(encoder) * 2 > capacity ||
        env->GetArrayLength(output) < 32768) {
        fail(env, "Invalid MP3 input buffer"); return -1;
    }
    auto samples = reinterpret_cast<short*>(address + offset);
    unsigned char encoded[32768];
    const int size = lame_get_num_channels(encoder) == 2
        ? lame_encode_buffer_interleaved(encoder, samples, frames, encoded, sizeof(encoded))
        : lame_encode_buffer(encoder, samples, samples, frames, encoded, sizeof(encoded));
    if (size < 0) { fail(env, "MP3 encoding failed"); return size; }
    env->SetByteArrayRegion(output, 0, size, reinterpret_cast<jbyte*>(encoded));
    return size;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_profynus_music_media_NativeMp3_finish(JNIEnv* env, jobject, jlong handle, jbyteArray output) {
    if (!handle || env->GetArrayLength(output) < 32768) {
        fail(env, "Invalid MP3 encoder"); return -1;
    }
    unsigned char encoded[32768];
    const int size = lame_encode_flush(reinterpret_cast<lame_t>(handle), encoded, sizeof(encoded));
    if (size < 0) { fail(env, "MP3 flush failed"); return size; }
    env->SetByteArrayRegion(output, 0, size, reinterpret_cast<jbyte*>(encoded));
    return size;
}

extern "C" JNIEXPORT void JNICALL
Java_com_profynus_music_media_NativeMp3_close(JNIEnv*, jobject, jlong handle) {
    if (handle) lame_close(reinterpret_cast<lame_t>(handle));
}
