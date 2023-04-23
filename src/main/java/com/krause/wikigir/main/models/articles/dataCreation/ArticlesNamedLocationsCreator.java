package com.krause.wikigir.main.models.articles.dataCreation;

import com.krause.wikigir.main.models.general.WikiXMLArticlesExtractor;
import com.krause.wikigir.main.models.articles.NamedLocationsInArticle;
import com.krause.wikigir.main.models.general.Coordinates;
import com.krause.wikigir.main.models.general.XMLParser;
import com.krause.wikigir.main.models.utils.*;

import java.util.stream.Collectors;
import java.util.*;
import java.io.*;

/**
 * Responsible for generating a list of "location entities" (wikipedia articles that have manually tagged coordinates)
 * which appear in an article's XML text (detected as an entity by the marking [[...]]).
 * <br>
 * The detected location entities are ordered based on their order of appearance in the article (the first entity is the
 * one which appeared first and so on), and the number of occurrences per each entity is also provided. Entities are
 * counted either by their regular Wikipedia "correct" name (having capital letters, can be used to generate an article
 * title, etc.), or by variations which are also provided in the enwiki XML file.
 */
public class ArticlesNamedLocationsCreator
{
    // The key which is used to store and fetch the xml parser's result.
    private static final String ENTITIES_KEY = "entities";

    // 0 = all articles.
    private static final int ARTICLES_LIMIT = 0;

    private final String filePath;
    private final BlockingThreadFixedExecutor executor;
    private final Map<String, Coordinates> titlesToCoordinates;
    private final Map<String, String> redirects;
    private final Map<String, NamedLocationsInArticle> titlesToLocationEntities;

    /**
     * Constructor.
     * @param titlesToCoordinates the mapping of titles to coordinates.
     */
    public ArticlesNamedLocationsCreator(Map<String, Coordinates> titlesToCoordinates, Map<String, String> redirects)
    {
        this.filePath = GetFromConfig.filePath("wikigir.base_path", "wikigir.articles.folder",
                                               "wikigir.articles.articles_to_named_locations.file_name");

        this.executor = new BlockingThreadFixedExecutor();
        this.titlesToLocationEntities = new HashMap<>();
        this.titlesToCoordinates = titlesToCoordinates;
        this.redirects = redirects;
    }

    public Map<String, NamedLocationsInArticle> create()
    {
        if(!new File(this.filePath).exists())
        {
            readFromXml();
            new Serializer().serialize();
        }
        else
        {
            new Serializer().deserialize();
        }

        return this.titlesToLocationEntities;
    }

    @SuppressWarnings("unchecked")
    private void readFromXml()
    {
        int[] parsed = {0};

        WikiXMLArticlesExtractor.extract(ArticlesContainedEntitiesParser::new,
            (parser, text) ->
                this.executor.execute(() ->
                    ExceptionWrapper.wrap(() ->
                    {
                        parser.addTitleToResult(text);
                        parser.parse(text);

                        // The parser retrieves all Wikipedia entities, their ordinal (first index of appearance) and
                        // their naming variants. We now need to select only those entities which actually have
                        // manually tagged coordinates and count how many times they appear in the text.
                        List<Pair<String, int[]>> locations;
                        locations = getLocations((Map<String, Pair<Integer, Set<String>>>)parser.
                                    getResult().get(ENTITIES_KEY), (String)parser.getResult().get(
                                    CleanTextXMLParser.CLEAN_TEXT_KEY)); // Note: search variants in a clean text!

                        synchronized(this.titlesToLocationEntities)
                        {
                            NamedLocationsInArticle nlia = new NamedLocationsInArticle(locations);

                            this.titlesToLocationEntities.put(parser.getTitle(), nlia);

                            if(++parsed[0] % 100_000 == 0)
                            {
                                System.out.println("Passed " + parsed[0] + " articles.");
                            }
                        }
                    }, ExceptionWrapper.Action.NOTIFY_LONG)
                ), ARTICLES_LIMIT);

        this.executor.waitForTermination();
    }

    // Given a map of entity names to their ordinal and name variations, do the following:
    // 1. If the entity does not have coordinates, drop it.
    // 2. Drop all name variations which are subsets of others, in the same entity.
    // 3. Count the number of occurrences of all variations for that entity, combined.
    // Then return an ordered list (based on the order of appearance) for all location entities. Each list
    // element contains the entity's name (the "representative" name, searchable and parsed as a Wikipedia
    // title), the word count in which that entity appeared, and the count of occurrences for all variations.
    private List<Pair<String, int[]>>
    getLocations(Map<String, Pair<Integer, Set<String>>> entitiesToOrdinalAndVariants, String text)
    {
        Map<String, int[]> workingMap = new HashMap<>();

        for(Map.Entry<String, Pair<Integer, Set<String>>> entity : entitiesToOrdinalAndVariants.entrySet())
        {
            String normalizedTitle = XMLParser.wikiTitleFromFreeText(entity.getKey());

            // Now make sure the entity we look for is a location with coordinates: have two attempts,
            // first in the regular titles to coordinates map, then using the redirects mapping.
            if(this.titlesToCoordinates.get(normalizedTitle) == null)
            {
                String redirect = this.redirects.get(normalizedTitle);

                if(redirect == null)
                {
                    continue;
                }

                // Try with the redirects title - also change the stored title name in the map to the redirect
                // title, so later attempts to fetch the coordinates would work (if it has coordinates).

                normalizedTitle = XMLParser.wikiTitleFromFreeText(redirect);

                if(this.titlesToCoordinates.get(normalizedTitle) == null)
                {
                    continue;
                }
            }

            // If one location variant is a substring of the other, only search the substring.
            List<String> toSearch = getSearchableVariants(entity);

            int totalCount = countVariantOccurrences(toSearch, text);

            workingMap.put(normalizedTitle, new int[] {entity.getValue().v1, totalCount});
        }

        // At this point, the list is sorted based on the current word count, which is the number of words in the
        // clean text until the first appearance of any of the entity's variants.
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(workingMap.entrySet());
        sorted.sort(Comparator.comparingInt(e -> e.getValue()[0]));

        return sorted.stream().map(x -> new Pair<>(x.getKey(), x.getValue())).collect(Collectors.toList());
    }

