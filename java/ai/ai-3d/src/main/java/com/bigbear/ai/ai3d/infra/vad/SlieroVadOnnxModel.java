package com.bigbear.ai.ai3d.infra.vad;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SlieroVadOnnxModel {
    private final OrtSession session;
    private float[][][] state;
    private float[][] context;
    private int lastSr = 0;
    private int lastBatchSize = 0;
    private static final List<Integer> SAMPLE_RATES = Arrays.asList(8000, 16000);

    public SlieroVadOnnxModel(String modelPath) throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setInterOpNumThreads(1);
        opts.setIntraOpNumThreads(1);
        opts.addCPU(true);
        session = env.createSession(modelPath, opts);
        resetStates();
    }

    void resetStates() {
        resetStates(1);
    }

    void resetStates(int batchSize) {
        state = new float[2][batchSize][128];
        context = new float[0][];
        lastSr = 0;
        lastBatchSize = 0;
    }

    public void close() throws OrtException {
        session.close();
    }

    private float[][] validateInput(float[][] x, int sr) {
        if (x.length == 1) {
            x = new float[][]{x[0]};
        }
        if (x.length > 2) {
            throw new IllegalArgumentException("Incorrect audio data dimension: " + x[0].length);
        }

        if (sr != 16000 && (sr % 16000 == 0)) {
            int step = sr / 16000;
            float[][] reducedX = new float[x.length][];

            for (int i = 0; i < x.length; i++) {
                float[] current = x[i];
                float[] newArr = new float[(current.length + step - 1) / step];

                for (int j = 0, index = 0; j < current.length; j += step, index++) {
                    newArr[index] = current[j];
                }

                reducedX[i] = newArr;
            }

            x = reducedX;
            sr = 16000;
        }

        if (!SAMPLE_RATES.contains(sr)) {
            throw new IllegalArgumentException("Only supports sample rates " + SAMPLE_RATES + " (or multiples of 16000)");
        }

        if (((float) sr) / x[0].length > 31.25) {
            throw new IllegalArgumentException("Input audio is too short");
        }

        return x;
    }

    public float[] call(float[][] x, int sr) throws OrtException {
        x = validateInput(x, sr);

        int batchSize = x.length;
        int numSamples = sr == 16000 ? 512 : 256;
        int contextSize = sr == 16000 ? 64 : 32;

        if (lastSr != 0 && lastSr != sr) {
            resetStates(batchSize);
        } else if (lastBatchSize != 0 && lastBatchSize != batchSize) {
            resetStates(batchSize);
        } else if (lastBatchSize == 0) {
            lastBatchSize = batchSize;
        }

        if (context.length == 0) {
            context = new float[batchSize][contextSize];
        }

        float[][] xWithContext = new float[batchSize][contextSize + numSamples];
        for (int i = 0; i < batchSize; i++) {
            System.arraycopy(context[i], 0, xWithContext[i], 0, contextSize);
            System.arraycopy(x[i], 0, xWithContext[i], contextSize, numSamples);
        }

        OrtEnvironment env = OrtEnvironment.getEnvironment();

        OnnxTensor inputTensor = null;
        OnnxTensor stateTensor = null;
        OnnxTensor srTensor = null;
        OrtSession.Result ortOutputs = null;

        try {
            inputTensor = OnnxTensor.createTensor(env, xWithContext);
            stateTensor = OnnxTensor.createTensor(env, state);
            srTensor = OnnxTensor.createTensor(env, new long[]{sr});

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input", inputTensor);
            inputs.put("sr", srTensor);
            inputs.put("state", stateTensor);

            ortOutputs = session.run(inputs);
            float[][] output = (float[][]) ortOutputs.get(0).getValue();
            state = (float[][][]) ortOutputs.get(1).getValue();

            for (int i = 0; i < batchSize; i++) {
                System.arraycopy(xWithContext[i], xWithContext[i].length - contextSize,
                               context[i], 0, contextSize);
            }

            lastSr = sr;
            lastBatchSize = batchSize;
            return output[0];
        } finally {
            if (inputTensor != null) inputTensor.close();
            if (stateTensor != null) stateTensor.close();
            if (srTensor != null) srTensor.close();
            if (ortOutputs != null) ortOutputs.close();
        }
    }
}
