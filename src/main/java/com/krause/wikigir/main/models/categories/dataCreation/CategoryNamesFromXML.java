package com.krause.wikigir.main.models.categories.dataCreation;

import com.krause.wikigir.main.models.general.WikiXMLArticlesExtractor;
import com.krause.wikigir.main.models.utils.ExceptionWrapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A safety measure for not missing relevant categories (not relying solely on the WikiMedia API):
 * Parse the "raw" wikipedia XML file, iterate over all articles and look for category references. Eventually, these
 * references are added to the queue in {@link CategoriesAPIQuerier}. Some of these
 * might be irrelevant/malformed category names, in which case the API will return an empty list of subcategories
 * and they will be removed from the final graph in {@link CategoryNamesGraph}.
 */
public class CategoryNamesFromXML extends CategoryNamesFromXMLBase
{
    /**
     * Constructor.
     */
    public CategoryNamesFromXML() {}

    /**
     * Iterates each article in the XML file, parses it and extracts the category references.
     * @return a set containing all unique categories extracted.
     */
    @SuppressWarnings("unchecked")
    public Set<String> getAllCategoriesInXml()
    {
        Set<String> categories = new HashSet<>();

        WikiXMLArticlesExtractor.extract(getCategoriesParserFactory(),
            (parser, text) ->
                // Launch a multithreaded operation to speed up the parsing process.
                super.executor.execute(() ->
                    ExceptionWrapper.wrap(() ->
                    {
                        parser.parse(text);

                        synchronized(categories)
                        {
                            categories.addAll((List<String>) parser.getResult().get(CATEGORIES_KEY));
                        }
                    }, ExceptionWrapper.Action.NOTIFY_LONG)
                ), ARTICLES_LIMIT);

        super.executor.waitForTermination();

        return categories;
    }
}