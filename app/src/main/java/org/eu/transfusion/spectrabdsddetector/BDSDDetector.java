package org.eu.transfusion.spectrabdsddetector;

import android.os.AsyncTask;
import android.util.Log;

import java.util.Arrays;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.util.fft.FFT;

/**
 * Class that calculates A from AudioEvents using Tarsos
 */
public class BDSDDetector {

    private static final int SAMPLE_RATE = 22050;
    private static final int BUFFER_SIZE = 1024 * 4;
    private static final int FFT_SIZE = BUFFER_SIZE / 2;

    private static final int OVERLAP = 768 * 4;

    private static class ProgressUpdateInfo {
        public final float A;

        ProgressUpdateInfo(float A){
            this.A = A;
        }


    }

    /**
     * Accepts no arguments, progress updates should be A, no return result (Void)
     */
    public static class BDSDDetectorAsyncTask extends AsyncTask<Void, ProgressUpdateInfo, Void> {

        private final OnBDSDDetectorListener listener;
        private AudioDispatcher audioDispatcher;

        public BDSDDetectorAsyncTask(OnBDSDDetectorListener listener) {
            this.listener = listener;
        }
        
        @Override
        protected void onPreExecute() {

        }

        private float calculateA(double[] bins, float[] rawAmplitudes) {
            float[] normalizedAmplitudes = new float[rawAmplitudes.length];
            // https://reference.wolfram.com/language/ref/Rescale.html
            float min = normalizedAmplitudes[0];
            float max = normalizedAmplitudes[0];

            for (float f : rawAmplitudes) {
                if (min > f) {
                    min = f;
                }
                if (max < f) {
                    max = f;
                }
            }

            if (max - min < 10){
                return -1;
            }

            float denominator = 0;
            for (int i = 0; i < rawAmplitudes.length; i++){
                normalizedAmplitudes[i] = (rawAmplitudes[i] - min) / (max - min);
                denominator += normalizedAmplitudes[i];
            }

            float numerator = 0;
            for (int i = 0; i < bins.length; i++) {
                numerator += bins[i] * normalizedAmplitudes[i];
            }

            return numerator / denominator;
        }

        @Override
        protected Void doInBackground(Void... voids) {

            audioDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLE_RATE,
                    BUFFER_SIZE, 0);

            audioDispatcher.addAudioProcessor(new AudioProcessor() {

                FFT fft = new FFT(BUFFER_SIZE);
                final float[] amplitudes = new float[FFT_SIZE];
                final double[] binInHz = new double[FFT_SIZE];

                // instance initializer
                {
                    for (int i = 0; i < FFT_SIZE; i++) {
                        binInHz[i] = fft.binToHz(i, SAMPLE_RATE);
                    }

                    String s = Arrays.toString(binInHz);
                    final int chunkSize = 2048;
                    for (int i = 0; i < s.length(); i += chunkSize) {
                        Log.d("bins: ", s.substring(i, Math.min(s.length(), i + chunkSize)));
                    }
                    Log.d("bins length: ", String.valueOf(binInHz.length));
                }

                @Override
                public boolean process(AudioEvent audioEvent) {

                    if (isCancelled()) {
                        stopAudioDispatcher();
                        return true;
                    }

//                    Arrays.fill(amplitudes, 0);

                    float[] audioBuffer = audioEvent.getFloatBuffer();
                    fft.forwardTransform(audioBuffer);
                    fft.modulus(audioBuffer, amplitudes);

                    /*for (int i = 0; i < amplitudes.length; i++) {
                        Log.d(getClass().getName(), String.format("Amplitude at %3d Hz: %8.3f", (int) fft.binToHz(i, SAMPLE_RATE) , amplitudes[i]));
                    }*/

                    float A = calculateA(binInHz, amplitudes);
                    publishProgress(new ProgressUpdateInfo(A));
//                    Log.d(getClass().getName(), String.format("A: %8.3f", calculateA(binInHz, amplitudes)));

                    return true;
                }

                @Override
                public void processingFinished() {

                }
            });

            audioDispatcher.run();
            return null;
        }


        @Override
        protected void onProgressUpdate(ProgressUpdateInfo... updateInfos) {
            ProgressUpdateInfo updateInfo = updateInfos[0];
            // void onPitchDetectionResult(float pitch, float probability, boolean isPitched);
            this.listener.onBDSDDetectionResult(updateInfo.A);

        }

        @Override
        protected void onCancelled(Void result) {
            stopAudioDispatcher();
        }

        private void stopAudioDispatcher() {
            if (audioDispatcher != null && !audioDispatcher.isStopped()) {
                audioDispatcher.stop();
//                IS_RECORDING = false;
            }
        }


    }
}
