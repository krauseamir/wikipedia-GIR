package com.krause.wikigir.main.models.general;

import com.krause.wikigir.main.models.articles.dataCreation.ArticlesFactory;
import com.krause.wikigir.main.models.articles.ArticlesSimilarityCalculator;
import com.krause.wikigir.main.models.utils.ExceptionWrapper;
import com.krause.wikigir.main.models.utils.GetFromConfig;
import com.krause.wikigir.main.models.utils.StringsIdsMapper;
import com.krause.wikigir.main.models.articles.Article;
import com.krause.wikigir.main.models.utils.Pair;
import com.krause.wikigir.main.Constants;

import java.util.stream.Collectors;
import java.util.*;
import java.io.*;

/**
 * Calculates the nearest neighbors for all articles (articles with coordinates, whose score with the article is of
 * some minimal threshold, and up to a bounded number of neighbors). The workload is divided among several workers (in
 * distinct threads), each taking care of a certain segment of the articles mapping. The results are written to disk.
 */
public class NearestNeighbors
{
    public static final String TF_IDF_WEIGHT_KEY = "tf-idf";
    public static final String CATEGORIES_WEIGHT_KEY = "categories";
    public static final String NAMED_LOCATIONS_WEIGHT_KEY = "named-locations";

    private static final int GENERATION_PRINT_CHECKPOINT = 1000;

    /**
     * A worker object which receives part of the articles mapping and calculates the nearest neighbors for each
     * article in that part, based on tf-idf, named locations and categories similarity. The result is written to file.
     */
    private class NearestNeighborsWorker implements Runnable
    {
        // Used to perform quick pruning for possible candidate similar articles for each article.
        private QuickPruner pruner;

        // The entries from the complete articles map this worker needs to handle.
        private Collection<Map.Entry<String, Article>> subset;

        /**
         * Constructor.
         * @param subset the partial segment of the articles map processed by this worker.
         */
        public NearestNeighborsWorker(Collection<Map.Entry<String, Article>> subset)
        {
            this.pruner = new QuickPruner();
            this.subset = subset;
        }

        /**
         * Iterates over this worker's articles "portion" and calculates the neighbors.
         * The result is immediately written to file and is not stored in memory.
         */
        public void run()
        {
            for(Map.Entry<String, Article> e : this.subset)
            {
                ExceptionWrapper.wrap(() ->
                {
                    List<Pair<String, Float>> result = getNearestNeighborsWithLocations(e.getValue());

                    NearestNeighbors.this.write(e.getKey(), result);

                    synchronized(NearestNeighbors.this)
                    {
                        if(++NearestNeighbors.this.count % GENERATION_PRINT_CHECKPOINT == 0)
                        {
                            System.out.println("Passed " + NearestNeighbors.this.count + " articles.");
                        }
                    }
                }, ExceptionWrapper.Action.NOTIFY_LONG);

            }
        }

        // Given an article:
        // 1. Prune the articles corpus to get neighbor candidates (with coordinates).
        // 2. Calculate the matching score for each neighbor, filter those whose score is too low.
        // 3. Sort the neighbors by matching score, in descending order, and trim the list if necessary.
        // Note the pruning is done on an inverted index containing only pages that have coordinates (other pages are
        // irrelevant for the purpose of assessing locations).
        private List<Pair<String, Float>> getNearestNeighborsWithLocations(Article article)
        {
            List<Pair<String, Float>> result = new ArrayList<>();

            Set<String> pruned = getPrunedTitles(article);

            for(String title : pruned)
            {
                Article candidate = NearestNeighbors.this.articles.get(title);

                // Shouldn't happen: all articles have a mapping and the index only indexes articles with coordinates.
                if(candidate == null || candidate.getCoordinates(null) == null)
                {
                    continue;
                }

                // Match the notations (alpha, beta, gamma) in the paper presenting this work.
                double alpha = NearestNeighbors.this.tfIdfScoreWeight;
                double beta = NearestNeighbors.this.namedLocationsScoreWeight;
                double gamma = NearestNeighbors.this.categoriesScoreWeight;

                // Calculate the score using the given weighting (initialized in the enclosing class).
                float score = (float)ArticlesSimilarityCalculator.calculate(article, candidate, alpha, beta, gamma);

                if(score >= NearestNeighbors.this.minSimilarity)
                {
                    result.add(new Pair<>(candidate.getTitle(), score));
                }
            }

            result.sort(Comparator.comparingDouble(Pair::getV2));
            Collections.reverse(result);

            if(result.size() > NearestNeighbors.this.maxNeighborsPerArticle)
            {
                result = result.subList(0, NearestNeighbors.this.maxNeighborsPerArticle);
            }

            return result;
        }

