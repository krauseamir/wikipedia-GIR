package com.krause.wikigir.main.models.categories.dataCreation;

import com.krause.wikigir.main.models.articles.dataCreation.ArticlesFactory;
import com.krause.wikigir.main.models.utils.CustomSerializable;
import com.krause.wikigir.main.models.categories.CategoryData;
import com.krause.wikigir.main.models.utils.StringsIdsMapper;
import com.krause.wikigir.main.models.utils.GetFromConfig;
import com.krause.wikigir.main.models.general.Coordinates;
import com.krause.wikigir.main.models.articles.Article;

import java.util.*;
import java.io.*;

/**
 * Scans the titles-to-articles mapping generated by {@link ArticlesFactory} and creates a mapping from category IDs
 * to all coordinates of articles which had that category. Additional information stored per category ID is the
 * average coordinates generated for the category and the average coordinates' dispersion values.
 */
public class CategoriesFactory
{
    /**
     * Used to aggregate data about a single category when iterating over article's data (and collecting categories).
     */
    public static class AggregatedData
    {
        public String title;
        public int articlesCount;
        public Map<String, Coordinates> titlesWithCoordinates;

        /**
         * Constructor.
         */
        public AggregatedData(String title)
        {
            this.title = title;
            this.articlesCount = 0;
            this.titlesWithCoordinates = new HashMap<>();
        }
    }

    private final String filePath;

    private final Map<String, CategoryData> categoriesMap;

    /**
     * Constructor.
     */
    public CategoriesFactory()
    {
        this.filePath = GetFromConfig.filePath("wikigir.base_path", "wikigir.categories.folder",
                                               "wikigir.categories.data_file_name");

        this.categoriesMap = new HashMap<>();
    }

    /**
     * Creates the categories data, or loads it from disk if previously created.
     * @return the mapping of category title to its data object.
     */
    public Map<String, CategoryData> create()
    {
        if(!ArticlesFactory.getInstance().isCreated())
        {
            throw new RuntimeException("Must create the articles data first (ArticlesFactory.getInstance().create()).");
        }

        if(!new File(this.filePath).exists())
        {
            Map<Integer, AggregatedData> data = createDataFromArticles();

            int counter = 0;
            for(AggregatedData catData : data.values())
            {
                CategoryData metadata = new CategoryData(catData.articlesCount, catData.titlesWithCoordinates);
                this.categoriesMap.put(catData.title, metadata);

                if(++counter % 100_000 == 0)
                {
                    System.out.println("Phase 2 - passed " + counter + " categories.");
                }
            }

            new Serializer().serialize();
        }
        else
        {
            new Serializer().deserialize();
        }

        return this.categoriesMap;
    }

    /**
     * Creates the mapping from category ids to their data, based on the article's (previously parsed) data.
     * @return the mapping.
     */
    public Map<Integer, AggregatedData> createDataFromArticles()
    {
        Map<String, Article> articles = ArticlesFactory.getInstance().getArticles();
        StringsIdsMapper catIdsMapper = ArticlesFactory.getInstance().getCategoriesIdsMapping();

        Map<Integer, AggregatedData> catIdsToData = new HashMap<>();

        int counter = 0;
        for(Article article : articles.values())
        {
            if(article.getCategoryIds() == null)
            {
                continue;
            }

            for(int catId : article.getCategoryIds())
            {
                catIdsToData.putIfAbsent(catId, new AggregatedData(catIdsMapper.getString(catId)));

                // Add the article coordinates to the category's coordinates list, if present.
                if(article.getCoordinates() != null)
                {
                    catIdsToData.get(catId).titlesWithCoordinates.put(article.getTitle(), article.getCoordinates());
                }

                // Increase the number of articles in the category, regardless of coordinates.
                catIdsToData.get(catId).articlesCount++;
            }

            if(++counter % 100_000 == 0)
            {
                System.out.println("Phase 1 - passed " + counter + " articles.");
            }
        }

        return catIdsToData;
    }

    private class Serializer implements CustomSerializable
    {
        @Override
        public String filePath()
        {
            return CategoriesFactory.this.filePath;
        }

        @Override
        public void customSerialize(DataOutputStream out) throws IOException
        {
            out.writeInt(CategoriesFactory.this.categoriesMap.size());
            for(Map.Entry<String, CategoryData> e : CategoriesFactory.this.categoriesMap.entrySet())
            {
                out.writeUTF(e.getKey());
                CategoryData.serialize(out, e.getValue());
            }
        }

        @Override
        public void customDeserialize(DataInputStream in) throws IOException
        {
            int size = in.readInt();
            for(int i = 0; i < size; i++)
            {
                String title = in.readUTF();
                CategoriesFactory.this.categoriesMap.put(title, CategoryData.deserialize(in));
            }
        }
    }

    public static void main(String[] args)
    {
        System.out.println("Creating articles data.");
        ArticlesFactory.getInstance().create();
        System.out.println("Creating categories data.");
        new CategoriesFactory().create();
    }
}