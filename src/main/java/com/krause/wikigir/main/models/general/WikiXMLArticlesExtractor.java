package com.krause.wikigir.main.models.general;

import com.krause.wikigir.main.Constants;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.util.Properties;
import java.io.FileReader;


/**
 * Iterates sequentially over the "raw" wikipedia XML file and extracts the contents of each individual article
 * (disregarding redirects and other resources, focusing on actual articles). Each article is then parsed through
 * a designated parser (which can look for different things in each article).
 */
public class WikiXMLArticlesExtractor
{
    /**
     * Any operation that is required to be performed on an individual article is wrapped in this
     * interface, where the parser indicates how the extraction of data from a article is done.
     */
    public interface Operationable
    {
        void operate(XMLParser parser, StringBuilder text) throws Exception;
    }

    /**
     * Sequentially reads the wiki XML file, extracts individual articles and applies the desired operation.
     * @param factory       factory generating parsing objects (what data is to be extracted from each page).
     * @param op            the operation that should be done with each page.
     * @param limit         the maximal number of pages to parse. If this number is larger than the number of
     *                      pages in the wikipedia xml file, or is 0, all pages will be parsed.
     * @param categories    should category pages be parsed, or not (title begins with "category:").
     */
    public static void extract(XMLParser.XMLParserFactory factory, Operationable op,
                               int limit, boolean categories, boolean redirects)
    {
        int parsed = 0;

        Properties p = new Properties();
        try
        {
            p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));
        }
        catch(Exception ignored) {}

        try(BufferedReader reader = new BufferedReader(new FileReader(p.getProperty("wikigir.base_path") +
                                                                 p.getProperty("wikigir.wiki_xml_file"))))
        {
            while(true)
            {
                StringBuilder text = extractPage(reader, categories, redirects);

                // This means the end of the input file has been reached.
                if(text == null)
                {
                    break;
                }

                // This means the resulting page was not a "real" page.
                if(text.length() == 0)
                {
                    continue;
                }

                op.operate(factory.getParser(), text);

                if(++parsed == limit)
                {
                    break;
                }
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * An overloading of the extract method with no categories parsing (the common case).
     * @param factory   the factory for singular page data processing.
     * @param op        the operation to be executed on each page.
     * @param limit     the maximal number of pages to parse (0 = all pages).
     */
    public static void extract(XMLParser.XMLParserFactory factory, Operationable op, int limit)
    {
        extract(factory, op, limit, false, false);
    }

    /**
     * Extracts redirects from the XML file (i.e., "articles" which are blank and just point to another article).
     * @param factory   the factory for singular page data processing.
     * @param op        the operation to be executed on each page.
     * @param limit     the maximal number of pages to parse (0 = all pages).
     */
    public static void extractRedirects(XMLParser.XMLParserFactory factory, Operationable op, int limit)
    {
        extract(factory, op, limit, false, true);
    }

    // Extracts the contents of a single page, sequentially, using a given buffered reader. Used to process the main
    // "enwiki.xml" file. Disregards pages that describe a non page (or non category) resource, pages with titles that
    // contain non US-ASCII characters, pages that are redirects, etc.
    private static StringBuilder extractPage(BufferedReader r, boolean categories, boolean redirects) throws Exception
    {
        // Holds the page text.
        StringBuilder res = new StringBuilder();

        // An invalid page is a page whose title matches one of the following: Internal wikipedia pages,
        // disambiguations, lists of things. If redirects are not searched, then also redirects.
        boolean invalidPage = false;

        boolean redirect = false;

        String line;
        while((line = r.readLine()) != null)
        {
            // A new page data starts.
            if(line.contains("<page>"))
            {
                res = new StringBuilder();
                continue;
            }

            // The page data ends - parse it if it isn't an invalid page.
            if(line.contains("</page>"))
            {
                if(redirects)
                {
                    return redirect ? res : new StringBuilder();
                }
                else
                {
                    return !invalidPage ? res : new StringBuilder();
                }
            }

            // The current line contains the page's title (creates the URL).
            if(line.contains("<title>"))
            {
                // The title indicates this is some internal wiki construct.
                // Note that "category" titled pages do not contain meaningful information
                // regarding the categories structure (such as subtitles or supertitles).
                if(line.toLowerCase().contains("wikipedia:") || line.toLowerCase().contains("file:") ||
                   line.toLowerCase().contains("portal:") || line.toLowerCase().contains("template:") ||
                   (line.toLowerCase().contains("category:") && !categories))
                {
                    invalidPage = true;
                }
                else if(line.toLowerCase().replaceAll("\\s", "").endsWith("(disambiguation)</title>"))
                {
                    invalidPage = true;
                }
                else if(line.toLowerCase().replaceAll("\\s", "").startsWith("<title>listof"))
                {
                    invalidPage = true;
                }
            }

            // This is a redirect article - do not parse it when reaching </page>.
            if(line.contains("<redirect title"))
            {
                redirect = true;

                // If we disallow redirects, this is an invalid page.
                if(!redirects)
                {
                    invalidPage = true;
                }
            }

            // If the article will not be processed, don't parse the line in vain.
            if(invalidPage)
            {
                continue;
            }

            if(line.isEmpty())
            {
                continue;
            }

            res.append(line).append("\n");
        }

        return null;
    }
}