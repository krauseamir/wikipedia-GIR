package com.krause.wikigir.main.models.categories.dataCreation;

import com.krause.wikigir.main.models.utils.BlockingThreadFixedExecutor;
import com.krause.wikigir.main.Constants;

import com.krause.wikigir.main.models.utils.ExceptionWrapper;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.io.*;

/**
 * Responsible for extracting all categories from the predefined wikipedia categories SQL file
 * (a different file from the main wikipedia pages XML file), as well as extracting all categories
 * from the main XML file using a designated xml parser. A queue is formed from all of these
 * categories, and a thread pool keep invoking workers to send API requests, specified under the
 * WikiMedia API specifications, to fetch all subcategories.
 *
 * The subcategories might need to be fetched in multiple iterations, if their number, per some
 * category, exceeds 500. In this case a "cmcontinue" flag is appended to the request, specifying
 * the starting category in the requested response. In addition, in order to not flood the servers,
 * a "slowdown" mechanism is dispatched when wikipedia returns an exception (of any kind).
 *
 * The categories, along their subcategories are eventually saved to sequentially created files
 * under a predefined directory. They are parsed by the {@link CategoryNamesGraph} class.
 *
 * @author Amir Krause
 */
public class CategoriesAPIQuerier
{
    // A small interval to wait in each fetching request (to be gentle on the wiki servers).
    private static final int WAIT_INTERVAL = 10;

    // Note the "%s" which is replaced when actually constructing a request with a category.
    private static final String REQUEST = "https://en.wikipedia.org/w/api.php?action=query&" +
                                          "list=categorymembers&cmtype=subcat&cmlimit=500&" +
                                          "cmcontinue=%s&cmtitle=Category:%s&format=json";

    // When wiki servers start returning 429s (too many requests), each requests, for the next cycle
    // of threads, has an additional waiting time of this value (in milliseconds).
    private static final int SLOW_DOWN_PERIOD = 1000;

    // When the main loop, querying the queue detects the queue is empty, instead of immediately
    // exiting the loop, this number of "grace" iterations is attempted, to give threads, currently
    // being processed by extractors, to add items to the queue. This can happen if some thread's
    // API request is denied (429), or malformed (any other HTTP error code), or that the query
    // simply has too many results and needs to be continued. The grace iterations are calculated
    // in such a way that would give the main loop at most x seconds before terminating, when
    // it is detected as completely empty.
    private static final int GRACE_ITERATIONS = (10 * 60 * 1000) / WAIT_INTERVAL;

    // The API request and parsing worker class.
    private class ExtractionWorker implements Runnable
    {
        private int attempts;
        private boolean wait;
        private String category;

        // When a category with too many subcategories is queried, repeated API requests are made
        // to the wikipedia server with a 'cmcontinue' flag, denoting from where to continue the
        // results. If this flag is empty, the results will be the first batch of results.
        private String cmcontinue;

        private ExtractionWorker(int attempts, String category, String cmcontinue)
        {
            this.attempts = attempts;
            this.category = category;
            this.wait = false;
            this.cmcontinue = cmcontinue;
        }

