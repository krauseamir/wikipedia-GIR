package com.krause.wikigir.main.models.articles.articleType;

import com.krause.wikigir.main.models.general.XMLParser;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.*;

/**
 * An XML parser that is designed to extract locations infobox information from wikipedia articles - in fact, we are
 * only interested in a single infobox information called "settlement type". If it is present, it's our best shot at
 * detecting this is a settlement.
 */
public class ArticleLocationInfoboxXMLParser extends XMLParser
{
    // The key under which the data is stored in the post-parsing map.
    public static final String ARTICLE_TYPE_KEY = "article_type";


    // If this data is present, the page is definitely about some form of settlement.
    private static final Pattern SETTLEMENT_TYPE_PATTERN = Pattern.compile("\\| *settlement_type *=(.*?)\\R");

    @Override
    public void parse(StringBuilder sb)
    {
        Matcher m = SETTLEMENT_TYPE_PATTERN.matcher(sb);
        if(m.find())
        {
            ArticleType at = getLocationTypeFromInfobox(m.group(1));
            if(at != null)
            {
                this.result.put(ARTICLE_TYPE_KEY, at);
            }
        }
    }

    // Get an infobox line in this form: | settlement_type = [[...|...|...]], separate it to its parts and try to deduce
    // the article (location) type from stored types in ArticleType using variants. Finally, take the topmost (lowest
    // priority) location type found to reduce the chance of errors (we are mainly focused on high priority locations
    // such as settlements or landmarks).
    private ArticleType getLocationTypeFromInfobox(String line)
    {
        String data = line.trim();
        if(data.startsWith("[[")) data = data.substring("[[".length());
        if(data.endsWith("]]")) data = data.substring(0, data.length() - "]]".length());
        String[] parts = data.split("\\|");

        List<ArticleType> matchedTypes = new ArrayList<>();
        for(String part : parts)
        {
            part = part.toLowerCase().trim();
            if(part.startsWith("list of "))
            {
                part = part.substring("list of".length());
            }

            // There are various variants such as "state capital" etc.
            if(part.contains(" capital"))
            {
                matchedTypes.add(ArticleType.SETTLEMENT);
                continue;
            }

            for(ArticleType lt : ArticleType.values())
            {
                for(String variant : lt.getVariants())
                {
                    if(part.startsWith(variant))
                    {
                        matchedTypes.add(lt);
                        break;
                    }
                }
            }
        }

        matchedTypes.sort(Comparator.comparingInt(ArticleType::getLocationPriority));
        Collections.reverse(matchedTypes); // from highest priority to smallest.

        return matchedTypes.isEmpty() ? null : matchedTypes.get(0);
    }
}