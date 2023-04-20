package com.krause.wikigir.main.models.general;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An abstract superclass for objects that take a single wiki page's text, analyze it
 * and produce some result. Also contains a title detection method which is needed by
 * most such XML parsers.
 *
 * @author Amir Krause
 */
public abstract class XmlParser
{
    // The key of the title in the xml parser's result map.
    private static final String TITLE = "title";

    // Regex for the title part of the page text.
    private static final Pattern TITLE_REGEX = Pattern.compile("< *title *>(.*?)< */ *title");

    // A general-purpose map holding results from different kinds of parsers.
    protected Map<String, Object> result;

    /**
     * Constructor.
     */
    public XmlParser()
    {
        this.result = new HashMap<>();
    }

    /**
     * Returns the results map.
     * @return the results map.
     */
    public Map<String, Object> getResult()
    {
        return this.result;
    }

    public String getTitle()
    {
        return (String)this.result.get(TITLE);
    }

    public abstract void parse(StringBuilder sb) throws Exception;

    // Looks for the title in the page's text and adds it to the results map.
    public void addTitleToResult(StringBuilder sb)
    {
        Matcher m = TITLE_REGEX.matcher(sb.toString());

        if(m.find())
        {
            this.result.put(TITLE, wikiTitleFromFreeText(m.group(1).trim()));
        }
    }

    /**
     * Normalizes a string such as "United States" to be a wiki-title format ("United_States").
     * @param title the text to be normalized (assumed to be representing a title).
     * @return the normalized title.
     */
    public static String wikiTitleFromFreeText(String title)
    {
        title = title.replaceAll(" ", "_");
        title = title.replaceAll("&quot;", "\"");
        title = title.replaceAll("&amp;", "&");
        return title;
    }

    // An abstract superclass for a factory generating an XML parser. A new parser is
    // instantiated per page, making a polypmorphic factory a necessity.
    public interface XmlParserFactory
    {
        XmlParser getParser();
    }
}