package com.krause.wikigir.main.models.categories;

import com.krause.wikigir.main.models.utils.BlockingThreadFixedExecutor;
import com.krause.wikigir.main.models.general.XMLParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

/**
 * A base class that is used by classes scanning the wikipedia XML file to extract categories.
 * Contains the definitions of the inner-workings of the {@link XMLParser} for extracting the
 * categories from each page, as well as the workers mechanism for parallel execution.
 */
public class CategoryNamesFromXMLBase
{
    // A category will start with "[[Category:" and will always end with a "]]". However, it might also
    // contain a delimiting "|", in which case we should stop at the pipe sign. Therefore, group the
    // actual category in "(.*?)".
    private static final Pattern CATEGORY = Pattern.compile("\\[\\[[Cc]ategory *: *(.*?)(\\||]])");

    // The name of the key in the XMLParser's result map, holding the extracted data.
    protected static final String CATEGORIES_KEY = "categories";

    // 0 = parse all articles.
    protected static final int ARTICLES_LIMIT = 0;

    protected BlockingThreadFixedExecutor executor;

    /**
     * Constructor.
     */
    public CategoryNamesFromXMLBase()
    {
        this.executor = new BlockingThreadFixedExecutor();
    }

    // Returns a factory generating the implementation of an XmlParser for fetching categories.
    protected XMLParser.XMLParserFactory getCategoriesParserFactory()
    {
        return new XMLParser.XMLParserFactory()
        {
            @Override
            public XMLParser getParser()
            {
                return new XMLParser()
                {
                    {
                        this.result.put(CATEGORIES_KEY, new ArrayList<String>());
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public void parse(StringBuilder sb) throws Exception
                    {
                        Matcher m = CATEGORY.matcher(sb.toString());
                        while(m.find())
                        {
                            // The categories in the page text appear in "free text" form. For
                            // convenience, normalize them to wikipedia link form (" " -> "_").
                            // Also, remove anything after a "#" sign.
                            String category = m.group(1).replaceAll(" ", "_");
                            if(category.contains("#"))
                            {
                                category = category.substring(0, category.indexOf("#"));
                            }

                            ((List<String>)this.result.get(CATEGORIES_KEY)).add(category);
                        }
                    }
                };
            }
        };
    }
}