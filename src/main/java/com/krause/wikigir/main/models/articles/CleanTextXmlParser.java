package com.krause.wikigir.main.models.articles;

import com.krause.wikigir.main.models.general.XmlParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.StringReader;

/**
 * Receives raw Wikipedia XML lines and tags, relevant for a single article. This object then parses the XML into a
 * readable, clean text by performing the following operations:
 * <br><br>
 * <ol>
 * 	<li>Getting only the relevant section (between the <text> and </text> tags).</li>
 * 	<br>
 * 	<li>Handling recursive metadata entries: citations ("{{}}"), square brackets ("[[]]") and tables
 * 	    ("{}") by iteratively finding them and removing them from the text. Each time we look for
 * 	    "clean" entries, for example, if we are looking for citations, we will look for citations
 * 	    which do not have another citation inside them (do not contain another "{" between the starting
 * 	    and ending brackets).
 * 		<br><br>
 * 		Note the citation entries are temporarily stored, as they are used for extracting external URLs
 * 		(URLs without "wiki" inside).</li>
 * 	<br>
 * 	<li>Looking for - and removing - irrelevant text, such as references, tags, titles, HTML codes etc.</li>
 * 	<br>
 * 	<li>Removing all lines with an illegal beginning (for example "#").</li>
 * 	<li>Removing bad article starts (information before the title).</li>
 * </ol>
 *
 * @author Amir Krause.
 */
public class CleanTextXmlParser extends XmlParser
{
    // Keys of data structures stored in the result map.
    public static final String CLEAN_TEXT_KEY = "clean_text";

    private static final int NESTED_BRACKETS_REMOVAL_ITERATIONS = 3;

    // After the text has been processed, we try to remove redundant bits that appear before the "real" text starts,
    // such as disambiguation information etc. This is the maximal distance (from text start) we are allowed to find
    // the title in (in the '''<title>''' format) - if the title is further away, we do not substring.
    private static final int MAX_DISTANCE_FOR_TITLE_IN_TEXT = 250;

    // Detects the <text...>...</text> part of the article's text.
    private static final Pattern TEXT_PART_PATTERN = Pattern.compile("<text xml.*?>(.*?)</text>", Pattern.DOTALL);

    // Detects a "clean" doubly square brackets string, which means the square bracketed text does not contain
    // another square bracketed text. Used for the iterative removal of recursive metadata entries.
    // Note that inside the doubly square brackets, anything but "[" is allowed.
    private static final Pattern SQUARE_BRACKETS = Pattern.compile("\\[\\[([^\\[]*?)]]", Pattern.DOTALL);

    // Detects a "clean" doubly curly brackets string, which means the curly bracketed text does not contain another
    // curly bracketed text. Used for the iterative removal of recursive metadata entries.
    // Note that inside the doubly curly brackets, anything but "{" is allowed.
    private static final Pattern CITATION = Pattern.compile("\\{\\{[^{]*?}}", Pattern.DOTALL);

    // Detects a "clean" curly brackets string, which means the curly bracketed text does not contain another curly
    // bracketed text. Used for the iterative removal of recursive metadata entries.
    // Note that inside the curly brackets, anything but "{" is allowed.
    private static final Pattern TABLE = Pattern.compile("\\{[^{]*?}", Pattern.DOTALL);

    // Contains patterns for removal of various text types: references (within HTML tags), tags (between "<" and ">"
    // characters), titles (surrounded by "=" of changing lengths), quotes, ampersands, HTML spaces (nbsp), wikipedia
    // terms prefix text ("wikt:").
    private static final Pattern[] OTHER_PATTERNS = new Pattern[]{
            Pattern.compile("&lt;.*?&gt;", Pattern.DOTALL),
            Pattern.compile("(=){1,3}.*\\1"),
            Pattern.compile("(&quot;)|(&amp;)|(nbsp;)|(wikt:)")};

    /**
     * Constructor.
     */
    public CleanTextXmlParser() {}

    /**
     * Parses the given xml text lines, representing an article's data.
     * @param article the given xml text lines.
     * @throws Exception if parsing failed.
     */
    public void parse(StringBuilder article) throws Exception
    {
        addTitleToResult(article);

        // Step 1 - find the xml lines between the <text> tags (inclusive).
        Matcher m = TEXT_PART_PATTERN.matcher(article);

        if(!m.find())
        {
            this.result.put(CLEAN_TEXT_KEY, "");
            return;
        }

        String text = m.group(1);

        // Remove all nested brackets (citations, square brackets, tables) iteratively, until the
        // article has none of them, but keep their inner-text. Note that we remove information we
        // extract later from the unprocessed text.
        text = removeNestedBrackets(text);

        // Remove all unneeded texts (references, tags, etc).
        for(Pattern pattern : OTHER_PATTERNS)
        {
            text = pattern.matcher(text).replaceAll(" ");
        }

        text = removeLinesWithIllegalBeginnings(text);

        // Finally, try to start the text from the actual start, not any disambiguation or prefixing
        // text that precedes the actual article's text.
        text = startWithTitle(text);

        this.result.put(CLEAN_TEXT_KEY, text);
    }


