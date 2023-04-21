package com.krause.wikigir.main.models.general;

import com.krause.wikigir.main.models.articles.dataCreation.ArticlesFactory;
import com.krause.wikigir.main.models.utils.BlockingThreadFixedExecutor;
import com.krause.wikigir.main.models.utils.CustomSerializable;
import com.krause.wikigir.main.models.utils.ExceptionWrapper;
import com.krause.wikigir.main.models.articles.Article;
import com.krause.wikigir.main.models.utils.Pair;
import com.krause.wikigir.main.Constants;

import java.util.stream.Collectors;
import java.util.*;
import java.io.*;

/**
 * An inverted index for:
 * <ul>
 *     <li>Word IDs to article title IDs which contained them.</li>
 *     <li>Category IDs to article title IDs which have that category.</li>
 *     <li>Named location (as article title IDs) IDs to article title IDs which contained them in their text.</li>
 * </ul>
 * All mappings contains a score and include two variants: to all articles and only only articles with categories.
 */
public class InvertedIndex
{
    /**
     * Type of mapping.
     */
    public enum Type
    {
        WORDS_TO_ARTICLES_COMPLETE,
        WORDS_TO_ARTICLES_WITH_COORDINATES,
        CATEGORIES_TO_ARTICLES_COMPLETE,
        CATEGORIES_TO_ARTICLES_WITH_COORDINATES,
        NAMED_LOCATIONS_TO_ARTICLES_COMPLETE,
        NAMED_LOCATIONS_TO_ARTICLES_WITH_COORDINATES
    }

    private static Map<Type, InvertedIndex> instances = new HashMap<>();

    /**
     * Singleton.
     * @return the singleton {@link InvertedIndex} object, per type.
     */
    public static InvertedIndex getInstance(Type type)
    {
        if(instances.get(type) == null)
        {
            instances.put(type, new InvertedIndex(type));
        }

        return instances.get(type);
    }

    /**
     * Used to store float values as integers, so the entire inverted index can be encoded as a 3D array of integers.
     */
    public static final int FLOAT_TO_INT_TRANSFORM_COEFF = 1_000_000;

    private Type type;

    private String folderPath;
    private String fileName;

    // Used during processing to store all results conveniently. Will be trimmed and discarded at the end.
    private Map<Integer, List<Pair<Integer, Float>>> workingMap;

    // A mapping of word ID to a list od 2-dimensional integers, where the first is the title ID and the second is the
    // score of the word in that title's article, multiplied by 1 million (to be stored as a simple integer).
    private int[][][] index;

    private boolean created;

