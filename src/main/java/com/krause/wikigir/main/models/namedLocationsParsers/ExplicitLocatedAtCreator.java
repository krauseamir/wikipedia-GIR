package com.krause.wikigir.main.models.namedLocationsParsers;

import com.krause.wikigir.main.Constants;
import com.krause.wikigir.main.models.articles.dataCreation.ArticlesCoordinatesCreator;
import com.krause.wikigir.main.models.articles.dataCreation.ArticlesRedirectsCreator;
import com.krause.wikigir.main.models.articles.articleType.ArticlesTypeCreator;
import com.krause.wikigir.main.models.utils.*;
import com.krause.wikigir.main.models.general.WikiXMLArticlesExtractor;
import com.krause.wikigir.main.models.articles.articleType.ArticleType;
import com.krause.wikigir.main.models.general.Coordinates;

import java.util.*;
import java.io.*;

/**
 * Scans articles to detect instances (in an initial position) of "located in", "located at", etc., and attempts to
 * extract the first viable location appearing afterwards in the text. These locations are highly indicative of the true
 * location of the page. However, several validations are performed to make sure as little locations as possible are
 * wrongly taken (since only one location is take, a bad location might be very far off). These safety measure
 * (implemented in {@link ExplicitLocatedAtParser} include:
 * <br>
 * <ol>
 *     <li>Making sure the located in/at appear in the relative beginning of the text.</li>
 *     <li>Making sure the detected location is actually an entity with location in the text itself (will remove cases
 *         of words appearing in the text but are not actual entities being recognized as such).</li>
 *     <li>Removing lines with titles, certain types of texts (such as "700 kilometers").</li>
 *     <li>Validating that the diameter of all first discovered locations in the text do not vary too much, distance-wise
 *         (a large number put in place to lose as little pages as possible while eliminating *very* wrong locations).</li>
 *     <li>Checking both the regular article title and the redirect article titles for maximum effect.</li>
 * </ol>
 */
public class ExplicitLocatedAtCreator
{
    private static final int ARTICLES_LIMIT = 0;

    private final String filePath;

    private final Map<String, Coordinates> articlesWithCoordinates;
    private final Map<String, ArticleType> articlesTypesMap;
    private final Map<String, String> redirects;

    private final Map<String, String> locatedAtMapping;

    private final BlockingThreadFixedExecutor executor;

    /**
     * Constructor.
     */
    public ExplicitLocatedAtCreator(Map<String, Coordinates> articlesWithCoordinates,
                                    Map<String, ArticleType> articlesTypesMap,
                                    Map<String, String> redirects)
    {
        this.locatedAtMapping = new HashMap<>();

        this.articlesWithCoordinates = articlesWithCoordinates;
        this.articlesTypesMap = articlesTypesMap;
        this.redirects = redirects;

        this.executor = new BlockingThreadFixedExecutor();

        this.filePath = GetFromConfig.filePath("wikigir.base_path", "wikigir.articles.folder",
                                               "wikigir.articles.explicit_located_at.file_name");
    }

    /**
     * Gets the mapping of page titles to (main) coordinates from file (if existing), or
     * from the raw XML wiki file (then saving to file).
     * @return  the mapping and a set of all page titles with at least one inner or outer coordinates
     *          (naturally, this set includes all pages in the mapping as well).
     */
    public Map<String, String> create()
    {
        if(new File(this.filePath).exists())
        {
            new Serializer().deserialize();
        }
        else
        {
            readFromXML();
            new Serializer().serialize();
        }

        return this.locatedAtMapping;
    }

    private void readFromXML()
    {
        int[] processed = {0};

        WikiXMLArticlesExtractor.extract(
            () -> new ExplicitLocatedAtParser(this.articlesWithCoordinates, this.articlesTypesMap, this.redirects),
            (parser, text) ->
                this.executor.execute(() ->
                    ExceptionWrapper.wrap(() ->
                    {
                        ProgressBar.mark(processed, Constants.NUMBER_OF_ARTICLES);
                        parser.addTitleToResult(text);
                        parser.parse(text);

                        String location = (String)parser.getResult().get(ExplicitLocatedAtParser.LOCATION_KEY);

                        synchronized(ExplicitLocatedAtCreator.this)
                        {
                            if(location != null)
                            {
                                this.locatedAtMapping.put(parser.getTitle(), location);
                            }
                        }
                    }, ExceptionWrapper.Action.NOTIFY_LONG)
                ), ARTICLES_LIMIT);

        this.executor.waitForTermination();
    }

    private class Serializer implements CustomSerializable
    {
        @Override
        public String filePath()
        {
            return ExplicitLocatedAtCreator.this.filePath;
        }

        @Override
        public void customSerialize(DataOutputStream out) throws IOException
        {
            out.writeInt(ExplicitLocatedAtCreator.this.locatedAtMapping.size());
            for(Map.Entry<String, String> e : ExplicitLocatedAtCreator.this.locatedAtMapping.entrySet())
            {
                out.writeUTF(e.getKey());
                out.writeUTF(e.getValue());
            }
        }

        @Override
        public void customDeserialize(DataInputStream in) throws IOException
        {
            int count = in.readInt();
            for(int i = 0; i < count; i++)
            {
                String title = in.readUTF();
                String location = in.readUTF();
                ExplicitLocatedAtCreator.this.locatedAtMapping.put(title, location);
            }
        }
    }

    public static void main(String[] args)
    {
        System.out.println("Creating coordinates mapping (or loading from disk).");
        Map<String, Coordinates> coordinates = new ArticlesCoordinatesCreator().create();
        System.out.println("Creating redirects mapping (or loading from disk).");
        Map<String, String> redirects = new ArticlesRedirectsCreator().create();
        System.out.println("Creating article types mapping (or loading from disk).");
        Map<String, ArticleType> articlesTypes = ArticlesTypeCreator.createObject();
        System.out.println("Creating located at mapping (or loading from disk).");
        int howMany = new ExplicitLocatedAtCreator(coordinates, articlesTypes, redirects).create().size();
        System.out.println("Created. Found " + howMany + " articles with \"located at\" structure.");
    }
}