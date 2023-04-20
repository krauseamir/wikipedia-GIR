package com.krause.wikigir.models.general;

import com.krause.wikigir.models.utils.JsonCreator;
import com.krause.wikigir.models.utils.Pair;
import com.krause.wikigir.models.utils.StringsToIdsMapper;

import java.util.*;

/**
 * Stores ids (of words / articles) mapped to their scores, computed in some manner (e.g., word-score pairs generated
 * by the tf-idf scheme for each article).
 */
public class IdsScoresVector
{
    private int[] ids;
    private float[] scores;

    public IdsScoresVector(int[] wordIds, float[] wordScores)
    {
        this.ids = wordIds;
        this.scores = wordScores;
    }

    public int[] getIds()
    {
        return this.ids;
    }

    public float[] getScores()
    {
        return this.scores;
    }

    @Override
    public String toString()
    {
        return toString(null);
    }

    /**
     * Allows to create a more informative representation of the vector, by providing a strings-to-ids mapper
     * translating ids to strings (e.g. words, titles, categories, etc.) The result is sorted by the scores, in
     * descending order.
     *
     * @param mapper a {@link StringsToIdsMapper} object to translate the IDs.
     * @return       a JSON representation of the vector.
     */
    public String toString(StringsToIdsMapper mapper)
    {
        List<Pair<String, Float>> pairings = new ArrayList<>();
        for(int i = 0; i < this.ids.length; i++)
        {
            pairings.add(new Pair<>(mapper == null ? "" + this.ids[i] : mapper.getString(this.ids[i]), this.scores[i]));
        }

        pairings.sort(Comparator.comparingDouble(Pair::getV2));
        Collections.reverse(pairings);

        return JsonCreator.create(pairings);
    }
}