package com.krause.wikigir.main.models.articles.articleType;

import com.krause.wikigir.main.Constants;
import com.krause.wikigir.main.models.articles.dataCreation.ArticlesCategoriesCreator;
import com.krause.wikigir.main.models.articles.dataCreation.CleanTextXMLParser;
import com.krause.wikigir.main.models.general.WikiXMLArticlesExtractor;
import com.krause.wikigir.main.models.general.TextTokenizer;
import com.krause.wikigir.main.models.utils.*;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.regex.Pattern;
import java.util.*;
import java.io.*;

/**
 * Creates "article type" information for each article in the enwiki XML file, when possible. The article type
 * (settlement, country, region, person, ship, etc.) is chosen from a fixed set of article types defined in
 * {@link ArticleType} and can be decided either by using wikipedia's own annotations in an infobox, utilizing the
 * article's categories, or by using textual heuristics in {@link LocationTypeFromText}.
 */
public class ArticlesTypeCreator
{
    // 0 = all pages.
    private static final int ARTICLES_LIMIT = 0;

    private static final Pattern BIRTHS_CAT = Pattern.compile("\\d+s?_births");
    private static final Pattern DEATHS_CAT = Pattern.compile("\\d+s?_deaths");
    private static final Pattern PEOPLE = Pattern.compile("People_((from)|(in)|(of))");

    private final String filePath;

    private final Map<String, int[]> pagesToCatIds;
    private final StringsIdsMapper categoriesIdsMapper;

    private final Map<String, ArticleType> articlesTypeMap;

    private final BlockingThreadFixedExecutor executor;

    /**
     * Constructor.
     * @param pagesToCatIds         the mapping from articles to their category IDs (required for heuristics).
     * @param categoriesIdsMapper   the index mapping from category string to their matching IDs.
     */
    public ArticlesTypeCreator(Map<String, int[]> pagesToCatIds, StringsIdsMapper categoriesIdsMapper)
    {
        this.filePath = GetFromConfig.filePath("wikigir.base_path", "wikigir.articles.folder",
                                               "wikigir.articles.articles_type_file_name");

        this.pagesToCatIds = pagesToCatIds;
        this.categoriesIdsMapper = categoriesIdsMapper;

        this.articlesTypeMap = new HashMap<>();

        this.executor = new BlockingThreadFixedExecutor();
    }

    /**
     * Creates and returns the mapping, or loads it from disk if previously created.
     * @return the mapping.
     */
    public Map<String, ArticleType> create()
    {
        if(!new File(this.filePath).exists())
        {
            createFromXml();
            new Serializer().serialize();
        }
        else
        {
            new Serializer().deserialize();
        }

        return this.articlesTypeMap;
    }

    // Iterates through the enwiki xml file and generates the needed information.
    private void createFromXml()
    {
        int[] processed = {0};

        WikiXMLArticlesExtractor.extract(ArticleLocationInfoboxXMLParser::new,
            (parser, text) ->
                this.executor.execute(() ->
                    ExceptionWrapper.wrap(() ->
                    {
                        ProgressBar.mark(processed, Constants.NUMBER_OF_ARTICLES);
                        parser.addTitleToResult(text);
                        parser.parse(text);

                        if(parser.getTitle() == null)
                        {
                            return;
                        }

                        List<String> cats = IntStream.of(this.pagesToCatIds.get(parser.getTitle())).boxed().map(
                                            this.categoriesIdsMapper::getString).collect(Collectors.toList());

                        // Use the categories to detect common structures indicating an article type, such as
                        // "Cities in ...", "Countries of ..." or "1919 ships".
                        ArticleType t = tryUsingCategories(cats);

                        if(t == null)
                        {
                            // This means that using wikipedia's own annotations, the page was deemed to
                            // be of a settlement - there is no need to turn to text based heuristics.
                            t = (ArticleType)parser.getResult().get(ArticleLocationInfoboxXMLParser.ARTICLE_TYPE_KEY);

                            if(t == null)
                            {
                                // Use text-based and categories-based heuristics to detect a location type.
                                t = tryToGetFromText(text, cats);

                                if(t == null)
                                {
                                    // Take no chances with ships (damages results), mark as one if possible.
                                    // Note that we don't search just for "_(ship)" since there are many variants
                                    // to ship types (battleship, steamship, airship, warship, etc.)
                                    if(parser.getTitle().toLowerCase().endsWith("ship)") &&
                                       !parser.getTitle().toLowerCase().endsWith("scholarship)") &&
                                       !parser.getTitle().toLowerCase().endsWith("fellowship)") &&
                                       !parser.getTitle().toLowerCase().endsWith("ownership)") &&
                                       !parser.getTitle().toLowerCase().endsWith("membership)"))
                                    {
                                        t = ArticleType.SHIP;
                                    }
                                }
                            }
                        }

                        synchronized(this.articlesTypeMap)
                        {
                            if(t != null)
                            {
                                this.articlesTypeMap.put(parser.getTitle(), t);
                            }
                        }
                    }, ExceptionWrapper.Action.NOTIFY_LONG)
                ), ARTICLES_LIMIT);

        this.executor.waitForTermination();
    }

