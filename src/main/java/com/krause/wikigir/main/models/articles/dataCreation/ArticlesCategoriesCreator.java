package com.krause.wikigir.main.models.articles.dataCreation;

import com.krause.wikigir.main.Constants;
import com.krause.wikigir.main.models.categories.dataCreation.CategoryNamesFromXMLBase;
import com.krause.wikigir.main.models.general.WikiXMLArticlesExtractor;
import com.krause.wikigir.main.models.utils.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.io.*;

/**
 * This class serves a dual purpose: It creates the mapping of article titles to category IDs and the mapping (and
 * reverse mapping) of category strings to IDs.
 */
public class ArticlesCategoriesCreator extends CategoryNamesFromXMLBase
{
    private final String articlesToCategoryIdsFile;
    private final String categoriesToIdsFile;

    private final Map<String, int[]> articlesToCategoryIds;
    private final StringsIdsMapper categoriesToIds;

    /**
     * Constructor.
     */
    public ArticlesCategoriesCreator()
    {
        this.articlesToCategoryIdsFile = GetFromConfig.filePath("wikigir.base_path", "wikigir.articles.folder",
                                                                "wikigir.articles.articles_to_categories.file_name");

        this.categoriesToIdsFile = GetFromConfig.filePath("wikigir.base_path", "wikigir.categories.folder",
                                                          "wikigir.categories.ids_file_name");

        this.articlesToCategoryIds = new HashMap<>();
        this.categoriesToIds = new StringsIdsMapper(this.categoriesToIdsFile);
    }

    /**
     * Creates the article titles to category IDs mapping and the category titles to IDs files.
     * This consists of the following phases:
     * 1) Parse the enwiki.xml file to retrieve all the categories (as strings) for all articles.
     * 2) Take all those categories as a unique set, create a mapping from categories to IDs.
     * 3) Transform the original map from title to string lists, to titles to category ID arrays.
     * 4) Return both the mappings titles to category IDs and category strings to IDs.
     *
     * @return a {@link Pair} object where the first value is the mapping from titles to category
     *         ids arrays and the second value is the mapping from category strings to int IDs.
     */
    public Pair<Map<String, int[]>, StringsIdsMapper> create()
    {
        // Note that serialization of the category-titles-to-IDs is handled in the StringsIdsMapper object.
        if(new File(this.articlesToCategoryIdsFile).exists() && new File(this.categoriesToIdsFile).exists())
        {
            new Serializer().deserialize();
            this.categoriesToIds.new Serializer().deserialize();
        }
        else
        {
            // Cover a case where one file exist and the other does not (due to bad
            // deletions etc.) and delete both files - all will be recreated.
            ExceptionWrapper.wrap(() ->
            {
                Files.deleteIfExists(Paths.get(this.articlesToCategoryIdsFile));
                Files.deleteIfExists(Paths.get(this.categoriesToIdsFile));
            });

            Map<String, List<String>> categoriesMap = readFromXml();
            createAllMappings(categoriesMap); // This also serializes the categories to IDs mapping.

            new Serializer().serialize();
        }

        return new Pair<>(this.articlesToCategoryIds, this.categoriesToIds);
    }

    // Reads the pages one by one from the enwiki.xml file and extracts the categories
    // as *strings* for each page, creating the initial mapping of titles to categories.
    @SuppressWarnings("unchecked")
    private Map<String, List<String>> readFromXml()
    {
        final Map<String, List<String>> catsMapping = new HashMap<>();

        int[] processed = {0};

        WikiXMLArticlesExtractor.extract(getCategoriesParserFactory(),
            (parser, text) ->
                this.executor.execute(() ->
                    ExceptionWrapper.wrap(() ->
                    {
                        ProgressBar.mark(processed, Constants.NUMBER_OF_ARTICLES);
                        parser.addTitleToResult(text);
                        parser.parse(text);

                        synchronized(catsMapping)
                        {
                            catsMapping.put(parser.getTitle(), (List<String>)parser.getResult().get(CATEGORIES_KEY));
                        }
                    }, ExceptionWrapper.Action.NOTIFY_LONG)
                ), ARTICLES_LIMIT);

        this.executor.waitForTermination();

        return catsMapping;
    }

    // Once all categories (as strings) have been extracted for each article, create the mapping from categories (as
    // strings) to category IDs (as integers), then re-create the titles to categories mapping by replacing each
    // category string with its int ID.
    private void createAllMappings(Map<String, List<String>> titlesToCategories)
    {
        Set<String> allCategories = new HashSet<>();
        titlesToCategories.forEach((key, value) -> allCategories.addAll(value));
        this.categoriesToIds.createFromCollection(allCategories);

        for(Map.Entry<String, List<String>> e : titlesToCategories.entrySet())
        {
            int[] list = new int[e.getValue().size()];
            for(int i = 0; i < e.getValue().size(); i++)
            {
                Integer id = this.categoriesToIds.getID(e.getValue().get(i));
                if(id == null)
                {
                    System.err.println("Category '" + e.getValue().get(i) + "' did not have an int ID.");
                    System.exit(1);
                }

                list[i] = id;
            }

            this.articlesToCategoryIds.put(e.getKey(), list);
        }
    }

    private class Serializer implements CustomSerializable
    {
        @Override
        public String filePath()
        {
            // Note that serialization of the category-titles-to-IDs is handled in the StringsIdsMapper object.
            return ArticlesCategoriesCreator.this.articlesToCategoryIdsFile;
        }

        public void customSerialize(DataOutputStream out) throws IOException
        {
            out.writeInt(ArticlesCategoriesCreator.this.articlesToCategoryIds.size());
            for(Map.Entry<String, int[]> e : ArticlesCategoriesCreator.this.articlesToCategoryIds.entrySet())
            {
                out.writeUTF(e.getKey());
                out.writeInt(e.getValue().length);
                for(int catId : e.getValue())
                {
                    out.writeInt(catId);
                }
            }
        }

        @Override
        public void customDeserialize(DataInputStream in) throws IOException
        {
            int size = in.readInt();
            for(int i = 0; i < size; i++)
            {
                String title = in.readUTF();
                int[] catIds = new int[in.readInt()];
                for(int j = 0; j < catIds.length; j++)
                {
                    catIds[j] = in.readInt();
                }

                ArticlesCategoriesCreator.this.articlesToCategoryIds.put(title, catIds);
            }
        }
    }

    public static void main(String[] args)
    {
        new ArticlesCategoriesCreator().create();
    }
}