    // Private methods.
    // --------------------------------------------------------------------------------------------

    // Iteratively tries to remove recursive brackets.
    private String removeNestedBrackets(String text)
    {
        StringBuilder temp;
        Matcher m;

        // The first situation is a square brackets - these sometimes contain a reference to a file
        // (in which case - dismiss the entire metadata), and sometimes some disambiguation of words -
        // in which case take only the first combination (before the delimiting |). On other cases
        // (most common), it is simply a wikipedia link in the form of [[link]]. In any event, if it
        // is a valid text we want to add it without the brackets. Do this iteratively to detect
        // "brackets within brackets", a possible situation.
        for(int i = 0; i < NESTED_BRACKETS_REMOVAL_ITERATIONS; i++)
        {
            m = SQUARE_BRACKETS.matcher(text);

            int start = 0;

            temp = new StringBuilder();

            while(m.find())
            {
                temp.append(text, start, m.start());

                // Part contains just the clean text between the brackets - note that the regex
                // specifies no infixed "[" are allowed, so this is a clean text.
                String part = m.group(1);

                // Disregard file metadata.
                if(part.contains("File:"))
                {
                    part = "";
                }

                // Take only the first combination.
                if(part.contains("|"))
                {
                    // Taking "sculpture" in case of a human sculptor will mess up spot location detection.
                    if(part.toLowerCase().contains("sculpture") && part.toLowerCase().contains("sculptor"))
                    {
                        part = "sculptor";
                    }
                    else if(part.toLowerCase().contains("musical theatre") && part.toLowerCase().contains("musical"))
                    {
                        part = "musical";
                    }
                    else
                    {
                        part = part.substring(0, part.indexOf("|"));
                    }
                }

                temp.append(part);
                start = m.end();
            }

            temp.append(text.substring(start));

            // Prepare the text for the next iteration.
            text = temp.toString();
        }

        // Remove citations.
        for(int i = 0; i < NESTED_BRACKETS_REMOVAL_ITERATIONS; i++)
        {
            text = CITATION.matcher(text).replaceAll("");
        }

        // The table metadata can be completely removed immediately.
        for(int i = 0; i < NESTED_BRACKETS_REMOVAL_ITERATIONS; i++)
        {
            text = TABLE.matcher(text).replaceAll("");
        }

        return text;
    }

    // As the name suggests, iterates over the article's text and recreates it, adding only lines with approved
    // beginnings. Note that we are dealing with the "pure" text here, and extraction of categories and other
    // metadata is done on the raw article text.
    private String removeLinesWithIllegalBeginnings(String text) throws Exception
    {
        StringBuilder result = new StringBuilder();

        BufferedReader r = new BufferedReader(new StringReader(text));

        String line;
        while((line = r.readLine()) != null)
        {
            if(line.startsWith("Category:") || line.startsWith("|") || line.startsWith("!") ||
               line.startsWith("*") || line.startsWith("#") || line.startsWith("Image:"))
            {
                continue;
            }

            result.append(line).append("\n");
        }

        return result.toString();
    }

    // Remove anything before the '''<title>''' mark in the text. This is done to remove redundant
    // disambiguations etc. which create unneeded words and possibly wrong named locations.
    private String startWithTitle(String text)
    {
        String title = getTitle();

        if(title.contains(","))
        {
            title = title.substring(0, title.indexOf(","));
        }

        if(title.contains("("))
        {
            title = title.substring(0, title.indexOf("("));
        }

        // The "trim" is important to remove probable suffixing " ", switched from "_", if title had "," or "(".
        title = title.replaceAll("_", " ").trim();

        int index = text.indexOf("'''" + title);

        if(index < 0)
        {
            // Try to remove dashes and search tht title again.
            title = title.replaceAll("-", " ").trim();
            index = text.indexOf("'''" + title);
        }

        // Don't stray too far from the beginning, to not actually lose important text.
        if(index >= MAX_DISTANCE_FOR_TITLE_IN_TEXT)
        {
            return text;
        }

        return index >= 0 ? text.substring(index) : text;
    }
}