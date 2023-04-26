package com.krause.wikigir.main;

import com.krause.wikigir.main.models.categories.dataCreation.CategoryNamesGraph;
import com.krause.wikigir.main.models.categories.dataCreation.CategoriesFactory;
import com.krause.wikigir.main.models.articles.dataCreation.ArticlesFactory;
import com.krause.wikigir.main.models.namedLocationsParsers.IsAInCreator;
import com.krause.wikigir.main.models.general.NearestNeighbors;
import com.krause.wikigir.main.models.general.InvertedIndex;
import com.krause.wikigir.main.models.general.Dictionary;

import java.util.ArrayList;
import java.util.List;

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
 *         <br> - [Wiki xml file]
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
        endProcess();

        startProcess("Creating articles' data:");
        ArticlesFactory af = ArticlesFactory.getInstance();
        af.create();
        endProcess();

        startProcess("Creating inverted indices:");
        InvertedIndex.createAll();
        endProcess();

        startProcess("Creating nearest neighbors:");
        NearestNeighbors.createFile();
        endProcess();

        startProcess("Creating categories' data:");
        System.out.println("Categories data:");
        CategoriesFactory.getInstance().create();
        System.out.println("Categories names graph:");
        CategoryNamesGraph.getInstance().create();
        endProcess();
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

        int minutesDiv = 60;
        int hoursDiv = 60 * 60;
        int daysDiv = 24 * 60 * 60;

        long secs = ((System.currentTimeMillis() - startTime) / 1000);
        int days = (int)secs / daysDiv;
        int hours = ((int)secs - days * daysDiv) / hoursDiv;
        int minutes = ((int)secs - days * daysDiv - hours * hoursDiv) / minutesDiv;
        int seconds = (int)secs - days * daysDiv - hours * hoursDiv - minutes * minutesDiv;

        List<String> l = new ArrayList<>();

        if(days > 0)
        {
            l.add(days == 1 ? "one day" : days + " days");
        }

        if(hours > 0)
        {
            l.add(hours == 1 ? "one hour" : hours + " hours");
        }

        if(minutes > 0)
        {
            l.add(minutes == 1 ? "one minute" : minutes + " minutes");
        }

        String message = String.join(", ", l) + (l.isEmpty() ? "" : " and ");
        message += seconds;

        System.out.println("Completed in " + message + ".");
        System.out.println();
    }
}