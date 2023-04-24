package com.krause.wikigir.main;

import com.krause.wikigir.main.models.categories.dataCreation.CategoriesFactory;
import com.krause.wikigir.main.models.articles.dataCreation.ArticlesFactory;
import com.krause.wikigir.main.models.categories.dataCreation.CategoryNamesGraph;
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
 *         CategoriesAPIQuerier and ArticleViewsCreator), or these structures must be downloaded and placed on disk.
 *         The disk structure should be:
 *         <br>
 *         [base folder]
 *         <br>
 *         <br> - Categories
 *         <br> -&nbsp;&nbsp;&nbsp; - Categories with Subcategories
 *         <br> -&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [all subcategories text files]
 *         <br>
 *         <br> - [wiki xml file]
 *     </li>
 *     <li>The process is very lengthy, especially the nearest neighbors creation file. It could take hours, or even
 *         days to complete. Sufficient heap memory (32GB and above) must be allocated via the -Xmx option. In the
 *         tests we ran, we allocated 58GB.</li>
 * </ol>
 */
public class FullDataGenerator
{
    private static long startTime = 0;

    public static void main(String[] args)
    {
        startProcess("Creating dictionary:");
        Dictionary.getInstance().create();
        System.out.println();

        startProcess("Creating articles' data:");
        ArticlesFactory af = ArticlesFactory.getInstance();
        af.create();
        System.out.println();

        startProcess("Creating inverted indices:");
        InvertedIndex.createAll();
        System.out.println();

        startProcess("Creating nearest neighbors:");
        NearestNeighbors.createFile();
        System.out.println();

        startProcess("Creating categories' data:");
        CategoriesFactory.getInstance().create();
        System.out.println();

        startProcess("Creating additional structures");
        System.out.println("is-a-in detection:");
        new IsAInCreator(af.getCoordinatesMapping(), af.getRedirects()).create();
        System.out.println("categories names graph:");
        CategoryNamesGraph.getInstance().create();
        System.out.println();
    }

    private static void startProcess(String s)
    {
        System.out.println(s);
        System.out.println("================================================================================");
        System.out.println();

        startTime = System.currentTimeMillis();
    }

    private static void endProcess()
    {
        System.out.println();
        double seconds = ((System.currentTimeMillis() - startTime) / 1000.0);
        System.out.println("Completed in " + Constants.DF.format(seconds) + " seconds.");
    }
}