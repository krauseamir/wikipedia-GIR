package com.krause.wikigir.models.articles;

import java.util.Arrays;

/**
 * Calculates the similarity score between two articles. Used in the nearest neighbors generation process.
 */
public class ArticlesSimilarityCalculator
{
    /**
     * The similarity score of two articles is defined as the weighted average of:
     * a * cosine-similarity + b * named-locations-similarity + c * categories-similarity
     * where:
     * <ul>
     *     <li>a + b + c = 1</li>
     *     <li>cosine-similarity is defined as the cosine between the tf-idf representation of the articles' texts
     *         (this representation should be normalized to 1).</li>
     *     <li>named-locations-similarity is defined as the cosine between the (score-normalized) vectors of the
     *         named locations in each article, where the scores are computed based on the square root of the relative
     *         counts of each named location in the article, limited to a predefined maximal number of locations.</li>
     *     <li>categories-similarity is the Jaccard similarity between the articles' Wikipedia-assigned categories.</li>
     * </ul>
     * @param a1            the first article.
     * @param a2            the second article.
     * @param tfIdfWeight   the cosine-similarity weight (a).
     * @param nlsWeight     the named-locations-similarity weight (b).
     * @param catsWeight    the categories-similarity weight (c).
     * @return              the final averaged score.
     */
    public static double calculate(Article a1, Article a2, double tfIdfWeight, double nlsWeight, double catsWeight)
    {
        if(tfIdfWeight + catsWeight + nlsWeight != 1)
        {
            throw new RuntimeException("Similarity score weights must sum to 1");
        }

        double wordsScore = cosine(a1.getWordsWordants().getIds(), a1.getWordsWordants().getScores(),
                                   a2.getWordsWordants().getIds(), a2.getWordsWordants().getScores());

        float[] scores1 = new float[a1.getCategoryIds().length];
        Arrays.fill(scores1, 1);

        float[] scores2 = new float[a2.getCategoryIds().length];
        Arrays.fill(scores2, 1);

        double intersect = cosine(a1.getCategoryIds(), scores1, a2.getCategoryIds(), scores2);

        // Jaccard similarity index.
        double catsScore = intersect / (scores1.length + scores2.length - intersect);

        double nlsScore = cosine(a1.getNamedLocationWordants().getIds(), a1.getNamedLocationWordants().getScores(),
                                 a2.getNamedLocationWordants().getIds(), a2.getNamedLocationWordants().getScores());

        return ((tfIdfWeight * wordsScore) + (catsWeight * catsScore) + (nlWeight * nlsScore)) /
                (tfIdfWeight + catsWeight + nlsWeight);
    }

    /**
     * Calculates the cosine similarity, given two vectors of the form (id_i, score_i).
     * @param ids1      ids of the first vector.
     * @param scores1   scores (for the ids) of the first vector.
     * @param ids2      ids of the second vector.
     * @param scores2   scores (for the ids) of the second vector.
     * @return          the score.
     */
    public static double cosine(int[] ids1, float[] scores1, int[] ids2, float[] scores2)
    {
        double result = 0;

        int index1 = 0, index2 = 0;

        while(index1 < ids1.length && index2 < ids2.length)
        {
            if(ids1[index1] < ids2[index2])
            {
                index1++;
            }
            else if(ids1[index1] > ids2[index2])
            {
                index2++;
            }
            else
            {
                result += scores1[index1] * scores2[index2];
                index1++;
                index2++;
            }
        }

        return result;
    }
}