        // Given an article a*, this method fetches only a small subset of the entire articles corpus, which might be
        // good candidates for a nearest neighbor (instead of computing the complete score for each and every article).
        // An article a is considered a possible candidate as a nearest neighbor if it either:
        //  - has a certain overlap (k1) of top words (by tf-idf score) with a*.
        //  - has a certain overlap (k2) of contained named locations with a*.
        //  - has a certain overlap (k3) of categories with a*.
        // An article which does not answer neither of these conditions is unlikely to be "close" to a*. Increasing the
        // values of k1, k2 and k3 will result in fewer - more probable candidates - but will hurt the recall and it is
        // possible to miss relevant candidates. Setting k1 = k2 = k3 = 1 will result in getting -all- possible nearest
        // neighbors, but is much more computationally intensive as the pruning is less effective. In the paper, we set
        // the values k1 = 2, k2 = k3 = 1.
        private Set<String> getPrunedTitles(Article a)
        {
            Collection<String> byWords = new ArrayList<>();
            Collection<String> byCategories = new ArrayList<>();
            Collection<String> byNamedLocations = new ArrayList<>();

            // We check if the assigned weight is greater than 0, since there is no point in considering candidate
            // articles using a property that is not used in the final scoring.
            if(NearestNeighbors.this.tfIdfScoreWeight > 0)
            {
                InvertedIndex.Type t = InvertedIndex.Type.WORDS_TO_ARTICLES_WITH_COORDINATES;
                byWords = InvertedIndex.getInstance(t).prune(a, this.pruner, NearestNeighbors.this.tfIdfThresh);
            }

            if(NearestNeighbors.this.categoriesScoreWeight > 0)
            {
                InvertedIndex.Type t = InvertedIndex.Type.CATEGORIES_TO_ARTICLES_WITH_COORDINATES;
                byCategories = InvertedIndex.getInstance(t).prune(a, this.pruner, NearestNeighbors.this.catThresh);
            }

            if(NearestNeighbors.this.namedLocationsScoreWeight > 0)
            {
                InvertedIndex.Type t = InvertedIndex.Type.NAMED_LOCATIONS_TO_ARTICLES_WITH_COORDINATES;
                byNamedLocations = InvertedIndex.getInstance(t).prune(a, this.pruner, NearestNeighbors.this.nlThresh);
            }

            Set<String> pruned = new HashSet<>();
            pruned.addAll(byWords);
            pruned.addAll(byCategories);
            pruned.addAll(byNamedLocations);

            return pruned;
        }
    }

    // Counts the total number of articles processed, updated by the workers.
    private int count;

    private Map<String, Article> articles;

    // The minimal number of top-words collisions that an articles needs in order to be considered a valid candidate
    // to be a neighbor. Collisions = the number of top-words shared by both it and the original articles.
    private int tfIdfThresh;

    // The minimal number of categories collisions that an articles needs in order to be considered a valid
    // candidate to be a neighbor. Collisions = number of categories shared by it and the original articles.
    private int catThresh;

    // The minimal number of named locations collisions that an articles needs in order to be considered a valid
    // candidate to be a neighbor. Collisions = number of named locations shared by it and the original articles.
    private int nlThresh;

    // The minimal similarity score required for a neighbor to be considered valuable. Note that this value only
    // determines whether or not a neighbor is eligible to be stored and kept, but when using the nearest neighbors
    // for some article, we can filter out neighbors based on a higher minimal threshold score.
    private double minSimilarity;

    // The maximal number of neighbors -which are stored- per each article.
    private int maxNeighborsPerArticle;

    // Weights of the similarity component (words, named locations, categories) in the computed score. Must sum to 1.
    private double tfIdfScoreWeight;
    private double categoriesScoreWeight;
    private double namedLocationsScoreWeight;

