package com.krause.wikigir.main.surrogates.weightFunctions;

import com.krause.wikigir.main.models.general.NearestNeighbors;
import com.krause.wikigir.main.models.articles.Article;
import com.krause.wikigir.main.models.utils.Pair;

import java.util.List;

/**
 * Assigns weights to nearest neighbor articles entities. This is the simplest weight assigner, since the relevance
 * score of the nearest neighbors, for a given article, was already computed in {@link NearestNeighbors}.
 */
public class NearestNeighborsWeightAssigner extends WeightAssigner
{
    /**
     * Constructor.
     * @param article   the article for which we calculate nearest neighbors entities' scores.
     * @param neighbors the nearest neighbors for the given article.
     */
    public NearestNeighborsWeightAssigner(Article article, List<Pair<Article, Float>> neighbors)
    {
        super(article);
        neighbors.forEach(n -> NearestNeighborsWeightAssigner.super.scores.put(n.v1, n.v2.doubleValue()));
    }
}