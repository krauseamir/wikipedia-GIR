package com.krause.wikigir.main.models.articles;

import com.krause.wikigir.main.models.articles.articleType.ArticleType;
import com.krause.wikigir.main.models.utils.StringsIdsMapper;
import com.krause.wikigir.main.models.utils.ExceptionWrapper;
import com.krause.wikigir.main.models.general.ScoresVector;
import com.krause.wikigir.main.models.general.Coordinates;
import com.krause.wikigir.main.models.general.WikiEntity;
import com.krause.wikigir.main.models.utils.Pair;
import com.krause.wikigir.main.Constants;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.*;

/**
 * Represents a single article with all needed data (extracted and created from multiple sources in
 * {@link com.krause.wikigir.main.models.articles.dataCreation.ArticlesFactory}).
 */
public class Article extends WikiEntity
{
    private static final int MAX_NAMED_LOCATIONS_PER_ARTICLE;
    static
    {
        Properties p = new Properties();
        ExceptionWrapper.wrap(() -> p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE))));
        MAX_NAMED_LOCATIONS_PER_ARTICLE = Integer.parseInt(p.getProperty("wikigir.articles.max_named_locations_per_article"));
    }

    // If the article has been manually tagged at the title level with coordinates (on Earth...) - store them here.
    private Coordinates coordinates;

    // Wikipedia entities (article title IDs), whose articles had coordinates, and the number of times they or any of
    // their variants (as defined by wikipedia in the [[...|...]] notation) were detected. The order of appearance in
    // the list is the order of the entities appearance in the article with location, it is stored as well.
    private List<Pair<Integer, Integer>> namedLocations;

    // The top-k words (by their tf-idf values) in the article.
    private ScoresVector wordsScoresVector;

    // The top-k named locations when sorted by their counts-then-ordinal in the article and their scores, without
    // any additional modulations (such as is-a-in, located-at or country modifiers). Simply root(count/total).
    private ScoresVector namedLocationScoresVector;

    // The assigned location type for the article (country, settlement, landmark, region, etc.) as heuristically parsed.
    private ArticleType articleType;

    // If the article's text was detected to contain a special structure such as "located at", "located in",
    // "headquartered at", etc., followed by a detected named location - it is stored here (and used later to
    // modulate named locations scores in the named locations entity set weights).
    private String explicitLocatedAt;

    // If the article's text was detected to contain structure such as "is a ____ in ____", "was a ____ at ____",
    // followed by detected named locations - they are stored here (and used later to modulate named locations scores
    // in the named locations entity set weights).
    private Set<String> isAInLocations;

    // Stores the automatically assigned (when parsed) category IDs of the article's categories.
    private int[] categoryIds;

    // The number of article views the article had in the 12 months of 2019 (can be used to detect popular articles).
    private int[] articleViews;

    /**
     * Constructor.
     * @param title the article's title.
     */
    public Article(String title, ArticleType articleType, Coordinates coordinates, int[] categories,
                   List<Pair<Integer, Integer>> namedLocations, ScoresVector wordsScoresVector,
                   String explicitLocatedAt, List<String> isAInLocations)
    {
        super(title);
        this.articleType = articleType != null ? articleType : ArticleType.NONE;
        this.coordinates = coordinates;
        this.categoryIds = categories != null ? categories : new int[0];
        this.namedLocations = namedLocations;
        this.wordsScoresVector = wordsScoresVector;
        this.explicitLocatedAt = explicitLocatedAt;
        this.isAInLocations = isAInLocations == null ? new HashSet<>() : new HashSet<>(isAInLocations);

        setNamedLocationsScoresVector();
    }

    public String getTitle()
    {
        return this.title;
    }

    /**
     * Returns the article's type.
     * @return the article's type.
     */
    public ArticleType getArticleType()
    {
        return this.articleType;
    }

    public Coordinates getCoordinates(WikiEntity requestingEntity)
    {
        return this.coordinates;
    }

    /**
     * Returns the article's tf-idf scores vector of the top words.
     * @return the article's tf-idf scores vector of the top words.
     */
    public ScoresVector getWordsScoresVector()
    {
        return this.wordsScoresVector;
    }

    /**
     * Returns the named locations stored in the article.
     * @return the named locations stored in the article.
     */
    public List<Pair<Integer, Integer>> getNamedLocations()
    {
        return this.namedLocations;
    }

    public ScoresVector getNamedLocationsScoresVector()
    {
        return this.namedLocationScoresVector;
    }

    /**
     * If there's a named location that was detected as being in a "located at" structure, it is returned here.
     * @return the named location (if found during parsing), or null otherwise.
     */
    public String getExplicitLocatedAt()
    {
        return this.explicitLocatedAt;
    }

    /**
     * Returns the set of locations which are part of a "is a ____ in ____" structure. Empty if there aren't any.
     * @return the set of locations.
     */
    public Set<String> getIsAInLocations()
    {
        return this.isAInLocations;
    }

    /**
     * Returns the article's category IDs.
     * @return the article's category IDs.
     */
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

    // Transforms the named locations of the article into a scores vector, based on the root of their relative frequency
    // of occurrence in the article (as detailed in the paper). Note that no named locations scores modulations (like
    // is-a-in etc.) are activated here.
    private void setNamedLocationsScoresVector()
    {
        List<Pair<Integer, Float>> l = new ArrayList<>();

        int totalOccurrences = this.namedLocations.stream().mapToInt(nl -> nl.v2).sum();

        for(Pair<Integer, Integer> namedLocation : this.namedLocations)
        {
            float score = (float)(Math.sqrt((double)namedLocation.v2 / totalOccurrences));
            l.add(new Pair<>(namedLocation.v1, score));
        }

        if(l.size() > MAX_NAMED_LOCATIONS_PER_ARTICLE)
        {
            l.sort(Comparator.comparingDouble(Pair::getV2));
            Collections.reverse(l);
            l = l.subList(0, MAX_NAMED_LOCATIONS_PER_ARTICLE);
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

        this.namedLocationScoresVector = new ScoresVector(ids, scores);
    }
}