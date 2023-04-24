package com.krause.wikigir.main.models.articles.dataCreation;

import com.krause.wikigir.main.models.utils.GetFromConfig;
import com.krause.wikigir.main.models.general.XMLParser;
import com.krause.wikigir.main.models.utils.Pair;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.*;

/**
 * An {@link XMLParser} which scans an article's data and finds entities, in the format defined by the Wikipedia XML
 * file (e.g., if the entity is "x", it will appear as [[x]] or [[x|variants]] if it has naming variations). An entity
 * is defined here as a Wikipedia article - all articles, not only those which have manually tagged coordinates. The
 * parsing also returns the ordinal of the entity (its first text appearance word index) and all possible naming
 * variations defined for it by Wikipedia editors, as appearing in the XML file.
 */
public class ArticlesContainedEntitiesParser extends XMLParser
{
    // The key which is used to store and fetch this XML parser's result.
    private static final String ENTITIES_KEY = "entities";

    private static final Pattern WIKI_ENTITY_PATTERN = Pattern.compile("\\[\\[ *(.*?) *]]");;

    private static final int MAX_INDEX_FOR_TITLE_REMOVAL = GetFromConfig.intValue(
            "wikigir.articles.articles_to_named_locations.max_index_for_title_removal");

    private static final int MAX_TITLE_LENGTH_FOR_REMOVAL = GetFromConfig.intValue(
            "wikigir.articles.articles_to_named_locations.max_title_length_for_removal");

    public ArticlesContainedEntitiesParser()
    {
        super.result.put(ENTITIES_KEY, new HashMap<String, Pair<Integer, Set<String>>>());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void parse(StringBuilder sb) throws Exception
    {
        addTitleToResult(sb);

        Matcher m = WIKI_ENTITY_PATTERN.matcher(sb);

        // Get the clean version of the text as well, in order to calculate where the entities
        // appear (how far from the beginning). Used to find very initial named locations.
        XMLParser cleanTextParser = new CleanTextXMLParser();
        cleanTextParser.parse(sb);
        String text = (String)cleanTextParser.getResult().get(CleanTextXMLParser.CLEAN_TEXT_KEY);
        text = removeTitle(text.toLowerCase());
        this.result.put(CleanTextXMLParser.CLEAN_TEXT_KEY, text);

        while(m.find())
        {
            String entity = m.group(1);

            // Stuff like "Category:..." and "File:..."
            if(entity.contains(":"))
            {
                continue;
            }

            // The full, correct name is identical to the article link usually. It is the first value (if
            // any follow, after "|" delimiters), but we also look for other ways to write the name.
            List<String> variants = Stream.of(entity.split("\\|")).collect(Collectors.toList());

            if(variants.isEmpty())
            {
                continue;
            }

            // This is rare, but if the location variant is actually the page itself, discard.
            if(variants.get(0).toLowerCase().equals(getTitle().toLowerCase()))
            {
                continue;
            }

            // Calculate the first appearance of that entity in the clean page text. If the entity
            // does not appear in the page text, discard it (might have appeared in the infobox).
            int wordCount = getFirstWordCount(text, variants.get(0));

            Map<String, Pair<Integer, Set<String>>> entitiesToWordLocationAndVariants =
                    (Map<String, Pair<Integer, Set<String>>>)this.result.get(ENTITIES_KEY);

            // The key entity is kept in true case (to search for page with location), but
            // the variants are normalized to broaden the search in a normalized text.

            entitiesToWordLocationAndVariants.putIfAbsent(variants.get(0), new Pair<>(wordCount, new HashSet<>()));

            entitiesToWordLocationAndVariants.get(variants.get(0)).v2.addAll(variants.stream().
                                        map(String::toLowerCase).collect(Collectors.toList()));
        }
    }

    // Attempts to remove the title from the page's text and start detecting entities right after it.
    private String removeTitle(String text)
    {
        int index = text.indexOf("'''");

        if(index < 0 || index > MAX_INDEX_FOR_TITLE_REMOVAL)
        {
            return text;
        }

        text = text.substring(index + "'''".length());

        index = text.indexOf("'''");
        if(index < 0 || index > MAX_TITLE_LENGTH_FOR_REMOVAL)
        {
            return text;
        }

        return text.substring(index + "'''".length());
    }

    // Retrieves the number of words until the first appearance of an entity (only consider the official full name).
    private int getFirstWordCount(String text, String officialVariant)
    {
        officialVariant = officialVariant.toLowerCase();

        int wordCount = -1;

        int index = text.indexOf(officialVariant);

        // The logic is as follows: if the variant is prefixed by "new" (which is not part of the variant), then
        // it means it is probably something like the variant being a city name, and the "new" totally modifying
        // it in the text, transforming it to a different city at a different location. In this case we did not
        // find the entity but the "new <entity>", continue searching.
        while(index >= 0)
        {
            if(index > " new ".length())
            {
                if(text.substring(index - " new ".length(), index).equals(" new "))
                {
                    index = text.indexOf(officialVariant, index + officialVariant.length());
                    continue;
                }
            }

            wordCount = text.substring(0, index).trim().split("\\s").length;
            break;
        }

        return wordCount;
    }
}