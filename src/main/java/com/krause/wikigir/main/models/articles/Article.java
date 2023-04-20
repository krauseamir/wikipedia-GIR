package com.krause.wikigir.main.models.articles;

import com.krause.wikigir.main.models.general.Coordinates;
import com.krause.wikigir.main.models.general.ScoresVector;
import com.krause.wikigir.main.models.general.WikiEntity;
import com.krause.wikigir.main.models.utils.Pair;

import java.util.*;

/**
 * Represents a single article with all needed data (extracted and created from multiple sources in {@link Articles}).
 * @author Amir Krause
 */
public class Article extends WikiEntity
{
    // If the article has been manually tagged at the title level with coordinates (on Earth...) - store them here.
    private Coordinates coordinates;

    // Wikipedia entities (article titles), whose articles had coordinates, and the number of times they or
    // any of their variants (as defined by wikipedia in the [[...|...]] notation) were detected. The
    // order of appearance in the list is the order of the entities appearance in the article.
    // with location, it is stored as well.
    private LocationsData locationsData;

    // The top-k words (by their tf-idf values) in the article.
    private ScoresVector wordsScoredVector;

    // The top-k named locations when sorted by their counts-then-ordinal in the article and their scores, without
    // any additional modulations (such as is-a-in, located-at or country modifiers). Simply root(count/total).
    private ScoresVector namedLocationScoredVector;

    // The assigned location type for the article (country, settlement, landmark, region, etc.) as heuristically parsed.
    private LocationType locationType;

    // If the article's text was detected to contain a special structure such as "located at", "located in",
    // "headquartered at", etc., followed by a detected named location - it is stored here (and used later to
    // modulate named locations scores in the named locations entity set weights).
    private String explicitLocatedAt;

    // Stores the automatically assigned (when parsed) category IDs of the article's categories.
    private int[] categoryIds;

    // The number of article views the article had in the 12 months of 2019 (can be used to detect popular articles).
    private int[] articleViews;

    /**
     * Constructor.
     * @param title the article's title.
     */
    public Article(String title)
    {
        super(title);
    }

    /**
     * Copy constructor.
     * @param other the article to be copied.
     */
    public Article(Article other)
    {
        super(other.title);
        this.wordsScoredVector = other.wordsScoredVector;
        this.namedLocationScoredVector = other.namedLocationScoredVector;
        this.coordinates = other.coordinates;
        this.categoryIds = other.categoryIds;
        this.locationsData = other.locationsData;
        this.locationType = other.locationType;
        this.explicitLocatedAt = other.explicitLocatedAt;
        this.articleViews = other.articleViews;
    }

    public String getTitle()
    {
        return this.title;
    }

    public void setCoordinates(Coordinates coordinates)
    {
        this.coordinates = coordinates;
    }

    public void setLocations(LocationsData locationsData, StringsToIdsMapping titlesIdsMapping)
    {
        this.locationsData = locationsData;

        List<Pair<Integer, Float>> l = new ArrayList<>();

        int totalOccurrences = locationsData.getLocations().stream().mapToInt(x -> x.v2).sum();

        for(Pair<String, Integer> namedLocation : locationsData.getLocations())
        {
            if(titlesIdsMapping.getID(namedLocation.v1) == null)
            {
                continue;
            }

            float score = (float)(Math.sqrt((double)namedLocation.v2 / totalOccurrences));

            l.add(new Pair<>(titlesIdsMapping.getID(namedLocation.v1), score));
        }

        if(l.size() > 20)
        {
            l.sort(Comparator.comparingDouble(Pair::getV2));
            Collections.reverse(l);
            l = l.subList(0, 20);
        }

        double length = Math.sqrt(l.stream().mapToDouble(Pair::getV2).map(d -> Math.pow(d, 2)).sum());
        l.forEach(p -> p.v2 /= (float)length);

        l.sort(Comparator.comparingInt(Pair::getV1));

        int[] ids = new int[l.size()];
        float[] scores = new float[l.size()];
        for(int i = 0; i < l.size(); i++)
        {
            ids[i] = l.get(i).v1;
            scores[i] = l.get(i).v2;
        }

        this.namedLocationWordants = new Wordants(ids, scores);
    }

    public void setLocationType(LocationType type)
    {
        this.locationType = type;
    }

    public void setExplicitLocatedAt(String location)
    {
        this.explicitLocatedAt = location;
    }

    public void setCategoryIds(int[] categoryIds)
    {
        this.categoryIds = categoryIds;
    }

    public Coordinates getCoordinates(WikiEntity requestingEntity)
    {
        return this.coordinates;
    }

    public LocationsData getLocationsData()
    {
        return this.locationsData;
    }

    public ScoresVector getWordsScoredVector()
    {
        return this.wordsScoredVector;
    }

    public ScoresVector getNamedLocationsScoredVector()
    {
        return this.namedLocationScoredVector;
    }

    public LocationType getLocationType()
    {
        return this.locationType;
    }

    public String getExplicitLocatedAt()
    {
        return this.explicitLocatedAt;
    }

    public int[] getCategoryIds()
    {
        return this.categoryIds;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Article article = (Article) o;
        return title.equals(article.title);
    }

    @Override
    public int hashCode()
    {
        // Need to hash together the type of the entity, since there are mutual titles for categories and articles.
        return Objects.hash(super.title, "article");
    }

    @Override
    public String toString()
    {
        return super.title;
    }
}
