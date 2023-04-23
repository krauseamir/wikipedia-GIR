package com.krause.wikigir.main.models.articles.dataCreation;

import com.krause.wikigir.main.models.namedLocationsParsers.ExplicitLocatedAtCreator;
import com.krause.wikigir.main.models.articles.articleType.ArticlesTypeCreator;
import com.krause.wikigir.main.models.articles.NamedLocationsInArticle;
import com.krause.wikigir.main.models.articles.articleType.ArticleType;
import com.krause.wikigir.main.models.utils.StringsIdsMapper;
import com.krause.wikigir.main.models.general.ScoresVector;
import com.krause.wikigir.main.models.general.Coordinates;
import com.krause.wikigir.main.models.utils.GetFromConfig;
import com.krause.wikigir.main.models.articles.Article;
import com.krause.wikigir.main.models.utils.Pair;

import java.util.Map;
import java.util.*;

/**
 * Creates the entire article-titles-to-articles-data mapping, by extracting different information from the "enwiki.xml"
 * file (using designated classes), such as tokenized page text, coordinates, entities etc., then unifies them into a
 * single mapping with the wrapper {@link Article} object. This class also creates the titles-to-IDs mapping that is
 * used by the inverted index and the categories-to-IDs mapping.
 */
public class ArticlesFactory
{
    private static ArticlesFactory instance;
    public static ArticlesFactory getInstance()
    {
        if(instance == null)
        {
            instance = new ArticlesFactory();
        }

        return instance;
    }

    private final Map<String, Article> articles;
    private final StringsIdsMapper titleIdsMapper;
    private Map<String, String> redirects;
    private Map<String, Coordinates> coordinatesMap;
    private StringsIdsMapper categoriesIdsMapper;

    private boolean created;

    /**
     * Constructor.
     */
    private ArticlesFactory()
    {
        this.titleIdsMapper = new StringsIdsMapper(GetFromConfig.filePath("wikigir.base_path", "wikigir.articles.folder",
                                                                    "wikigir.articles.titles_to_ids_mapping.file_name"));
        this.articles = new HashMap<>();
        this.created = false;
    }

    public Map<String, Article> create()
    {
        System.out.println("Creating articles' top-words scores vector.");
        Map<String, ScoresVector> articlesTexts = new ArticleTopWordsScoresVectorCreator().create();
        this.titleIdsMapper.createFromCollection(articlesTexts.keySet());

        System.out.println("Creating articles to coordinates mapping.");
        this.coordinatesMap = new ArticlesCoordinatesCreator().create();

        System.out.println("Creating articles' redirects map.");
        this.redirects = new ArticlesRedirectsCreator().create();

        System.out.println("Creating articles to contained named locations mapping.");
        Map<String, NamedLocationsInArticle> locations =
                new ArticlesNamedLocationsCreator(this.coordinatesMap, this.redirects).create();

        System.out.println("Creating articles to categories mapping.");
        Pair<Map<String, int[]>, StringsIdsMapper> p = new ArticlesCategoriesCreator().create();
        Map<String, int[]> articlesToCategoryIds = p.v1;
        this.categoriesIdsMapper = p.v2;

        System.out.println("Creating articles to article types mapping.");
        Map<String, ArticleType> articleTypesMap = new ArticlesTypeCreator(p.v1, p.v2).create();

        System.out.println("Detecting articles with explicit located-at mapping.");
        Map<String, String> articlesWithLocatedAt =
                new ExplicitLocatedAtCreator(this.coordinatesMap, articleTypesMap, this.redirects).create();

        for(String title : this.titleIdsMapper.getStrings())
        {
            Article article = new Article(title);

            article.setWordsScoresVector(articlesTexts.get(title));

            // Will be null if there's no coordinates for that Wikipedia page.
            article.setCoordinates(this.coordinatesMap.get(title));

            article.setLocations(locations.get(title), this.titleIdsMapper);

            article.setLocationType(articleTypesMap.get(title));
            if(article.getLocationType() == null)
            {
                article.setLocationType(ArticleType.NONE);
            }

            article.setExplicitLocatedAt(articlesWithLocatedAt.get(title));

            int[] categoryIds = articlesToCategoryIds.get(title);
            categoryIds = categoryIds == null ? new int[0] : categoryIds;
            article.setCategoryIds(categoryIds);

            this.articles.put(title, article);
        }

        this.created = true;

        return this.articles;
    }

    /**
     * Returns the articles mapping (title to {@link Article} objects).
     * @return the articles mapping.
     */
    public Map<String, Article> getArticles()
    {
        return this.articles;
    }

    /**
     * Returns the redirects mapping.
     * @return the redirects mapping.
     */
    public Map<String, String> getRedirects()
    {
        return this.redirects;
    }

    /**
     * Returns the coordinates mapping.
     * @return the coordinates mapping.
     */
    public Map<String, Coordinates> getCoordinatesMapping()
    {
        return this.coordinatesMap;
    }

    /**
     * Returns the mappings and reverse mappings of titles (as strings) to ids.
     * @return the mappings and reverse mappings of titles (as strings) to ids.
     */
    public StringsIdsMapper getTitleToIdsMapper()
    {
        return this.titleIdsMapper;
    }

    /**
     * Returns the mappings and reverse mappings of categories (as strings) to ids.
     * @return the mappings and reverse mappings of categories (as strings) to ids.
     */
    public StringsIdsMapper getCategoriesIdsMapper()
    {
        return this.categoriesIdsMapper;
    }

    /**
     * Returns true if the create() method has been invoked for the singleton.
     * @return true if the create() method has been invoked for the singleton.
     */
    public boolean isCreated()
    {
        return this.created;
    }

    public static void main(String[] args)
    {
        new ArticlesFactory().create();
    }
}