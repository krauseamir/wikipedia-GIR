package com.krause.wikigir.main.models.general;

import com.krause.wikigir.main.models.articles.dataCreation.ArticlesFactory;
import com.krause.wikigir.main.models.articles.ArticlesSimilarityCalculator;
import com.krause.wikigir.main.models.utils.*;
import com.krause.wikigir.main.models.articles.Article;
import com.krause.wikigir.main.Constants;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.io.*;

import static com.krause.wikigir.main.models.utils.ExceptionWrapper.Action.*;
import static com.krause.wikigir.main.models.general.InvertedIndex.Type.*;
import static com.krause.wikigir.main.Constants.*;

/**
 * Calculates the nearest neighbors for all articles (articles with coordinates, whose score with the article is of
 * some minimal threshold, and up to a bounded number of neighbors). The workload is divided among several workers (in
 * distinct threads), each taking care of a certain segment of the articles mapping. The results are written to disk.
 */
public class NearestNeighbors
{
    private static final String TF_IDF_WEIGHT_KEY = "tf-idf";
    private static final String NAMED_LOCATIONS_WEIGHT_KEY = "named-locations";
    private static final String CATEGORIES_WEIGHT_KEY = "categories";

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
            final StringsIdsMapper titlesToIds = ArticlesFactory.getInstance().getTitleToIdsMapper();

