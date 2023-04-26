package com.krause.wikigir.main.surrogates.weightFunctions;

import com.krause.wikigir.main.models.articles.Article;
import com.krause.wikigir.main.models.utils.GetFromConfig;
import com.krause.wikigir.main.models.utils.Pair;

import java.util.List;

/**
 *
 */
public class NamedLocationsWeightAssigner extends WeightAssigner
{
    // The weight boost to named locations that where detected as "x" in a "is a ____ in (x)" (or equivalent) structure.
    private static final double IS_A_IN_MODIFIER =
            GetFromConfig.doubleValue("wikigir.weights.named_locations.is_a_in_modifier");

    // The weight boost to named locations that where detected as "x" in a "located at (x)" (or equivalent) structure.
    private static final double LOCATED_AT_MODIFIER =
            GetFromConfig.doubleValue("wikigir.weights.named_locations.located_at_modifier");

    // The minimal -detected- location priority of the named location's article type to receive the is_a_in / located_at
    // score modifications. This is done so we will not boost scores for entities which cover a large geographical area.
    private static final int MIN_LOCATION_PRIORITY_FOR_BOOSTS =
            GetFromConfig.intValue("wikigir.weights.named_locations.min_location_priority_for_boosts");

    /**
     * Constructor.
     * @param article           the article for which we calculate named locations entities' scores.
     * @param namedLocations    the article's named locations.
     */
    public NamedLocationsWeightAssigner(Article article, List<Pair<Article, Integer>> namedLocations)
    {
        super(article);

        int totalCount = namedLocations.stream().mapToInt(Pair::getV2).sum();

        for(Pair<Article, Integer> namedLocation : namedLocations)
        {

        }
    }

    private double applyIsAInModifier(Article namedLocation, double weight)
    {
        if(namedLocation.getArticleType().getLocationPriority() < MIN_LOCATION_PRIORITY_FOR_BOOSTS)
        {
            return weight;
        }

        return 0;
    }

    private double applyLocatedAtModifier(Article namedLocation, double weight)
    {
        if(namedLocation.getArticleType().getLocationPriority() < MIN_LOCATION_PRIORITY_FOR_BOOSTS)
        {
            return weight;
        }

        return 0;
    }
}