package com.krause.wikigir.main.models.general;

import com.krause.wikigir.main.models.utils.BlockingThreadFixedExecutor;
import com.krause.wikigir.main.models.utils.CustomSerializable;

import java.util.HashMap;
import java.util.Map;
import java.io.*;

public abstract class ScoresVectorCreator
{
    protected String filePath;
    protected int maxVectorElements;
    protected Map<String, ScoresVector> vectorsMap;
    protected BlockingThreadFixedExecutor executor;

    public ScoresVectorCreator()
    {
        this.vectorsMap = new HashMap<>();
        this.executor = new BlockingThreadFixedExecutor();
    }

    // Make the L2 norm of the scores vector normalized (length = 1).
    protected float[] normalize(float[] wordScores)
    {
        double norm = 0;
        for(float score : wordScores)
        {
            norm += Math.pow(score, 2);
        }

        norm = (float)Math.sqrt(norm);

        for(int i = 0; i < wordScores.length; i++)
        {
            wordScores[i] = (float)(wordScores[i] / norm);
        }

        return wordScores;
    }

    public class Serializer implements CustomSerializable
    {
        @Override
        public String filePath()
        {
            return ScoresVectorCreator.this.filePath;
        }

        @Override
        public void customSerialize(DataOutputStream out) throws IOException
        {
            out.writeInt(ScoresVectorCreator.this.vectorsMap.size());
            for(Map.Entry<String, ScoresVector> e : ScoresVectorCreator.this.vectorsMap.entrySet())
            {
                out.writeUTF(e.getKey());

                out.writeInt(e.getValue().getIds().length);
                for (int id : e.getValue().getIds())
                {
                    out.writeInt(id);
                }

                out.writeInt(e.getValue().getScores().length);
                for (float score : e.getValue().getScores())
                {
                    out.writeFloat(score);
                }
            }
        }

        @Override
        public void customDeserialize(DataInputStream in) throws IOException
        {
            int size = in.readInt();
            for(int i = 0; i < size; i++)
            {
                String title = in.readUTF();

                int[] wordIds = new int[in.readInt()];
                for(int j = 0; j < wordIds.length; j++)
                {
                    wordIds[j] = in.readInt();
                }

                float[] wordScores = new float[in.readInt()];
                for(int j = 0; j < wordScores.length; j++)
                {
                    wordScores[j] = in.readFloat();
                }

                ScoresVectorCreator.this.vectorsMap.put(title, new ScoresVector(wordIds, wordScores));
            }
        }
    }
}