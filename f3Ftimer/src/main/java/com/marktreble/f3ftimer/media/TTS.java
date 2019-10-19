/*
 *     ___________ ______   _______
 *    / ____/__  // ____/  /_  __(_)___ ___  ___  _____
 *   / /_    /_ </ /_       / / / / __ `__ \/ _ \/ ___/
 *  / __/  ___/ / __/      / / / / / / / / /  __/ /
 * /_/    /____/_/        /_/ /_/_/ /_/ /_/\___/_/
 *
 * Open Source F3F timer UI and scores database
 *
 */

package com.marktreble.f3ftimer.media;

import android.annotation.TargetApi;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

public class TTS implements TextToSpeech.OnInitListener {

    private static final String TAG = "TTS";
    private static TTS sharedTTS;
    private TextToSpeech mttsengine;
    private onInitListenerProxy mInitListener;
    public int mTTSStatus;


    public interface onInitListenerProxy {
        void onInit(int status);

        void onStart(String utteranceId);

        void onDone(String utteranceId);

        void onError(String utteranceId);
    }

    /**
     * Constructor
     *
     * @param context Context
     */
    private TTS(Context context) {
        Log.i(TAG, "STARTING TTS ENGINE");
        mttsengine = new TextToSpeech(context, this);
    }


    /**
     * TTS Engine Singleton creator
     *
     * @param context Context
     * @param listener TTS.onInitListenerProxy
     * @return TTS
     */
    public static TTS sharedTTS(Context context, TTS.onInitListenerProxy listener) {
        if (sharedTTS == null) { //if there is no instance available... create new one
            sharedTTS = new TTS(context);
        }

        if (listener != null)
            sharedTTS.setListener(listener);

        return sharedTTS;
    }

    /**
     * Set listener callbacks
     *
     * @param listener TTS.onInitListenerProxy
     */
    public void setListener(TTS.onInitListenerProxy listener) {
        mInitListener = listener;
    }

    /**
     * Getter for the TTS Engine
     *
     * @return TextToSpeech
     */
    public TextToSpeech ttsengine() {
        return mttsengine;
    }

    /**
     * Clean Up
     */
    public void release() {
        if (mttsengine != null) {
            mttsengine.shutdown();
            mttsengine = null;
        }
        sharedTTS = null;
    }

    /**
     * Initialise the TTS progress listener
     *
     * @param status Int
     */
    public void onInit(int status) {
        mTTSStatus = status;

        initUtteranceListenerForMinICS();
        mInitListener.onInit(status);
    }

    @TargetApi(15)
    private void initUtteranceListenerForMinICS() {
        mttsengine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                mInitListener.onStart(utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                mInitListener.onDone(utteranceId);
            }

            @Override
            public void onError(String utteranceId) {
                mInitListener.onError(utteranceId);

            }
        });
    }
}
