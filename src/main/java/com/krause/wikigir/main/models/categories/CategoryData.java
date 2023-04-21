package com.krause.wikigir.main.models.categories;

import com.krause.wikigir.main.models.general.Coordinates;
import com.krause.wikigir.main.models.general.WikiEntity;

import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A utility class which holds data for a single Wikipedia category. It contains statistics about the category (total
 * number of articles, mean coordinates, average dispersion from the mean coordinates and total number of articles.
 * It also contains "article-specific" statistics: since the category's data is determined by its contained articles,
 * this information cannot rely on a tested article's base truth:
 * <br>
 * Suppose we want to assess the coordinates for some article a*. If this article has base truth coordinates, we cannot
 * rely on them in any way. But to compute the average coordinates, dispersion and coordinates ratios, we take a*'s
 * data into account. Therefore, we compute all of these statistics -without- a single article's data, for all articles
 * contained in the category, and store this information in easily accessible maps.
 */
public class CategoryData
{
    // Contains the average category coordinates without the key article title.
    private Map<String, Coordinates> avgCoordinates;

    // Contains the category dispersions without the key article title.
    private Map<String, Double> dispersions;

    // Dispersion from average, calculated across all articles without omissions.
    private Double allArticlesDispersion;

    // Used when the article requesting the category's average coordinates is not part of the category.
    private Coordinates avgCoordinatesAllArticles;

    private int totalArticles;
    private int articlesWithCoordinates;

    /**
     * Constructor 1 - used when creating the categories in the categories creator.
     * @param totalArticles         total number of articles in the category.
     * @param titlesWithCoordinates number of articles(titles) with coordinates.
     */
    public CategoryData(int totalArticles, Map<String, Coordinates> titlesWithCoordinates)
    {
        this.totalArticles = totalArticles;
        this.articlesWithCoordinates = titlesWithCoordinates.size();
        calculateAveragesAndDispersions(titlesWithCoordinates);
    }

    /**
     * Constructor 2 - used when deserializing stored information from disk.
     * @param totalArticles             total number of articles in the category.
     * @param avgCoordinates            average category coordinates for each individual article, when omitting it.
     * @param dispersions               dispersion values for each individual article, when omitting it.
     * @param allArticlesDisp           category's dispersion (from average), across all articles.
     * @param avgCoordinatesAllArticles average category coordinates, without omissions.
     */
    public CategoryData(int totalArticles, int articlesWithCoordinates, Map<String, Coordinates> avgCoordinates,
                        Map<String, Double> dispersions, Double allArticlesDisp, Coordinates avgCoordinatesAllArticles)
    {
        this.totalArticles = totalArticles;
        this.articlesWithCoordinates = articlesWithCoordinates;
        this.avgCoordinates = avgCoordinates;
        this.dispersions = dispersions;
        this.allArticlesDispersion = allArticlesDisp;
        this.avgCoordinatesAllArticles = avgCoordinatesAllArticles;
    }

    /**
     * Returns the total number of articles with coordinates, in relation to a "requesting entity" (an article whose
     * coordinates we might try to assess). If the requesting entity is one of the category's articles, and it has
     * coordinates, we cannot rely on them, and reduce the number of articles with coordinates in the category by 1.
     *
     * @param requestingEntity  the requesting entity (article).
     * @return                  the adjusted total number of articles with coordinates in the category.
     */
    public int getTotalArticlesWithCoordinates(WikiEntity requestingEntity)
    {
        return this.avgCoordinates.get(requestingEntity == null ? null : requestingEntity.getTitle()) == null ?
               this.avgCoordinates.size() : this.avgCoordinates.size() - 1;
    }

    /**
     * Returns the category's dispersion, in relation to a "requesting entity" (an article whose coordinates we might
     * try to assess). If the requesting entity is one of the category's articles, and it has coordinates, we cannot
     * rely on them, and compute the dispersion without that article's coordinates.
     *
     * @param requestingEntity  the requesting entity (article).
     * @return                  the adjusted dispersion.
     */
    public Double getDispersion(WikiEntity requestingEntity)
    {
        return this.dispersions.getOrDefault(requestingEntity.getTitle(), this.allArticlesDispersion);
    }

    /**
     * Returns the (modified) ratio of articles with coordinates from the total number of articles in the category.
     * As with other metrics, this is done in relation to a "requesting entity" (an article whose coordinates we might
     * try to assess). If the requesting entity is part of the category's articles and it has coordinates, it is ignored.
     *
     * @param requestingEntity  the requesting entity (article).
     * @return                  the modified ratio.
     */
    public double getCoordinatesArticlesAdjustedRatio(WikiEntity requestingEntity)
    {
        int numerator = getTotalArticlesWithCoordinates(requestingEntity);

        String title = requestingEntity == null ? null : requestingEntity.getTitle();
        int totalArticles = this.avgCoordinates.get(title) == null ? this.totalArticles : this.totalArticles - 1;

        // +1 to the denominator (so the ratio isn't exactly #-with-coordinates/#-articles) in order to counter the
        // bias towards categories with just one coordinates (after removing the requesting entity, or if the requesting
        // entity isn't in the category). This case causes both a ratio of 1 and a dispersion of 0 and skews the results.
        return totalArticles == 0 ? 0 : (double)numerator / (totalArticles + 1);
    }

    /**
     * Returns the total number of articles in the category.
     * @return the total number of articles in the category.
     */
    public int totalArticles()
    {
        return this.totalArticles;
    }

    /**
     * Returns the number of articles in the category which were manually tagged with coordinates by Wikipedia editors.
     * @return the number of articles in the category which were manually tagged with coordinates by Wikipedia editors.
     */
    public int articlesWithCoordinates()
    {
        return this.articlesWithCoordinates;
    }

    /**
     * Returns the average category's coordinates, across all of its articles (with coordinates).
     * @return the average category's coordinates, across all of its articles (with coordinates).
     */
    public Coordinates avgCoordinatesAllArticles()
    {
        return this.avgCoordinatesAllArticles;
    }

    /**
     * Returns the average coordinates map, where each entry's key is an article in the category which has coordinates,
     * and the value is the average coordinates of all tagged articles in the category <i>except</i> that one article.
     *
     * @return the average coordinates map.
     */
    public Map<String, Coordinates> avgCoordinates()
    {
        return this.avgCoordinates;
    }

    private void calculateAveragesAndDispersions(Map<String, Coordinates> titlesWithCoordinates)
    {
        this.avgCoordinatesAllArticles = calculateAverage(null, titlesWithCoordinates);
        this.allArticlesDispersion = calculateDispersion(null, titlesWithCoordinates, this.avgCoordinatesAllArticles);
        this.avgCoordinates = new HashMap<>();
        this.dispersions = new HashMap<>();

        for(Map.Entry<String, Coordinates> e : titlesWithCoordinates.entrySet())
        {
            Coordinates avgCoords = calculateAverage(e.getKey(), titlesWithCoordinates);
            Double dispersion = calculateDispersion(e.getKey(), titlesWithCoordinates, avgCoords);
            this.avgCoordinates.put(e.getKey(), avgCoords);
            this.dispersions.put(e.getKey(), dispersion);
        }
    }

    private Coordinates calculateAverage(String without, Map<String, Coordinates> titlesWithCoordinates)
    {
        int count = 0;
        double lat = 0;
        double lon = 0;

        for (Map.Entry<String, Coordinates> e : titlesWithCoordinates.entrySet())
        {
            if (e.getKey().equals(without))
            {
                continue;
            }

            count++;
            lat += e.getValue().getLatitude();
            lon += e.getValue().getLongitude();
        }

        return count > 0 ? new Coordinates(lat / count, lon / count) : null;
    }

    private Double calculateDispersion(String without, Map<String, Coordinates> titlesWithCoordinates,
                                       Coordinates avgCoords)
    {
        int counter = 0;
        double distance = 0;
        for(Map.Entry<String, Coordinates> e : titlesWithCoordinates.entrySet())
        {
            if(e.getKey().equals(without))
            {
                continue;
            }

            counter++;
            distance += Coordinates.dist(e.getValue(), avgCoords);
        }

        return counter > 0 ? distance / counter : null;
    }

    public static void serialize(DataOutputStream out, CategoryData category) throws IOException
    {
        out.writeInt(category.totalArticles);
        out.writeInt(category.articlesWithCoordinates);
        out.writeInt(category.avgCoordinates.size());

        for(Map.Entry<String, Coordinates> e : category.avgCoordinates.entrySet())
        {
            out.writeUTF(e.getKey());
            out.writeBoolean(e.getValue() != null);
            if(e.getValue() != null)
            {
                Coordinates.serialize(out, e.getValue());
            }
            Double dispersion = category.dispersions.get(e.getKey());
            out.writeBoolean(dispersion != null);
            if(dispersion != null)
            {
                out.writeDouble(dispersion);
            }
        }

        out.writeBoolean(category.avgCoordinatesAllArticles != null);
        if(category.avgCoordinatesAllArticles != null)
        {
            Coordinates.serialize(out, category.avgCoordinatesAllArticles);
        }

        out.writeBoolean(category.allArticlesDispersion != null);
        if(category.allArticlesDispersion != null)
        {
            out.writeDouble(category.allArticlesDispersion);
        }
    }

    public static CategoryData deserialize(DataInputStream in) throws IOException
    {
        int totalArticles = in.readInt();
        int articlesWithCoordinates = in.readInt();
        int articlesMapSize = in.readInt();
        Map<String, Coordinates> avgCoordinates = new HashMap<>();
        Map<String, Double> dispersions = new HashMap<>();

        for(int i = 0; i < articlesMapSize; i++)
        {
            String t = in.readUTF();
            Coordinates coordinates = in.readBoolean() ? Coordinates.deserialize(in) : null;
            Double dispersion = in.readBoolean() ? in.readDouble() : null;
            avgCoordinates.put(t, coordinates);
            dispersions.put(t, dispersion);
        }

        Coordinates avgCoordinatesAllArticles = in.readBoolean() ? Coordinates.deserialize(in) : null;

        Double allArticlesDisp = in.readBoolean() ? in.readDouble() : null;

        return new CategoryData(totalArticles, articlesWithCoordinates, avgCoordinates,
                                dispersions, allArticlesDisp, avgCoordinatesAllArticles);
    }
}