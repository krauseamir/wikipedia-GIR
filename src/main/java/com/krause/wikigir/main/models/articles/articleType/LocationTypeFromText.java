package com.krause.wikigir.main.models.articles.articleType;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Uses a combination of text based and categories based heuristics to figure out an article's type, for articles which
 * are locations (i.e., it doesn't handle people, ships, etc.). For example, a common first sentence structure in
 * wikipedia is: "X is a city in ...", in which case the text itself clearly divulges the information. In other cases,
 * the categories for an article reveal its location type, with common category conventions such as "cities in France"
 * or "countries in Africa", which make clear what the article's type, and location type, are.
 */
public class LocationTypeFromText
{
    // The maximal number of words (from the beginning) to search for "is a X in Y" type structure.
    private static final int MAX_INITIAL_WORDS = 50;

    // Once a "is/are/was/were" is detected, this is the maximal distance (words) to look for the end.
    private static final int VERB_PROXIMITY = 5;

    // Start scanning for the location from these words.
    private static final String[] VERB = {"is", "are", "was", "were"};

    // Stop scanning for locations at these words (otherwise, if we have something like:
    // "X is a house in the united kingdom", and kingdom would be wrongly recognized).
    private static final String[] STOP_AT = {"in", "of", "that", "at", "on"};

    // The first MAX_INITIAL_WORDS of the clean-text parsed version of the article, with stop words.
    private List<String> initialWords;

    // The article's categories.
    private List<String> categories;

    /**
     * Constructor.
     * @param words         the article's words *including stop words*.
     * @param categories    the article's categories.
     */
    public LocationTypeFromText(List<String> words, List<String> categories)
    {
        this.initialWords = words.subList(0, Math.min(words.size(), MAX_INITIAL_WORDS));
        this.categories = categories;
    }

    /**
     * Applies the heuristics to find the article's (location) type, if applicable:
     * 1. Find the index of the first pronoun ("in", "at", etc).
     * 2. From that index, until finding a stop word (indicating the plausible end of the "x [pronoun] y" structure),
     *    try to search for all location types variants. Apply special handling to avoid some frequent common caveats.
     *
     * @return the article's type, or null if it isn't a location article, or no location found.
     */
    public ArticleType process()
    {
        int firstVerbIndex = firstVerbIndex();
        if(firstVerbIndex == -1)
        {
            return null;
        }

        for(int i = firstVerbIndex; i < Math.min(firstVerbIndex + VERB_PROXIMITY, this.initialWords.size()); i++)
        {
            // Finding any of these words indicates the end of the "x [pronoun] y" structure.
            for(String sa : STOP_AT)
            {
                if(sa.equals(this.initialWords.get(i)))
                {
                    return null;
                }
            }

            // Reached the end of a sentence, so the structure of "is... of ..." probably ended.
            if(this.initialWords.get(i).trim().endsWith("."))
            {
                return null;
            }

            ArticleType locType = detectLocationType(this.initialWords.get(i));

            if(locType != null)
            {
                // United States would wrongly identify other terms as a country.
                if(i > 0 && this.initialWords.get(i).equals("states") &&
                        this.initialWords.get(i - 1).equals("united"))
                {
                    continue;
                }

                // Try to capture situations like "island country" or "state capital".
                if(i < this.initialWords.size() - 1)
                {
                    ArticleType locType2 = detectLocationType(this.initialWords.get(i + 1));
                    if(locType2 != null)
                    {
                        if(locType2.getLocationPriority() > locType.getLocationPriority())
                        {
                            if(validateWithCategories(locType2))
                            {
                                return locType2;
                            }
                        }
                    }
                }

                if(validateWithCategories(locType))
                {
                    return locType;
                }
            }

        }

        return null;
    }

    // Finds the index of the first pronoun in the text.
    private int firstVerbIndex()
    {
        for(int i = 0; i < this.initialWords.size(); i++)
        {
            for(String verb : VERB)
            {
                if(this.initialWords.get(i).equals(verb))
                {
                    return i;
                }
            }
        }

        return -1;
    }

    // Checks if a given word is one of the variants of a location type.
    private ArticleType detectLocationType(String word)
    {
        for(ArticleType locType : ArticleType.values())
        {
            for(String variant : locType.getVariants())
            {
                if(word.equals(variant))
                {
                    return locType;
                }
            }
        }

        return null;
    }

    // "country" can be things like "country club". Make sure in the categories that the page we are parsing is
    // a country ("countries in..."). Try top minimize situations of things like "state house", "state park" etc.
    private boolean validateWithCategories(ArticleType found)
    {
        switch(found)
        {
            case COUNTRY:
                for(String cat : this.categories)
                {
                    if(cat.startsWith("countries") && (cat.contains("_in_") || cat.contains("_of_")))
                    {
                        return true;
                    }
                }
                return false;
            case STATE:
                for(String cat : this.categories)
                {
                    if(cat.startsWith("states") && (cat.contains("_in_") || cat.contains("_of_")))
                    {
                        return true;
                    }
                }
                return false;
            default:
                return true;
        }
    }
}