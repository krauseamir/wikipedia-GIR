package com.krause.wikigir.main.models.namedLocationsParsers;

import com.krause.wikigir.main.models.utils.ExceptionWrapper;
import com.krause.wikigir.main.models.general.Coordinates;
import com.krause.wikigir.main.models.general.XMLParser;
import com.krause.wikigir.main.Constants;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IsAInParser extends XMLParser
{
    public static final String ENTITIES_KEY = "entities";

    // Detects a wikipedia entity in the relevant text section (when looking for the maximal diameter).
    private static final Pattern ENTITY = Pattern.compile("\\[\\[(.*?)(\\||(]]))");

    private static final Pattern DISTANCE = Pattern.compile("\\d{2,}\\s*(km|kilometer|mile|mi)");

    // The maximal number of words (from the article's beginning) we can pass before detecting a verb (is, was, etc.)
    private static final int MAX_WORDS_TILL_VERB;

    // The size (in characters) of the segment where we look for the structure (from the article's start).
    private static final int SEGMENT_CHARACTERS_SIZE;

    private static final String[] RELEVANT_VERBS = {"is", "was", "are", "were"};

    private static final String[] RELEVANT_PREPOSITIONS = {"in", "on", "at"};

    static
    {
        int[] i = {0, 0};
        ExceptionWrapper.wrap(() ->
        {
            Properties p = new Properties();
            p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));
            i[0] = Integer.parseInt(p.getProperty("wikigir.articles.is_a_in.max_words_till_verb"));
            i[1] = Integer.parseInt(p.getProperty("wikigir.articles.is_a_in.segment_characters_size"));
        });

        MAX_WORDS_TILL_VERB = i[0];
        SEGMENT_CHARACTERS_SIZE = i[1];
    }


    private Map<String, Coordinates> coordinatesMap;
    private Map<String, String> redirects;

    public IsAInParser(Map<String, Coordinates> coordinatesMap, Map<String, String> redirects)
    {
        this.coordinatesMap = coordinatesMap;
        this.redirects = redirects;
    }

    @Override
    public void parse(StringBuilder sb)
    {
        // Cannot detect these titles since creating a regex will result in a "dangling" meta character.
        if(getTitle().contains("?") || getTitle().contains("*") || getTitle().contains("+"))
        {
            return;
        }

        Pattern titlePattern = Pattern.compile("'''\\s*?" + getTitle().replaceAll("_", " ") + "\\s*?'''");

        if(this.coordinatesMap.get(getTitle()) == null)
        {
            return;
        }

        Matcher m = titlePattern.matcher(sb);
        if(!m.find())
        {
            return;
        }

        String text = getRelevantTextPortion(sb, m.end());

        // Drop pages with "100 km east of..." etc. These are not high confidence surrogates (they can still be
        // chosen as surrogates, just not using the confidence of the is-a-in).
        if(DISTANCE.matcher(text).find() || text.contains("---LOCATION DELETE---"))
        {
            return;
        }

        String[] tokens = text.split("\\s");

        int verbIndex = getFirstVerbIndex(tokens);

        if(verbIndex < 0)
        {
            return;
        }

        int prepositionIndex = getFirstPrepositionIndex(tokens, verbIndex);

        if(prepositionIndex < 0)
        {
            return;
        }

        boolean foundPeriod = false;
        List<String> portionList = new ArrayList<>();

        for(int i = prepositionIndex + 1; i < tokens.length; i++)
        {
            portionList.add(tokens[i]);

            if(tokens[i].endsWith("]].") || (tokens[i].endsWith(".") && !tokens[i].toLowerCase().startsWith("[[")))
            {
                foundPeriod = true;
                break;
            }
        }

        if(!foundPeriod)
        {
            return;
        }

        String portion = String.join(" ", portionList);

        m = ENTITY.matcher(portion);

        List<String> locations = new ArrayList<>();

        while(m.find())
        {
            String wikiTitle = wikiTitleFromFreeText(m.group(1));

            if(this.redirects.get(wikiTitle) != null)
            {
                wikiTitle = this.redirects.get(wikiTitle);

            }

            if(this.coordinatesMap.get(wikiTitle) != null)
            {
                locations.add(wikiTitle);
            }
        }

        super.result.put(ENTITIES_KEY, locations);
    }

    // Trim the article's text from right after the title portion, up to a predefined segment characters.
    private String getRelevantTextPortion(StringBuilder sb, int titleEndIndex)
    {
        String text = sb.substring(titleEndIndex);
        text = text.replaceAll("\\{\\{[Cc]onvert.*?\\d{2,}.*?((km)|(mi)).*?}}", "---LOCATION DELETE---");
        text = text.replaceAll("\\{\\s*\\{.*?}\\s*}", "");
        text = text.replaceAll("(&lt;)|(&gt;)", " ");
        text = text.replaceAll("ref name.*?=.*?/ref", " ");
        text = text.replaceAll("nbsp;", " ");
        text = text.replaceAll("&.{1,4};", " ");
        text = text.replaceAll("\\s+[,;|?]+\\s+", " ");
        text = text.replaceAll("\\(\\s*\\)", "");
        return text.substring(0, Math.min(text.length(), SEGMENT_CHARACTERS_SIZE));
    }

    // Scans the tokenized relevant article segment and looks for the first instance of one of the relevant verbs.
    private int getFirstVerbIndex(String[] tokens)
    {
        int index = -1;

        outerloop:
        for(int i = 0; i < tokens.length; i++)
        {
            if(tokens[i].length() <= 1)
            {
                continue;
            }

            // The verb is too far from the title to be considered a good indicator.
            if(i >= MAX_WORDS_TILL_VERB)
            {
                break;
            }

            // Found a period too soon.
            if(tokens[i].endsWith("]].") || (tokens[i].endsWith(".") && !tokens[i].toLowerCase().startsWith("[[")))
            {
                break;
            }

            for(String verb : RELEVANT_VERBS)
            {
                if(tokens[i].strip().toLowerCase().equals(verb))
                {
                    index = i;
                    break outerloop;
                }
            }
        }

        return index;
    }

    // Scans the tokenized relevant article segment -starting from the verb index- and looks for a relevant preposition.
    private int getFirstPrepositionIndex(String[] tokens, int verbIndex)
    {
        int index = -1;

        outerloop:
        for(int i = verbIndex + 1; i < tokens.length; i++)
        {
            // Found a period too soon.
            if(tokens[i].endsWith("]].") || (tokens[i].endsWith(".") && !tokens[i].toLowerCase().startsWith("[[")))
            {
                break;
            }

            for(String preposition : RELEVANT_PREPOSITIONS)
            {
                if(tokens[i].strip().toLowerCase().equals(preposition))
                {
                    index = i;
                    break outerloop;
                }
            }
        }

        return index;
    }
}