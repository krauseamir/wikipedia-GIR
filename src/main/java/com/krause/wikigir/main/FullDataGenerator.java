package com.krause.wikigir.main;

import com.krause.wikigir.main.models.categories.dataCreation.CategoriesFactory;
import com.krause.wikigir.main.models.articles.dataCreation.ArticlesFactory;
import com.krause.wikigir.main.models.namedLocationsParsers.IsAInCreator;
import com.krause.wikigir.main.models.general.NearestNeighbors;
import com.krause.wikigir.main.models.general.InvertedIndex;
import com.krause.wikigir.main.models.general.Dictionary;

/**
 * Generates all needed mappings in the correct order.
 * <br>
 * Note that:
 * <ol>
 *     <li>The raw enwiki XML file must be on disk (in the location specified in program.properties).</li>
 *     <li>Structures resulting from web API calls / scraping (subcategories text files, article views) will <b>not</b>
 *         be automatically created. They must either be invoked manually (via their main() methods in
 *         CategoriesAPIQuerier and ArticleViewsCreator), or these structures must be downloaded and placed on disk.</li>
 *     <li>The process is very lengthy, especially the nearest neighbors creation file. It could take hours, or even
 *         days to complete. Sufficient heap memory (32GM and above) must be allocated via the -Xmx option.</li>
 * </ol>
 */
public class FullDataGenerator
{
    public static void main(String[] args)
    {
        printTitle("Creating dictionary:");
        Dictionary.getInstance().create();

        printTitle("Creating inverted indices:");
        InvertedIndex.createAll();

        printTitle("Creating articles' data:");
        ArticlesFactory af = ArticlesFactory.getInstance();
        af.create();

        printTitle("Creating nearest neighbors:");
        NearestNeighbors.createFile();

        printTitle("Creating categories' data:");
        CategoriesFactory.getInstance().create();

        printTitle("Creating additional structures");
        new IsAInCreator(af.getCoordinatesMapping(), af.getRedirects()).create();
    }

    private static void printTitle(String s)
    {
        System.out.println(s);
        System.out.println("================================================================================");
    }
}