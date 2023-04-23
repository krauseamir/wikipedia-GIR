package com.krause.wikigir.main.models.articles;

import com.krause.wikigir.main.models.utils.GetFromConfig;
import com.krause.wikigir.main.models.utils.Pair;

import java.util.stream.Collectors;
import java.util.*;

/**
 * Stores information about text locations in pages - both from the text and the title.
 * @author Amir Krause
 */
public class NamedLocationsInArticle
{
    public static final int FIRST_LOCATION_OCCURRENCE_INDEX = 0;
    public static final int COUNT_INDEX = 1;

    // Named locations that were detected in the article past this word index are considered to be too
    // far from the start and thus much less likely to be relevant to assessing the article's location.
    private static final int MAX_WORD_INDEX = GetFromConfig.intValues(
            "wikigir.articles.max_word_index_for_named_locations")[0];

    public Map<String, Integer> wordsUpToLocation;
    private List<Pair<String, Integer>> locations;

    /**
     * Constructor.
     * @param locationsData a list of named locations data in the article, as parsed from the Wikipedia XML file. It
     *                      contains a list of pairs, where the first element is the named location's string name and
     *                      the second element are two integers: the first marks the first time (word index) the named
     *                      location appeared in the article and the second is the number of occurrences of the named
     *                      location in the article.
     */
    public NamedLocationsInArticle(List<Pair<String, int[]>> locationsData)
    {
        // It is possible for a location to have zero occurrences, if it was detected as a location in
        // the "non clean text" version of the page, but its variants were not found in the clean text.
        locationsData = locationsData.stream().filter(p -> p.v2[COUNT_INDEX] != 0).collect(Collectors.toList());

        Map<String, Integer> wordsUpToLocation = new HashMap<>();
        for(Pair<String, int[]> p : locationsData)
        {
            wordsUpToLocation.put(p.v1, p.v2[FIRST_LOCATION_OCCURRENCE_INDEX]);
        }

        List<Pair<String, Integer>> locations = locationsData.stream().map(p -> new Pair<>(p.v1,
                                                p.v2[COUNT_INDEX])).collect(Collectors.toList());

        constructorHelper(wordsUpToLocation, locations);
    }

    public NamedLocationsInArticle(Map<String, Integer> wordsUpToLocation, List<Pair<String, Integer>> locations)
    {
        constructorHelper(wordsUpToLocation, locations);
    }

    public List<Pair<String, Integer>> getAllLocations()
    {
        return this.locations;
    }

    public List<Pair<String, Integer>> getValidLocations()
    {
        List<Pair<String, Integer>> results = new ArrayList<>();

        for(Pair<String, Integer> p : this.locations)
        {
            if(this.wordsUpToLocation.get(p.v1) == null)
            {
                continue;
            }

            // Named locations that were detected in the article past this word index are considered to be too
            // far from the start and thus much less likely to be relevant to assessing the article's location.
            if(this.wordsUpToLocation.get(p.v1) >= MAX_WORD_INDEX)
            {
                continue;
            }

            results.add(p);
        }

        return results;
    }

    private void constructorHelper(Map<String, Integer> wordsUpToLocation, List<Pair<String, Integer>> locations)
    {
        this.locations = locations;
        this.wordsUpToLocation = wordsUpToLocation;
    }
}