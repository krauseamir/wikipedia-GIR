package com.krause.wikigir.main.models.categories;

import com.krause.wikigir.main.models.general.Coordinates;
import com.krause.wikigir.main.models.general.WikiEntity;
import com.krause.wikigir.main.models.utils.JsonCreator;

import java.util.Objects;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single category, which is one of the two Wikipedia {@link WikiEntity} objects.
 */
public class Category extends WikiEntity
{
    private CategoryData data;

    /**
     * Constructor.
     * @param title the category's title.
     * @param data  the category's data (average coordinates, dispersion, ratio, etc.)
     */
    public Category(String title, CategoryData data)
    {
        super(title);
        this.data = data;
    }

    @Override
    public Coordinates getCoordinates(WikiEntity requestingEntity)
    {
        // In this implementation of getCoordinates, for a category, we must take the "requesting entity" into account.
        // If we just want the average coordinates of the category, this entity can be ignored. However, in the context
        // of predicting coordinates for an article a*, when looking at any of its categories, those categories' data -
        // such as their average coordinates - are computed based (in part) on a*'s coordinates, which we should not use.
        // So, if this is the scenario, the coordinates of the requesting entity - a* - are removed from the calculation.

        if(this.data.avgCoordinatesAllArticles() == null || this.data.avgCoordinates().isEmpty())
        {
            return null;
        }

        return requestingEntity == null ? this.data.avgCoordinatesAllArticles() :
               this.data.avgCoordinates().getOrDefault(requestingEntity.getTitle(),
                                            this.data.avgCoordinatesAllArticles());
    }

    /**
     * Returns the category's dispersion (from average coordinates), in the context of a requesting entity (article).
     * @param requestingEntity  the requesting entity (article).
     * @return                  the dispersion.
     */
    public Double getDispersion(WikiEntity requestingEntity)
    {
        return this.data.getDispersion(requestingEntity);
    }

    /**
     * Returns the number of articles with coordinates, in the context of a requesting entity (article) - e.g., if the
     * requesting entity is an article with coordinates and it is included in this category - the count is reduced.
     *
     * @param requestingEntity  the requesting entity (article).
     * @return                  the number of articles with coordinates.
     */
    public int getTotalArticlesWithCoordinates(WikiEntity requestingEntity)
    {
        return this.data.getTotalArticlesWithCoordinates(requestingEntity);
    }

    /**
     * Returns the articles-with-coordinates / articles ratio for the category, in the context of a requesting entity.
     * @param requestingEntity  the requesting entity.
     * @return                  the ratio.
     */
    public double getCoordinatesArticlesAdjustedRatio(WikiEntity requestingEntity)
    {
        return this.data.getCoordinatesArticlesAdjustedRatio(requestingEntity);
    }

    @Override
    public String toString()
    {
        Map<String, Object> json = new HashMap<String, Object>()
        {
            {
                put("coords", getCoordinates(null));
                put("articles", Category.this.data.totalArticles());
                put("articles_coordinates", Category.this.data.articlesWithCoordinates());
            }
        };

        return JsonCreator.create(json);
    }

    @Override
    public int hashCode()
    {
        // Need to hash together the type of the entity, since there are mutual titles for categories and pages.
        return Objects.hash(super.title, "category");
    }

    @Override
    public boolean equals(Object other)
    {
        if(other == this)
        {
            return true;
        }

        if(!(other instanceof Category))
        {
            return false;
        }

        return this.title.equals(((Category)other).title);
    }
}