package com.krause.wikigir.main.surrogates.weightFunctions;

import com.krause.wikigir.main.models.general.WikiEntity;
import com.krause.wikigir.main.models.articles.Article;

import java.util.HashMap;
import java.util.Map;

/**
 * Superclass for all methods which attempt to assign an importance/relevance weight to an entity (article/category).
 * It is responsible for assigning weights for entities for a given <i>single</i> article's entities.
 */
public abstract class WeightAssigner
{
    // The article for which we calculate entities' relevance scores.
    protected Article article;

    // Contains the entities-to-scores mapping, as computed by the derived class for some given entities.
    protected Map<WikiEntity, Double> scores;

    /**
     * Constructor.
     * @param article the article for which we calculate entities' scores.
     */
    public WeightAssigner(Article article)
    {
        this.article = article;
        this.scores = new HashMap<>();
    }

    /**
     * Receives an entity (article / category) and assigns it an importance/relevance weight, based on some heuristic.
     * @param entity   the entity.
     * @return         its weight.
     */
    public double assign(WikiEntity entity)
    {
        return this.scores.getOrDefault(entity, 0.0);
    }
}