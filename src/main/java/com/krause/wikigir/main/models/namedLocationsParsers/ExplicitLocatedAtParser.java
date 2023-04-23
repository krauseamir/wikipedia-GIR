package com.krause.wikigir.main.models.namedLocationsParsers;

import com.krause.wikigir.main.Constants;
import com.krause.wikigir.main.models.articles.articleType.ArticleType;
import com.krause.wikigir.main.models.articles.dataCreation.CleanTextXMLParser;
import com.krause.wikigir.main.models.general.Coordinates;
import com.krause.wikigir.main.models.general.XMLParser;
import com.krause.wikigir.main.models.utils.ExceptionWrapper;
import com.krause.wikigir.main.models.utils.Pair;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses an article's text to discover a valid location appearing after phrases such as "located at" or "located in".
 * After finding a phrase, both the "clean" and the full (XML-annotated) texts are trimmed to a certain substring,
 * starting with the phrase. We operate on those strings by:
 *
 *  -   Iterating word after word, each word starting a new possible "sequence". For each word, start concatenating
 *      subsequent words to form a chain, such as "Paris, Texas". Test each sequence for:
 *          > It is an article appearing entity (to exclude words that are'nt actual locations in the article's context).
 *          > It has a location, or its redirect title has a location.
 *  -   Removing suffixing periods or commas before testing.
 *  -   Excluding locations which are prefaced by a "new", not part of the entity itself ("New York", with "New
 *      York" not being annotated but "York" is).
 *  -   If a certain sequence is an article entity, but not a location, discard all previous locations in the same
 *      sequence (starting with the same word). This will eliminate cases of "Paris, Texas" identified as "Paris".
 */
public class ExplicitLocatedAtParser extends XMLParser
{
    // The key name for the parsing result in the parser's map.
    public static final String LOCATION_KEY = "location";

    // Patterns for appearance of distance like "960 kilometers" - one for the clean text, one for the full text.
    private static final Pattern DISTANCE_IN_SENTENCE = Pattern.compile("\\d{2,}\\s+(nautical\\s+)?(km|kilomet|mile)");
    private static final Pattern DISTANCE_CONVERSION = Pattern.compile("\\{\\{convert\\|\\d{2,}\\|");

    // Detects a Wikipedia entity in the relevant text section (when looking for the maximal diameter).
    private static final Pattern ENTITY = Pattern.compile("\\[\\[(.*?)(\\||(]]))");

    // If the diameter of entities within the relevant full text section exceed this diameter, do not extract.
    private static final double MAX_ENTITIES_DIAMETER;

    // Do not consider structures which appear too far from the article's start.
    private static final int MAX_WORDS_TILL_PHRASE;

    // Look for named locations which appear up to this number of characters from the detected "located in" structure.
    private static final int MAX_CHARACTERS_POST_PHRASE;

    // Note the space at phrase suffix (to be as accurate as possible).
    private static final String[] RELEVANT_PHRASES =
            {"located in ", "located at ", "located outside ", "located inside ", "located east ",
             "located west ", "located north ", "located south ", "located near ", "headquartered in ",
             "headquartered at ", "found in "};

    static
    {
        double[] d = {0};
        int[] i = {0, 0};
        ExceptionWrapper.wrap(() ->
        {
            Properties p = new Properties();
            p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));
            d[0] = Double.parseDouble(p.getProperty("wikigir.articles.explicit_located_at.max_entities_diameter"));
            i[0] = Integer.parseInt(p.getProperty("wikigir.articles.explicit_located_at.max_words_till_phrase"));
            i[1] = Integer.parseInt(p.getProperty("wikigir.articles.explicit_located_at.max_characters_post_phrase"));
        });

        MAX_ENTITIES_DIAMETER = d[0];
        MAX_WORDS_TILL_PHRASE = i[0];
        MAX_CHARACTERS_POST_PHRASE = i[1];
    }

    // Detect a situation where the text is separated by a title or a new section. Since the text we look at is the
    // "clean" text, patterns of ==<title>== hae been removed from it, so we need to search for an empty line.
    // However, for robustness in future changes, in case the title is not removed, also search for a title.
    private static final Pattern SECTION_TITLE = Pattern.compile("(\n\\s+\n)|(==.*?==)");

    // Used to decide what to do with a result of parsing a certain sequence of words in the "clean" text.
    private enum SequenceResult
    {
        LOCATION,           // Discovered a valid location.
        NOT_LOCATION,       // Did not find a location.
        DISCARD_PREVIOUS    // Need to discard previously found (shorter) sequences of the current sequence.
    }

    private Map<String, Coordinates> coordinatesMap;
    private Map<String, ArticleType> articlesTypesMap;
    private Map<String, String> redirects;

    public ExplicitLocatedAtParser(Map<String, Coordinates> coordinatesMap, Map<String, ArticleType> articlesTypesMap,
                                   Map<String, String> redirects)
    {
        this.coordinatesMap = coordinatesMap;
        this.articlesTypesMap = articlesTypesMap;
        this .redirects = redirects;
    }

    @Override
    public void parse(StringBuilder sb) throws Exception
    {
        CleanTextXMLParser textParser = new CleanTextXMLParser();
        textParser.parse(sb);

        if(textParser.getResult().get(CleanTextXMLParser.CLEAN_TEXT_KEY) == null)
        {
            return;
        }

        String cleanText = (String)textParser.getResult().get(CleanTextXMLParser.CLEAN_TEXT_KEY);

        // If there is more than one phrase, try to get the one which appears first.
        int bestIndex = Integer.MAX_VALUE;

        for(String phrase : RELEVANT_PHRASES)
        {
            int index = cleanText.indexOf(phrase);
            if(index >= 0 && index < bestIndex)
            {
                int indexOf1stPeriod = cleanText.indexOf(".");

                String[] wordsToPhrase = cleanText.substring(0, index).split("\\s");

                // Only consider "located in" phrases that come after the first period, and soon.
                if(indexOf1stPeriod > index && wordsToPhrase.length < MAX_WORDS_TILL_PHRASE)
                {
                    String location = extractLocation(cleanText, sb.toString(), phrase, index);
                    if(location != null)
                    {
                        bestIndex = index;
                        this.result.put(LOCATION_KEY, location);
                    }
                }
            }
        }
    }

    // Now need to start searching in the sentence part starting with the phrase and ending with ".":
    // If testing a potential sequence, trim a suffixing ",". The winner is first appearing location,
    // who is the longest.
    private String extractLocation(String cleanText, String fullText, String phrase, int index)
    {
        int endIndex = Math.min(index + phrase.length() + MAX_CHARACTERS_POST_PHRASE, cleanText.length());

        String line = cleanText.substring(index + phrase.length(), endIndex);

        // If the line captures a subsection title, remove everything from the title onwards.
        Matcher m = SECTION_TITLE.matcher(line);
        if(m.find())
        {
            line = line.substring(0, m.start());
        }

        // Return only the full text part that is not too far away from the discovered phrase.
        String relevantFullText = getRelevantFullText(fullText, phrase);

        // There are several locations in the full text, but the diameter is too large.
        if(scatteredEntities(relevantFullText))
        {
            return null;
        }

        // From now on when searching entities in the full text, we search them lowercased.
        relevantFullText = relevantFullText.toLowerCase();

        // This indicates the sentence contains something like "located 600 miles south of...".
        if(distanceInSentence(relevantFullText, line))
        {
            return null;
        }

        String[] words = line.split("\\s");

        // Start at each word, then try to create a "sequence" starting from that word. The rule is: the first word
        // which managed to form a sequence wins, the longest valid sequence starting with that word wins.
        String bestFound = null;
        for(int i = 0; i < words.length; i++)
        {
            if(StringUtils.isBlank(words[i]))
            {
                continue;
            }

            // A special test: if we detected a location, but it had the word "new" before it (which is not part of
            // the location), we might be missing by a lot - for example, detecting "York" instead of "New York", if
            // "New York" appears in the text not as the full title of "New York City". So discard to be safe.
            if(i > 0 && words[i - 1].toLowerCase().trim().equals("new"))
            {
                continue;
            }

            List<String> currSequence = new ArrayList<>();

            for(int j = i; j < words.length; j++)
            {
                currSequence.add(words[j]);

                Pair<String, SequenceResult> currFound = trySequenceOfWords(relevantFullText, currSequence);

                if(currFound.v1 != null)
                {
                    bestFound = currFound.v1;
                }
                else if(currFound.v2 == SequenceResult.DISCARD_PREVIOUS)
                {
                    // Discard any previously found location, since a longer entity was detected, but without a
                    // location, which might cause cases of "Paris, Texas" being recognized as "Paris", if the
                    // page for "Paris, Texas" did not have a location.
                    bestFound = null;
                }
            }

            // The first appearing location always wins.
            if(bestFound != null)
            {
                return bestFound;
            }
        }

        return null;
    }

    private Pair<String, SequenceResult> trySequenceOfWords(String relevantFullText, List<String> curr)
    {
        // Return the sequence of words to canonized wikipedia title form.
        String tested = String.join("_", curr).trim();

        // Remove suffixing characters which are never part of a title but can appear in the text.
        if(tested.endsWith(",") || tested.endsWith(".") || tested.endsWith(";") || tested.endsWith("?") ||
           tested.endsWith("!") || tested.endsWith("-") || tested.endsWith("%") || tested.endsWith("#"))
        {
            tested = tested.substring(0, tested.length() - 1);
        }

        if(tested.isEmpty())
        {
            return new Pair<>(null, SequenceResult.NOT_LOCATION);
        }

        // Titles will always appear with an initial capital letter, but in the text not necessarily.
        if(!Character.isUpperCase(tested.charAt(0)))
        {
            tested = Character.toUpperCase(tested.charAt(0)) + tested.substring(1);
        }

        String possibleRedirect = this.redirects.get(tested);
        if(notEntity(relevantFullText, tested) && notEntity(relevantFullText, possibleRedirect))
        {
            // If the current sequence cannot be identified as an entity in the full text, then it's not a location.
            return new Pair<>(null, SequenceResult.NOT_LOCATION);
        }

        if(this.coordinatesMap.get(tested) != null)
        {
            return new Pair<>(tested, SequenceResult.LOCATION);
        }
        else
        {
            // Try to rescue a location using the redirects.
            if(possibleRedirect != null)
            {
                // Don't forget to transform the redirect, which appear as free text, to wiki title form.
                possibleRedirect = XMLParser.wikiTitleFromFreeText(possibleRedirect);
                if(this.coordinatesMap.get(possibleRedirect) != null)
                {
                    return new Pair<>(possibleRedirect, SequenceResult.LOCATION);
                }
            }
        }

        // The sequence is an entity, but it was not detected as a location: in this case, if we have already found
        // a location as part of a smaller prefix of the sequence, drop it. We might encounter phrases such as
        // "Paris, Texas", but if "Paris, Texas" did not have coordinates for some reason, we don't want "Paris".
        return new Pair<>(null, SequenceResult.DISCARD_PREVIOUS);
    }

    private boolean notEntity(String relevantFullText, String toCheck)
    {
        // If the phrase is null (we are testing a redirect which doesn't exist), then it's not an entity.
        if(toCheck == null)
        {
            return true;
        }

        // From titles to free text titles. Lowercase just to make sure we don't discard entities in wrong case.
        String s = toCheck.replaceAll("_", " ").toLowerCase();

        // Note - the entity needs to be with a single variant ([[<entity>]]) or we are only looking for
        // the first "official" variant of the entity, AKA "[[<variant>|"...]] to eliminate false positives.
        return !relevantFullText.contains("[[" + s + "|") && !relevantFullText.contains("[[" + s + "]]");
    }

    // Retrieves an assumed portion of the full (XML and annotated) text for testing a certain structure.
    private String getRelevantFullText(String text, String phrase)
    {
        int index = text.toLowerCase().indexOf(phrase);
        if(index < 0)
        {
            // Might happen due to changes between the full and clean text - the clean text contained the phrase
            // but for some reason the full text did not.
            return "";
        }

        // Try searching for the entity in the full text, but not too far away from the "located at".
        return text.substring(index, Math.min(index + MAX_CHARACTERS_POST_PHRASE * 2, text.length()));
    }

    // Indicates the sentence contains something like "located 600 miles south of..."
    private boolean distanceInSentence(String relevantFullText, String line)
    {
        if(DISTANCE_IN_SENTENCE.matcher(line.toLowerCase()).find())
        {
            return true;
        }

        return DISTANCE_CONVERSION.matcher(relevantFullText).find();
    }

    // Tests for the diameter of named locations appearing in the relevant full text section. If their diameter
    // is too large, the page will not be processed due to too high variance. Only consider regions and above.
    private boolean scatteredEntities(String text)
    {
        List<String> entities = new ArrayList<>();
        Matcher m = ENTITY.matcher(text);
        while(m.find())
        {
            entities.add(wikiTitleFromFreeText(m.group(1)));
        }

        List<String> redirects = new ArrayList<>();
        for(String entity : entities)
        {
            if(this.redirects.get(entity) != null)
            {
                redirects.add(wikiTitleFromFreeText(this.redirects.get(entity)));
            }
        }

        entities.addAll(redirects);

        // Only consider entities that are more specific than countries, reduce false negatives.
        entities = entities.stream().filter(x -> this.articlesTypesMap.get(x) != null).filter(x ->
                this.articlesTypesMap.get(x).getLocationPriority() >= 3).collect(Collectors.toList());

        List<Coordinates> coordinates = entities.stream().filter(x -> this.coordinatesMap.get(x) != null).
                                        map(this.coordinatesMap::get).collect(Collectors.toList());

        for(int i = 0; i < coordinates.size(); i++)
        {
            for(int j = i + 1; j < coordinates.size(); j++)
            {
                double dist = Coordinates.dist(coordinates.get(i), coordinates.get(j));
                if(dist > MAX_ENTITIES_DIAMETER)
                {
                    return true;
                }
            }
        }

        return false;
    }
}