    private int numWorkers;

    // The output stream for the resulting mapping.
    private DataOutputStream out;

    /**
     * Constructor.
     * @param articles  the articles map (titles to articles) as created in {@link ArticlesFactory}.
     * @param filePath  the full (absolute) path to the file where the data is written.
     */
    public NearestNeighbors(Map<String, Article> articles, String filePath, Map<String, Double> weights)
    {
        ExceptionWrapper.wrap(() ->
        {
            Properties p = new Properties();
            p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));

            this.numWorkers = Integer.parseInt(p.getProperty("wikigir.nearest_neighbors.workers"));

            this.tfIdfThresh = Integer.parseInt(p.getProperty("wikigir.nearest_neighbors.tf_idf_pruning_threshold"));
            this.nlThresh = Integer.parseInt(p.getProperty("wikigir.nearest_neighbors.named_locations_pruning_threshold"));
            this.catThresh = Integer.parseInt(p.getProperty("wikigir.nearest_neighbors.categories_pruning_threshold"));
            this.minSimilarity = Double.parseDouble(p.getProperty("wikigir.nearest_neighbors.min_similarity"));
            this.maxNeighborsPerArticle = Integer.parseInt(p.getProperty("wikigir.nearest_neighbors.max_neighbors"));

            this.out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filePath)));
        });

        this.tfIdfScoreWeight = weights.get(TF_IDF_WEIGHT_KEY);
        this.categoriesScoreWeight = weights.get(CATEGORIES_WEIGHT_KEY);
        this.namedLocationsScoreWeight = weights.get(NAMED_LOCATIONS_WEIGHT_KEY);
        this.articles = articles;
        this.count = 0;
    }

    /**
     * Creates the nearest neighbors mapping (and file).
     */
    public void create()
    {
        if(!ArticlesFactory.getInstance().isCreated())
        {
            throw new RuntimeException("Must create articles (ArticlesFactory.getInstance().create()) first.");
        }

        if(!InvertedIndex.getInstance(InvertedIndex.Type.WORDS_TO_ARTICLES_WITH_COORDINATES).isCreated() ||
           !InvertedIndex.getInstance(InvertedIndex.Type.CATEGORIES_TO_ARTICLES_WITH_COORDINATES).isCreated() ||
           !InvertedIndex.getInstance(InvertedIndex.Type.NAMED_LOCATIONS_TO_ARTICLES_WITH_COORDINATES).isCreated())
        {
            throw new RuntimeException("Must first create the full inverted index structure (for all types).");
        }

        doProcessing();

        ExceptionWrapper.wrap(() -> this.out.close());
    }

    // Segments the work between all workers, launches them then blocks until they are all done.
    private void doProcessing()
    {
        // First, divide the work between all workers (segment the articles map to equal portions).

        int total = this.articles.size();

        int chunkSize = total % this.numWorkers == 0 ? total / this.numWorkers : total / this.numWorkers + 1;

        List<Collection<Map.Entry<String, Article>>> chunks = new ArrayList<>();

        Iterator<Map.Entry<String, Article>> it = this.articles.entrySet().iterator();

        for(int i = 0; i < this.numWorkers; i++)
        {
            Collection<Map.Entry<String, Article>> chunk = new ArrayList<>();
            for(int j = 0; j < chunkSize && it.hasNext(); j++)
            {
                chunk.add(it.next());
            }
            chunks.add(chunk);
        }

        // Next, launch the workers with their respective work load:

        Collection<Thread> threads = new ArrayList<>();

        for(Collection<Map.Entry<String, Article>> chunk : chunks)
        {
            Thread t = new Thread(new NearestNeighborsWorker(chunk));
            threads.add(t);
            t.start();
        }

        // Wait for all threads to complete.
        threads.forEach(t -> ExceptionWrapper.wrap(t::join));
    }

    // Outputs the mapping of a single article (title ID + list of neighboring title IDs and the matching score)
    // to disk. Note that this method is synchronized as it is used concurrently by all workers.
    private synchronized void write(String title, List<Pair<String, Float>> neighbors)
    {
        // Note that all the tests for the existence of the titleID are almost redundant, since all
        // IDs should have matching title, but it is tested regardless to validate the data.
        final StringsIdsMapper titleIdMapper = ArticlesFactory.getInstance().getTitleToIdsMapper();

        ExceptionWrapper.wrap(() ->
        {
            Integer titleId = ArticlesFactory.getInstance().getTitleToIdsMapper().getID(title);

            if(titleId == null)
            {
                return;
            }

            this.out.writeInt(titleId);

            // Filter by titles which have IDs (should be all), transform to IDs list.
            List<Pair<Integer, Float>> existing = neighbors.stream().filter(p -> titleIdMapper.getID(p.v1) != null).
                                                  map(p -> new Pair<>(titleIdMapper.getID(p.v1), p.v2)).
                                                  collect(Collectors.toList());

            this.out.writeInt(existing.size());

            for(Pair<Integer, Float> neighbor : existing)
            {
                this.out.writeInt(neighbor.v1);
                this.out.writeFloat(neighbor.v2);
            }
        });
    }

    // Create a meaningful file name for the nearest neighbors file based on the chosen weights.
    private static String filePath(String basePath, Map<String, Double> weights)
    {
        StringBuilder sb = new StringBuilder(basePath);
        for(Map.Entry<String, Double> weight : weights.entrySet())
        {
            sb.append("_").append(weight.getKey()).append("=").append(Constants.DF.format(weight.getValue()));
        }
        return sb.toString();
    }

    // Read the weights, as written in the configuration file, in this exact order: tf_idf weight, named location
    // weight, then the categories weight. The weight can be expressed as either a double value (e.g. 0.5) and as a
    // literal fraction (e.g. 1/3). We use the literal fraction to allow weight such that 1/3 + 1/3 + 1/3 = 1. The
    // weights must sum exactly to 1.
    private static Map<String, Double> parseWeights(String weightsList)
    {
        Map<String, Double> result = new HashMap<>();

        String[] weights = weightsList.split(",");
        String[] keys = {TF_IDF_WEIGHT_KEY, NAMED_LOCATIONS_WEIGHT_KEY, CATEGORIES_WEIGHT_KEY};

        for(int i = 0; i < keys.length; i++)
        {
            if(weights[i].contains("/"))
            {
                String[] parts = weights[i].split("/");
                result.put(keys[i], Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]));
            }
        }

        if(result.values().stream().mapToDouble(x -> x).sum() != 1)
        {
            throw new RuntimeException("Nearest neighbors weights must sum up to 1.");
        }

        return result;
    }

    /**
     * Creates the nearest neighbors file based on the processed inverted indices and articles mapping.
     */
    public static void createFile()
    {
        final Map<String, Article> articles = ArticlesFactory.getInstance().create();

        InvertedIndex.getInstance(InvertedIndex.Type.WORDS_TO_ARTICLES_WITH_COORDINATES).create();
        InvertedIndex.getInstance(InvertedIndex.Type.CATEGORIES_TO_ARTICLES_WITH_COORDINATES).create();
        InvertedIndex.getInstance(InvertedIndex.Type.NAMED_LOCATIONS_TO_ARTICLES_WITH_COORDINATES).create();

        ExceptionWrapper.wrap(() ->
        {
            String basePath = GetFromConfig.filePath("wikigir.base_path", "wikigir.nearest_neighbors.folder",
                                                     "wikigir.nearest_neighbors.file_name");

            String weightsList = GetFromConfig.stringValue("wikigir.nearest_neighbors.weights");

            Map<String, Double> weights = parseWeights(weightsList);

            System.out.println("Processing - tf-idf weight = " + weights.get(TF_IDF_WEIGHT_KEY) +
                               ", named locations weight = " + weights.get(NAMED_LOCATIONS_WEIGHT_KEY) +
                               ", categories weight = " + weights.get(CATEGORIES_WEIGHT_KEY));

            System.out.println("Note: checkpoints are every " + GENERATION_PRINT_CHECKPOINT + " articles (very slow).");

            String path = filePath(basePath, weights);

            // Don't accidentally delete an existing file. It has to be manually removed.
            if(!new File(path).exists())
            {
                new NearestNeighbors(articles, path, weights).create();
                // Prepare for next iteration.
                System.gc();
            }
        });
    }

    public static void main(String[] args)
    {
        createFile();
    }
}