            for(Map.Entry<String, Article> e : this.subset)
            {
                ExceptionWrapper.wrap(() ->
                {
                    ProgressBar.mark(processed, Constants.NUMBER_OF_ARTICLES);

                    List<Pair<Integer, Float>> result = getNearestNeighborsWithLocations(e.getValue());

                    NearestNeighbors.this.write(titlesToIds.getID(e.getKey()), result);
                },
                NOTIFY_LONG);
            }
        }

        // Given an article:
        // 1. Prune the articles corpus to get neighbor candidates (with coordinates).
        // 2. Calculate the matching score for each neighbor, filter those whose score is too low.
        // 3. Sort the neighbors by matching score, in descending order, and trim the list if necessary.
        // Note the pruning is done on an inverted index containing only pages that have coordinates (other pages are
        // irrelevant for the purpose of assessing locations).
        private List<Pair<Integer, Float>> getNearestNeighborsWithLocations(Article article)
        {
            List<Pair<String, Float>> result = new ArrayList<>();

            Set<String> pruned = getPrunedTitles(article);

            for(String title : pruned)
            {
                Article candidate = NearestNeighbors.this.articles.get(title);

                // Safety (shouldn't happen): all articles have a mapping. The index only has articles with coordinates.
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

            return verifyAndTransformData(article.getTitle(), result);
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
        // the values k1 = 2, k2 = k3 = 1 (i.e., share either at least two top words, a named location, or a category).
        private Set<String> getPrunedTitles(Article a)
        {
            Collection<String> byWords = new ArrayList<>();
            Collection<String> byCategories = new ArrayList<>();
            Collection<String> byNamedLocations = new ArrayList<>();

            // We check if the assigned weight is greater than 0, since there is no point in considering candidate
            // articles using a property that is not used in the final scoring.
            if(NearestNeighbors.this.tfIdfScoreWeight > 0)
            {
                InvertedIndex index = InvertedIndex.getInstance(WORDS_TO_ARTICLES_WITH_COORDINATES);
                byWords = index.prune(a, this.pruner, NearestNeighbors.this.tfIdfPruningThreshold);
            }

            if(NearestNeighbors.this.namedLocationsScoreWeight > 0)
            {
                InvertedIndex index = InvertedIndex.getInstance(NAMED_LOCATIONS_TO_ARTICLES_WITH_COORDINATES);
                byNamedLocations = index.prune(a, this.pruner, NearestNeighbors.this.nlPruningThreshold);
            }

            if(NearestNeighbors.this.categoriesScoreWeight > 0)
            {
                InvertedIndex index = InvertedIndex.getInstance(CATEGORIES_TO_ARTICLES_WITH_COORDINATES);
                byCategories = index.prune(a, this.pruner, NearestNeighbors.this.catPruningThreshold);
            }

            Set<String> pruned = new HashSet<>();
            pruned.addAll(byWords);
            pruned.addAll(byCategories);
            pruned.addAll(byNamedLocations);

            return pruned;
        }

        // Perform additional safety verifications on the results, and transforms all neighbors string titles to IDs.
        // The two safety verifications are that title indeed have IDs, and that one of the neighbors isn't the article
        // itself (then, using its coordinates is technically valid, as we do not "know" this named location is the
        // truth value, but is avoided nonetheless). Note that this also cannot technically happen since the pruning
        // method in the inverted index explicitly removes the title from its matched pruned candidate neighbors.
        // But it doesn't hurt (other than wasted CPU cycles...) to double verify for data integrity :)
        private List<Pair<Integer, Float>> verifyAndTransformData(String title, List<Pair<String, Float>> neighbors)
        {
            // Note that all the tests for the existence of the titleID are almost redundant, since all
            // IDs should have matching title, but it is tested regardless to validate the data.
            final StringsIdsMapper titleIdMapper = ArticlesFactory.getInstance().getTitleToIdsMapper();

            Integer titleId = ArticlesFactory.getInstance().getTitleToIdsMapper().getID(title);

            if(titleId == null)
            {
                return null;
            }

            List<Pair<Integer, Float>> verified = new ArrayList<>();
            for(Pair<String, Float> neighbor : neighbors)
            {
                // Only write neighbors which have IDs - should be all of them.
                if(titleIdMapper.getID(neighbor.v1) == null)
                {
                    continue;
                }

                // Handle the odd case where the same article appears in its contained named locations - then, using
                // its coordinates is technically valid (as we do not "know" this named location is the truth value),
                // but is avoided nonetheless.
                if(title.equals(neighbor.v1))
                {
                    continue;
                }

                verified.add(new Pair<>(titleIdMapper.getID(neighbor.v1), neighbor.v2));
            }

            return verified;
        }
    }

    // Counts the total number of articles processed, updated by the workers.
    private int[] processed;

    // The mapping from titles to Article objects created by ArticlesFactory.
    private final Map<String, Article> articles;

    // The minimal number of top-words collisions that an articles needs in order to be considered a valid candidate
    // to be a neighbor. Collisions = the number of top-words shared by both it and the original articles.
    private final int tfIdfPruningThreshold;

    // The minimal number of named locations collisions that an articles needs in order to be considered a valid
    // candidate to be a neighbor. Collisions = number of named locations shared by it and the original articles.
    private final int nlPruningThreshold;

    // The minimal number of categories collisions that an articles needs in order to be considered a valid
    // candidate to be a neighbor. Collisions = number of categories shared by it and the original articles.
    private final int catPruningThreshold;

    // The minimal similarity score required for a neighbor to be considered valuable. Note that this value only
    // determines whether or not a neighbor is eligible to be stored and kept, but when using the nearest neighbors
    // for some article, we can filter out neighbors based on a higher minimal threshold score.
    private final double minSimilarity;

    // The maximal number of neighbors -which are stored- per each article.
    private final int maxNeighborsPerArticle;

    // Weights of the similarity component (words, named locations, categories) in the computed score. Must sum to 1.
    private final double tfIdfScoreWeight;          // In the article - alpha
    private final double namedLocationsScoreWeight; // In the article - beta
    private final double categoriesScoreWeight;     // In the article - gamma

    // The number of worker threads to instantiate.
    private final int numWorkers;

    // The output stream for the resulting mapping.
    private DataOutputStream out;

    /**
     * Constructor.
     * @param fp        the full (absolute) path to the file where the data is written.
     */
    public NearestNeighbors(String fp, Map<String, Double> weights)
    {
        if(!ArticlesFactory.getInstance().isCreated())
        {
            throw new RuntimeException("Must create articles (via ArticlesFactory) before instantiating.");
        }

        this.articles = ArticlesFactory.getInstance().getArticles();

        this.numWorkers = GetFromConfig.intValue("wikigir.nearest_neighbors.workers");
        this.minSimilarity = GetFromConfig.doubleValue("wikigir.nearest_neighbors.min_similarity");
        this.maxNeighborsPerArticle = GetFromConfig.intValue("wikigir.nearest_neighbors.max_neighbors");

        this.tfIdfPruningThreshold = GetFromConfig.intValue("wikigir.nearest_neighbors.tf_idf_pruning_threshold");
        this.nlPruningThreshold = GetFromConfig.intValue("wikigir.nearest_neighbors.named_locations_pruning_threshold");
        this.catPruningThreshold = GetFromConfig.intValue("wikigir.nearest_neighbors.categories_pruning_threshold");

        ExceptionWrapper.wrap(() -> this.out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fp))));

        this.tfIdfScoreWeight = weights.get(TF_IDF_WEIGHT_KEY);
        this.categoriesScoreWeight = weights.get(CATEGORIES_WEIGHT_KEY);
        this.namedLocationsScoreWeight = weights.get(NAMED_LOCATIONS_WEIGHT_KEY);

        this.processed = new int[]{0};
    }

    /**
     * Creates the nearest neighbors mapping (and file).
     */
    public void create()
    {
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
    private synchronized void write(int titleID, List<Pair<Integer, Float>> neighbors)
    {
        ExceptionWrapper.wrap(() ->
        {
            this.out.writeInt(titleID);
            this.out.writeInt(neighbors.size());
            for(Pair<Integer, Float> neighbor : neighbors)
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
    private static Map<String, Double> parseWeights()
    {
        Map<String, Double> result = new HashMap<>();

        String weightsList = GetFromConfig.stringValue("wikigir.nearest_neighbors.weights");
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
        String folderPath = GetFromConfig.filePath("wikigir.base_path", "wikigir.nearest_neighbors.folder");
        String basePath = folderPath + GetFromConfig.stringValue("wikigir.nearest_neighbors.file_name");

        Map<String, Double> weights = parseWeights();

        System.out.println("Processing - tf-idf weight = " + DF.format(weights.get(TF_IDF_WEIGHT_KEY)) +
                           ", named locations weight = " + DF.format(weights.get(NAMED_LOCATIONS_WEIGHT_KEY)) +
                           ", categories weight = " + DF.format(weights.get(CATEGORIES_WEIGHT_KEY)));

        System.out.println("Note: Creating this file takes a long time (perhaps even several days).");

        String completePath = filePath(basePath, weights);

        if(!new File(folderPath).exists())
        {
            ExceptionWrapper.wrap(() -> Files.createDirectories(Paths.get(folderPath)));
        }

        // Don't accidentally delete an existing file. It has to be manually removed.
        if(!new File(completePath).exists())
        {
            new NearestNeighbors(completePath, weights).create();
        }
        else
        {
            System.out.println("Already created on disk.");
        }
    }

    public static void main(String[] args)
    {
        System.out.println("Creating (or loading from disk) the dictionary, full articles data and inverted indices.");
        Dictionary.getInstance().create();
        ArticlesFactory.getInstance().create();
        InvertedIndex.getInstance(InvertedIndex.Type.WORDS_TO_ARTICLES_WITH_COORDINATES).create();
        InvertedIndex.getInstance(InvertedIndex.Type.CATEGORIES_TO_ARTICLES_WITH_COORDINATES).create();
        InvertedIndex.getInstance(InvertedIndex.Type.NAMED_LOCATIONS_TO_ARTICLES_WITH_COORDINATES).create();

        System.out.println("Done. Creating the nearest neighbor file.");
        createFile();
    }
}