        public void run()
        {
            String response = getResponse();

            if(StringUtils.isEmpty(response))
            {
                synchronized(CategoriesAPIQuerier.this)
                {
                    // Lower the number of attempts so this request would be indefinitely added.
                    if(--this.attempts > 0)
                    {
                        // If the wait value was true, return it to queue with false.
                        CategoriesAPIQuerier.this.queue.add(this.setWait(false));
                    }
                }

                return;
            }

            List<String> subcategories = getSubCategories(response);

            synchronized(CategoriesAPIQuerier.this)
            {
                try
                {
                    writeCategoryToFile(subcategories);
                }
                catch(Exception e)
                {
                    // We need to stop the program, there's a data corruption that will make
                    // it difficult to parse the categories file for future references.
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }

        public ExtractionWorker setWait(boolean val)
        {
            this.wait = val;
            return this;
        }

        // Retrieves the raw, unparsed API response. It looks something like this:
        // {
        //  "batchcomplete":"",
        //  "query":{
        //      "categorymembers":[
        //          {"pageid":13078054,"ns":14,"title":"Category:Fields of history"},
        //          {"pageid":4303236,"ns":14,"title":"Category:History-related lists"},
        //          {"pageid":2032160,"ns":14,"title":"Category:History by ethnic group"},
        //          {"pageid":18985014,"ns":14,"title":"Category:History by location"},
        //          ...
        //      ]
        //  }
        // }
        private String getResponse()
        {
            try
            {
                if(this.wait)
                {
                    Thread.sleep(SLOW_DOWN_PERIOD);
                }

                // Note that we give the query both the category to be queried and the cmcontinue
                // flag which is not empty in cases where a previous extractor encountered a
                // category with too many results for a single API call, thus re-queuing the
                // category with a flag indicating where to pick up the responses, in accordance
                // with the wikipedia querying guidelines, as specified here:
                // https://www.mediawiki.org/wiki/API:Query#Example_4:_Continuing_queries
                HttpURLConnection con = (HttpURLConnection)new URL(String.format(REQUEST,
                                        this.cmcontinue, this.category)).openConnection();

                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String response = in.readLine();
                in.close();

                return response;
            }
            catch(Exception e)
            {
                // If an exception is thrown, it means that possibly, the wikipedia server is
                // telling us "slow down" (cannot be sure, but just in case). Setting the waits
                // counter to some value X means the next X worker threads will start their
                // operation with a delay, temporarily dramatically decreasing request rates.
                // Note we do this even when the status code is not explicitly 429, for caution.
                synchronized(CategoriesAPIQuerier.this)
                {
                    CategoriesAPIQuerier.this.slowDown();
                }
            }

            return null;
        }

        // Utilizes a Gson object (for json string parsing) to fetch all needed information from
        // the wikipedia API response. This information includes the list of subcategories (disregarding
        // additional data supplied with them, keeping only the names) and - when present - the cmcontinue
        // flag for the next request with the same category name, if the number of subcategories is
        // over the maximal limit.
        @SuppressWarnings("unchecked")
        private List<String> getSubCategories(String response)
        {
            List<String> subcategories = new ArrayList<>();

            Map<String, ?> map = new HashMap<>();

            try
            {
                map = (Map<String, ?>)CategoriesAPIQuerier.this.gson.fromJson(response, Map.class);
            }
            // If the response could not be parsed, just ignore it.
            catch(Exception ignored) {}

            Map<String, ?> query = (Map<String, ?>)map.get("query");
            if(query == null)
            {
                return new ArrayList<>();
            }

            List<Map<String, ?>> list = (List<Map<String, ?>>)query.get("categorymembers");
            if(list == null)
            {
                return new ArrayList<>();
            }

            for(Map<String, ?> subcategoryData : list)
            {
                String categoryWithPrefix = (String)subcategoryData.get("title");
                subcategories.add(categoryWithPrefix.substring("Category:".length()));
            }

            // Handle a (maybe) possible situation in which the category appears as its own subcategory.
            if(!subcategories.isEmpty())
            {
                if(subcategories.get(0).equals(this.category))
                {
                    subcategories.remove(0);
                }
            }

            // Now, check if a category had too many subcategories and another API call should be
            // made, starting where this query's results have ended.
            Map<String, ?> continueParameter = (Map<String, ?>)map.get("continue");
            if(continueParameter != null)
            {
                String cm = (String)continueParameter.get("cmcontinue");
                if(cm != null)
                {
                    // Add this category to the queue with the relevant continue flag.
                    synchronized(CategoriesAPIQuerier.this)
                    {
                        CategoriesAPIQuerier.this.queue.add(CategoriesAPIQuerier.this.createWorker(this.category, cm));
                    }
                }
            }

            return subcategories;
        }

        // Writes the category along its subcategories to file and logs the time on checkmarks.
        private void writeCategoryToFile(List<String> subcategories) throws Exception
        {
            String text = counter + "\t" + this.category.replace("+", " ") +
                          "\t[" + String.join("|||", subcategories) + "]";

            writer.write(text);

            // Log the passing of another checkmark and record the time + rate.
            if (++counter % 1000 == 0)
            {
                long time = System.currentTimeMillis();
                int rate = (int) (1000 / ((time - timeInLastCheckpoint) / 1000));
                CategoriesAPIQuerier.this.timeInLastCheckpoint = time;
                System.out.println("Parsed " + CategoriesAPIQuerier.this.counter + ". Rate: " + rate +
                                   " categories/sec. Queue size: " + CategoriesAPIQuerier.this.queue.size() +
                                   ". Time: " + new Date().toString().substring(4, 19));
            }
        }
    }

    private Properties p;

    // Holds the categories to be queried, along with an "attempts" flag that is reduced at each try.
    private Queue<ExtractionWorker> queue;

    // Manages the threads sending API requests.
    private BlockingThreadFixedExecutor executor;

    // How many categories were parsed so far.
    private int counter;

    // When this value is set to X, the next X threads will hold a little more.
    private AtomicInteger waitCount;

    // A utility used for writing the subcategories data to disk.
    private final MultipleFilesWriter writer;

    // Used for parsing the API response.
    private Gson gson;

    // Used for logging.
    private long timeInLastCheckpoint;

    /**
     * Constructor.
     * @throws Exception if loading the properties or categories from SQL/XML failed.
     */
    public CategoriesAPIQuerier() throws Exception
    {
        this.p = new Properties();
        this.p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));