    // Iterates through the categories to detect common structures indicative of the type.
    private ArticleType tryUsingCategories(List<String> categories)
    {
        for(ArticleType at : ArticleType.values())
        {
            for(String variant : at.getVariants())
            {
                // Only look at plural locations in categories.
                if(variant.endsWith("s"))
                {
                    for(String cat : categories)
                    {
                        // Remove year markings, which are common in categories.
                        while(cat.startsWith("0") || cat.startsWith("1") || cat.startsWith("2") ||
                              cat.startsWith("3") || cat.startsWith("4") || cat.startsWith("5") ||
                              cat.startsWith("6") || cat.startsWith("7") || cat.startsWith("8") ||
                              cat.startsWith("9") || cat.startsWith("-") || cat.startsWith("_"))
                        // include "-" for ranges, "_" for comma between years and noun.
                        {
                            cat = cat.substring(1);
                        }

                        cat = cat.toLowerCase().trim();

                        if(cat.equals(variant))
                        {
                            return at;
                        }
                        if(cat.startsWith(variant) && (cat.contains("_in_") || cat.contains("_of_")))
                        {
                            return at;
                        }
                    }
                }
            }
        }

        // Try to find people:
        for(String cat : categories)
        {
            if(BIRTHS_CAT.matcher(cat).find() || DEATHS_CAT.matcher(cat).find() || PEOPLE.matcher(cat).find() ||
               cat.contains("Living_people") || cat.endsWith("_alumni"))
            {
                return ArticleType.PERSON;
            }
        }

        return null;
    }

    // Scan the page's text and categories in an attempt to discover location type information,
    // using a specialized object. Note that another parsing is required to get the page's
    // "clean" text information.
    private ArticleType tryToGetFromText(StringBuilder text, List<String> categories) throws Exception
    {
        CleanTextXMLParser textParser = new CleanTextXMLParser();
        textParser.parse(text);

        if(textParser.getResult().get(CleanTextXMLParser.CLEAN_TEXT_KEY) == null)
        {
            return null;
        }

        // Stop words are *not* filtered (necessary in order to detect locations).
        List<String> words = TextTokenizer.tokenize((String)textParser.getResult().get(
                                            CleanTextXMLParser.CLEAN_TEXT_KEY), false);

        return new LocationTypeFromText(words, categories).process();
    }

    private class Serializer implements CustomSerializable
    {
        @Override
        public String filePath()
        {
            return ArticlesTypeCreator.this.filePath;
        }

        @Override
        public void customSerialize(DataOutputStream out) throws IOException
        {
            out.writeInt(ArticlesTypeCreator.this.articlesTypeMap.size());
            for(Map.Entry<String, ArticleType> e : ArticlesTypeCreator.this.articlesTypeMap.entrySet())
            {
                out.writeUTF(e.getKey());
                out.writeUTF(e.getValue().toString());
            }
        }

        @Override
        public void customDeserialize(DataInputStream in) throws IOException
        {
            int locationTypesMapSize = in.readInt();
            for(int i = 0; i < locationTypesMapSize; i++)
            {
                String title = in.readUTF();
                String lt = in.readUTF();
                ArticlesTypeCreator.this.articlesTypeMap.put(title, ArticleType.valueOf(lt));
            }
        }
    }

    public static Map<String, ArticleType> createObject()
    {
        // First, get the pages to categories mapping and the index for int-to-string translations.
        Pair<Map<String, int[]>, StringsIdsMapper> p = new ArticlesCategoriesCreator().create();
        return new ArticlesTypeCreator(p.v1, p.v2).create();
    }

    public static void main(String[] args)
    {
        createObject();
    }
}