    /**
     * Constructor.
     */
    public InvertedIndex(Type type)
    {
        ExceptionWrapper.wrap(() ->
        {
            Properties p = new Properties();
            p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));

            this.type = type;

            this.folderPath = p.getProperty("wikigir.base_path") + p.getProperty("wikigir.inverted_index.folder");

            switch(this.type)
            {
                case WORDS_TO_ARTICLES_COMPLETE:
                    this.fileName = p.getProperty("wikigir.inverted_index.articles_index_file");
                    break;
                case WORDS_TO_ARTICLES_WITH_COORDINATES:
                    this.fileName = p.getProperty("wikigir.inverted_index.articles_with_coordinates_index_file");
                    break;
                case CATEGORIES_TO_ARTICLES_COMPLETE:
                    this.fileName = p.getProperty("wikigir.inverted_index.categories_index_file_name");
                    break;
                case CATEGORIES_TO_ARTICLES_WITH_COORDINATES:
                    this.fileName = p.getProperty("wikigir.inverted_index.categories_with_coordinates_index_file");
                    break;
                case NAMED_LOCATIONS_TO_ARTICLES_COMPLETE:
                    this.fileName = p.getProperty("wikigir.inverted_index.named_locations_index_file");
                    break;
                case NAMED_LOCATIONS_TO_ARTICLES_WITH_COORDINATES:
                    this.fileName = p.getProperty("wikigir.inverted_index.named_locations_with_coordinates_index_file");
                    break;
                default:
                    throw new RuntimeException("Bad inverted index type provided.");
            }
        });

        this.created = false;
    }

    /**
     * Creates the inverted index, or loads it from disk.
     */
    public void create()
    {
        if(!ArticlesFactory.getInstance().isCreated())
        {
            throw new RuntimeException("Must create Articles object first (ArticlesFactory.getInstance().create())");
        }

        if(new File(this.folderPath + this.fileName).exists())
        {
            new Serializer().deserialize();
        }
        else
        {
            Map<String, Article> articles = ArticlesFactory.getInstance().getArticles();

            if(this.type == Type.WORDS_TO_ARTICLES_WITH_COORDINATES ||
               this.type == Type.CATEGORIES_TO_ARTICLES_WITH_COORDINATES ||
               this.type == Type.NAMED_LOCATIONS_TO_ARTICLES_WITH_COORDINATES)
            {
                articles = articles.entrySet().stream().filter(e -> e.getValue().getCoordinates() != null).
                        collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }

            createIndex(articles);
            new Serializer().serialize();
        }

        this.created = true;
    }

    /**
     * Returns all articles who had at least a given number of matches (minCollisionsThreshold) across their mapped
     * posting lists values (words, contained named locations, categories). The pruning is done using
     * {@link QuickPruner}, unless the threshold is 1.
     *
     * @param a                         the given article.
     * @param pruner                    the quick pruner object.
     * @param minCollisionsThreshold    the minimal number of words / categories that the articles need to
     *                                  share in order for the candidate to be included in the results set.
     * @return                          the pruned articles.
     */
    public Collection<String> prune(Article a, QuickPruner pruner, int minCollisionsThreshold)
    {
        if(minCollisionsThreshold < 1)
        {
            throw new RuntimeException("Minimal collisions threshold for pruner must be strictly positive.");
        }

        int[] ids = new int[0];
        switch(this.type)
        {
            case WORDS_TO_ARTICLES_COMPLETE:
            case WORDS_TO_ARTICLES_WITH_COORDINATES:
                ids = a.getWordsScoresVector().getIds();
                break;
            case CATEGORIES_TO_ARTICLES_COMPLETE:
            case CATEGORIES_TO_ARTICLES_WITH_COORDINATES:
                ids = a.getCategoryIds();
                break;
            case NAMED_LOCATIONS_TO_ARTICLES_COMPLETE:
            case NAMED_LOCATIONS_TO_ARTICLES_WITH_COORDINATES:
                ids = a.getLocationsData().getLocations().stream().mapToInt(p ->
                      ArticlesFactory.getInstance().getTitleToIdsMapping().getID(p.v1)).toArray();
        }

        List<int[][]> lists = new ArrayList<>();
        for(int id : ids)
        {
            // Could happen if the index is a partial index (for example, only with
            // articles with coordinates), thus not all words / titles will appear in it.
            if(id >= this.index.length)
            {
                continue;
            }

            if(this.index[id] != null)
            {
                lists.add(this.index[id]);
            }
        }

        Map<Integer, Integer> result;

        // The fast pruning algorithm is not designed to work with a threshold of 1. Use the simpler algorithm instead.
        if(minCollisionsThreshold == 1)
        {
            result = new HashMap<>();
            for(int[][] list : lists)
            {
                for (int[] pair : list)
                {
                    result.put(pair[0], 1);
                }
            }
        }
        else
        {
            result = pruner.prune(lists);
        }

        result.remove(ArticlesFactory.getInstance().getTitleToIdsMapping().getID(a.getTitle()));

        return result.entrySet().stream().filter(e -> e.getValue() >= minCollisionsThreshold).map(e ->
               ArticlesFactory.getInstance().getTitleToIdsMapping().getString(e.getKey())).collect(Collectors.toList());
    }

    /**
     * Returns the index.
     * @return the index.
     */
    public int[][][] getIndex()
    {
        return this.index;
    }

    /**
     * Returns true after the create() method has been invoked.
     * @return true if the create() method has been invoked, otherwise false.
     */
    public boolean isCreated()
    {
        return this.created;
    }

    // Creates the index using the given articles map, then creates the slimmest possible version of the index by
    // transforming it into a list of integers, where the list for the word with id x is in cell x of the integers
    // array (there are empty cells for words with empty lists).
    private void createIndex(Map<String, Article> articles)
    {
        this.workingMap = new HashMap<>();

        for(Map.Entry<String, Article> e : articles.entrySet())
        {
            Integer[] titleId = {null};
            ExceptionWrapper.wrap(() ->
            {
                titleId[0] = ArticlesFactory.getInstance().getTitleToIdsMapping().getID(e.getKey());
                if(titleId[0] == null)
                {
                    throw new Exception("Missing title ID in inverted index.");
                }
            });

            // There are three types of inverted indices - for words, for (named location) titles and for categories.
            // They all behave similarly, with the difference being the type of IDs and the value of the scores.

            int[] ids = new int[0];
            float[] scores = new float[0];

            if(this.type == Type.WORDS_TO_ARTICLES_COMPLETE ||
               this.type == Type.WORDS_TO_ARTICLES_WITH_COORDINATES)
            {
                ids = e.getValue().getWordsScoresVector().getIds();
                scores = e.getValue().getWordsScoresVector().getScores();
            }
            else if(this.type == Type.CATEGORIES_TO_ARTICLES_COMPLETE ||
                    this.type == Type.CATEGORIES_TO_ARTICLES_WITH_COORDINATES)
            {
                ids = e.getValue().getCategoryIds();
                scores = new float[ids.length];
                Arrays.fill(scores, 1f);
            }
            else if(this.type == Type.NAMED_LOCATIONS_TO_ARTICLES_COMPLETE ||
                    this.type == Type.NAMED_LOCATIONS_TO_ARTICLES_WITH_COORDINATES)
            {
                ids = e.getValue().getNamedLocationsScoredVector().getIds();
                scores = e.getValue().getNamedLocationsScoredVector().getScores();
            }

            for(int i = 0; i < ids.length; i++)
            {
                this.workingMap.putIfAbsent(ids[i], new ArrayList<>());
                this.workingMap.get(ids[i]).add(new Pair<>(titleId[0], scores[i]));
            }
        }

        transformTo3DIntList();
    }

    // Trims the working map (with the inverted index) into the slimmest possible version: an array
    // of integer arrays: the integer array matching cell x is the list of title IDs in whose articles
    // the word/category, whose ID is x, appeared.
    // Naturally, some indices have empty mappings, but it allows accessing the list in instant
    // time, given the word ID.
    protected void transformTo3DIntList()
    {
        // Dynamically resized when necessary.
        this.index = new int[1][][];

        // Speed up the process by using workers.
        BlockingThreadFixedExecutor executor = new BlockingThreadFixedExecutor();

        for(Map.Entry<Integer, List<Pair<Integer, Float>>> e : this.workingMap.entrySet())
        {
            executor.execute(() ->
            {
                // a list of title mappings for the word: index 0 = the title, index 1 = the score.
                int[][] list = new int[e.getValue().size()][2];

                for(int i = 0; i < e.getValue().size(); i++)
                {
                    list[i][0] = e.getValue().get(i).v1;
                    list[i][1] = (int)(e.getValue().get(i).v2 * FLOAT_TO_INT_TRANSFORM_COEFF);
                }

                synchronized(InvertedIndex.this)
                {
                    // Increase the list until the size is sufficient.
                    while(this.index.length - 1 < e.getKey())
                    {
                        resizeIndex(this.index.length * 2, this.index.length);
                    }

                    this.index[e.getKey()] = list;
                }
            });
        }

        executor.waitForTermination();

        // Finally, trim the list to the exact desired size. Scan backwards to make sure we get the exact first
        // non-null list, without relying on (possibly changing) calculations of indices and locations in the index.
        int finalSize = this.index.length;
        for(; finalSize > 0 ; finalSize--)
        {
            if(this.index[finalSize - 1] != null)
            {
                break;
            }
        }

        resizeIndex(finalSize, finalSize);

        this.workingMap.clear();
        this.workingMap = null;
    }

    // Resizes the index to a given size and fills it with data for a given length.
    private void resizeIndex(int newSize, int length)
    {
        int[][][] prev = this.index;
        this.index = new int[newSize][][];
        System.arraycopy(prev, 0, this.index, 0, length);
    }

    private class Serializer implements CustomSerializable
    {
        @Override
        public String filePath()
        {
            return InvertedIndex.this.folderPath + InvertedIndex.this.fileName;
        }

        @Override
        public void customSerialize(DataOutputStream out) throws IOException
        {
            out.writeInt(InvertedIndex.this.index.length);
            for(int[][] list : InvertedIndex.this.index)
            {
                if(list == null)
                {
                    out.writeInt(0);
                }
                else
                {
                    out.writeInt(list.length);
                    for (int[] pair : list)
                    {
                        out.writeInt(pair[0]);
                        out.writeInt(pair[1]);
                    }
                }
            }
        }

        @Override
        public void customDeserialize(DataInputStream in) throws IOException
        {
            InvertedIndex.this.index = new int[in.readInt()][][];
            for(int i = 0; i < InvertedIndex.this.index.length; i++)
            {
                int listSize = in.readInt();
                if(listSize > 0)
                {
                    InvertedIndex.this.index[i] = new int[listSize][2];
                    for(int j = 0; j < InvertedIndex.this.index[i].length; j++)
                    {
                        InvertedIndex.this.index[i][j][0] = in.readInt();
                        InvertedIndex.this.index[i][j][1] = in.readInt();
                    }
                }
            }
        }
    }

    public static void main(String[] args)
    {
        System.out.println("Creating articles file mapping...");
        ArticlesFactory.getInstance().create();

        System.out.println("Creating the inverted indices...");
        System.out.println("words to articles complete");
        InvertedIndex.getInstance(Type.WORDS_TO_ARTICLES_COMPLETE).create();
        System.out.println("words to articles with coordinates");
        InvertedIndex.getInstance(Type.WORDS_TO_ARTICLES_WITH_COORDINATES).create();
        System.out.println("categories to articles complete");
        InvertedIndex.getInstance(Type.CATEGORIES_TO_ARTICLES_COMPLETE).create();
        System.out.println("categories to articles with coordinates");
        InvertedIndex.getInstance(Type.CATEGORIES_TO_ARTICLES_WITH_COORDINATES).create();
        System.out.println("named locations to articles complete");
        InvertedIndex.getInstance(Type.NAMED_LOCATIONS_TO_ARTICLES_COMPLETE).create();
        System.out.println("named locations to articles with coordinates");
        InvertedIndex.getInstance(Type.NAMED_LOCATIONS_TO_ARTICLES_WITH_COORDINATES).create();
    }
}