        int catsPerFile = Integer.parseInt(this.p.getProperty("wikigir.categories.web_api.text_files.per_file"));

        String baseFilesPath = this.p.getProperty("wikigir.base_path") +
                               this.p.getProperty("wikigir.categories.folder") +
                               this.p.getProperty("wikigir.categories.web_api.text_files.folder") +
                               this.p.getProperty("wikigir.categories.web_api.text_files.file_prefix");

        this.writer = new MultipleFilesWriter(catsPerFile, baseFilesPath);

        this.counter = 1;
        this.executor = new BlockingThreadFixedExecutor();

        this.queue = initQueue();

        this.timeInLastCheckpoint = System.currentTimeMillis();
        this.waitCount = new AtomicInteger(0);
        this.gson = new Gson();
    }

    /**
     * Iterates over the queue of categories, as long as it is not empty, and launches API
     * requests via workers. Note that even when the queue is empty, the iteration continues
     * for a predefined number of iterations, to allow for current workers to add new
     * requests to queue.
     * @throws Exception if closing the write stream failed.
     */
    public void execute() throws Exception
    {
        int graceIterations = GRACE_ITERATIONS;
        while(!this.queue.isEmpty() || graceIterations > 0)
        {
            ExtractionWorker e = null;

            // Issue a new worker - if the connections waits is larger than zero, it means the
            // worker will hold for a certain interval before sending the request. The waits is
            // set to some value once we detect an exception from wikipedia (this is done to
            // create a fast response (possibly over-responding) to wikipedia servers denials.
            synchronized(this)
            {
                if(!this.queue.isEmpty())
                {
                    e = this.queue.poll();
                }
            }

            if(e != null)
            {
                graceIterations = GRACE_ITERATIONS;

                // Note this cannot be inside the synchronized block since the executor
                // is blocking, which would cause a deadlock situation with the extractors.
                this.executor.execute(e.setWait(this.waitCount.get() > 0));
                this.waitCount.set(Math.max(this.waitCount.get() - 1, 0));
            }
            else
            {
                graceIterations--;
            }

            ExceptionWrapper.wrap(() -> Thread.sleep(WAIT_INTERVAL), ExceptionWrapper.Action.IGNORE);
        }

        this.executor.waitForTermination();
        this.writer.close();

        // Terminate explicitly in case the threads pool is stranded for some reason.
        System.exit(1);
    }

    // Fuses the unique category names fetched from the raw SQL file with the unique category names from the raw
    // Wikipedia XML file, then returns a queue of workers used to query the Wikipedia API for subcategories.
    private Queue<ExtractionWorker> initQueue()
    {
        Set<String> categories = new CategoriesFromSQLFile().parse();
        categories.addAll(new CategoryNamesFromXML().getAllCategoriesInXml());
        return categories.stream().map(cat -> createWorker(cat, "")).collect(Collectors.toCollection(LinkedList::new));
    }

    private ExtractionWorker createWorker(String category, String cmcontinue)
    {
        int maxAttempts = Integer.parseInt(p.getProperty("wikigir.categories.web_api.max_attempts_per_category"));
        return new ExtractionWorker(maxAttempts, category, cmcontinue);
    }

    // Sets a waiting threads count to X, which causes the next X threads to wait a little more.
    private synchronized void slowDown()
    {
        this.waitCount.set(Integer.parseInt(this.p.getProperty("wikigir.categories.web_api.slowdown_threads_count")));
    }

    public static void main(String[] args) throws Exception
    {
        new CategoriesAPIQuerier().execute();
    }
}