    // Drop all name variations which are substrings of other variations (redundant).
    private List<String> getSearchableVariants(Map.Entry<String, Pair<Integer, Set<String>>> entity)
    {
        List<String> toSearch = new ArrayList<>();
        for(String var1 : entity.getValue().v2)
        {
            boolean doNotAdd = false;

            for(String var2 : entity.getValue().v2)
            {
                if(var1.equals(var2))
                {
                    continue;
                }

                if(var1.contains(var2))
                {
                    // Because we would search the j-th term.
                    doNotAdd = true;
                    break;
                }
            }

            if(!doNotAdd)
            {
                toSearch.add(var1);
            }
        }

        return toSearch;
    }

    // This method (along with searchWord) counts the number of occurrences for all variations.
    // Each variation is searched in the article's text but -not- in a plain "indexOf" fashion - it has to
    // be preceded, and followed, by a valid word delimiter. For example, when looking for "abc", we
    // don't want to find it in "aabcd", but we do want to find it in "a, abc, def".
    private int countVariantOccurrences(List<String> toSearch, String text)
    {
        int totalCount = 0;
        for(String term : toSearch)
        {
            if(term.length() < 1)
            {
                continue;
            }

            int count = 0;
            int index = -term.length(); // required due to the loop condition.
            while((index = searchWord(text, term, index + term.length())) >= 0)
            {
                count++;
            }

            totalCount += count;
        }

        return totalCount;
    }

    // Try to find a word in a text, starting at a given index (in the text), assuming it is
    // surrounded by valid word delimiters.
    private int searchWord(String text, String word, int fromIndex)
    {
        String[] suffixDelims = {",", ".", " ", "?", "!", "]", "}", ")", "-", "_", "\"", "'", "|"};

        String[] prefixDelims = {" ", "\n", "[", "{", "(", "-", "_", "\"", "'", "|"};

        int index;
        for(String prefixDelim : prefixDelims)
        {
            for(String suffixDelim : suffixDelims)
            {
                if((index = text.indexOf(prefixDelim + word + suffixDelim, fromIndex)) >= 0)
                {
                    return index;
                }
            }
        }

        return -1;
    }

    private class Serializer implements CustomSerializable
    {
        @Override
        public String filePath()
        {
            return ArticlesNamedLocationsCreator.this.filePath;
        }

        @Override
        public void customSerialize(DataOutputStream out) throws IOException
        {
            out.writeInt(ArticlesNamedLocationsCreator.this.titlesToLocationEntities.size());

            for(Map.Entry<String, NamedLocationsInArticle> e :
                    ArticlesNamedLocationsCreator.this.titlesToLocationEntities.entrySet())
            {
                out.writeUTF(e.getKey());

                out.writeInt(e.getValue().getAllLocations().size());
                for(Pair<String, Integer> p : e.getValue().getAllLocations())
                {
                    out.writeUTF(p.v1);
                    out.writeInt(p.v2);
                }

                out.writeInt(e.getValue().wordsUpToLocation.size());
                for(Map.Entry<String, Integer> e2 : e.getValue().wordsUpToLocation.entrySet())
                {
                    out.writeUTF(e2.getKey());
                    out.writeInt(e2.getValue());
                }
            }
        }

        @Override
        public void customDeserialize(DataInputStream in) throws IOException
        {
            int size = in.readInt();

            for(int i = 0; i < size; i++)
            {
                String title = in.readUTF();


                int entitiesCount = in.readInt();

                List<Pair<String, Integer>> locations = new ArrayList<>();
                for(int j = 0; j < entitiesCount; j++)
                {
                    String entity = in.readUTF();
                    int count = in.readInt();
                    locations.add(new Pair<>(entity, count));
                }

                int wordsCountSize = in.readInt();
                Map<String, Integer> wordsUpToLocation = new HashMap<>();
                for(int j = 0; j < wordsCountSize; j++)
                {
                    String entity = in.readUTF();
                    int words = in.readInt();
                    wordsUpToLocation.put(entity, words);
                }

                ArticlesNamedLocationsCreator.this.titlesToLocationEntities.put(title,
                            new NamedLocationsInArticle(wordsUpToLocation, locations));
            }
        }
    }

    public static void main(String[] args)
    {
        System.out.println("Creating coordinates.");
        Map<String, Coordinates> coordinates = new ArticlesCoordinatesCreator().create();
        System.out.println("Creating redirects.");
        Map<String, String> redirects = new ArticlesRedirectsCreator().create();
        System.out.println("Creating named locations.");
        new ArticlesNamedLocationsCreator(coordinates, redirects).create();
    }
}