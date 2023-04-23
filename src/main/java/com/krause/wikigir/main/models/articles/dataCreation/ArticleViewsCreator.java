package com.krause.wikigir.main.models.articles.dataCreation;

import com.krause.wikigir.main.models.general.WikiXMLArticlesExtractor;
import com.krause.wikigir.main.models.general.XMLParser;
import com.krause.wikigir.main.models.utils.*;
import com.google.gson.Gson;

import java.nio.charset.CharacterCodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.util.*;
import java.io.*;

/**
 * Queries the wikimedia server for article views for all existing titles in the enwiki XML file. The result
 * is subsequently stored in disk. The querying is done slowly to not overload the wikipedia servers and
 * is expected to take approximately 1.5 days.
 *
 * Note! In Java 11, when running this class' main method, need to include the following VM option:
 * -Dhttps.protocols=TLSv1,TLSv1.1,TLSv1.2
 * (see stackoverflow.com/questions/52574050/javax-net-ssl-sslexception-no-psk-available-unable-to-resume).
 */
public class ArticleViewsCreator
{
    // 0 = all pages.
    private static final int ARTICLES_LIMIT = 0;

    // The maximal number of attempts to try and query wikipedia.
    private static final int MAX_ATTEMPTS = 5;

    private static final int DELAY = 100;

    // If an exception was detected in one of the requests,
    private static final long SLOWDOWN_PERIOD_IN_MS = 10 * 1000;

    private static final String REQUEST = "https://wikimedia.org/api/rest_v1/metrics/pageviews/per-article/" +
                                          "en.wikipedia/all-access/all-agents/%s/monthly/2019010100/2020010100";

    private static final String ENCODING = "UTF-8";

    private final String filePath;

    private final Map<String, int[]> viewsMap;

    private Queue<Pair<String, Integer>> queue;

    private long slowDownStart;

    private final Gson gson;

    /**
     * Constructor.
     */
    public ArticleViewsCreator()
    {
        this.filePath = GetFromConfig.filePath("wikigir.base_path", "wikigir.articles.folder",
                                               "wikigir.articles.article_views.file_name");

        this.viewsMap = new HashMap<>();
        this.gson = new Gson();
    }

    /**
     * Either loads the page views from disk, or initiates a querying process via the wikimedia servers.
     */
    public Map<String, int[]> create(boolean retryMissing)
    {
        if(new File(this.filePath).exists())
        {
            new Serializer().deserialize();
        }

        if(retryMissing || !new File(this.filePath).exists())
        {
            loadTitlesFromXml();
            queryTitles();
            new Serializer().serialize();
        }

        return this.viewsMap;
    }

    // Loads all titles from the enwiki XML file and stores them to disk.
    private void loadTitlesFromXml()
    {
        this.queue = new LinkedList<>();

        System.out.println("Inserting titles into queue:");

        int[] read = {0};
        // Don't perform any parsing of the page - we just need the title.
        WikiXMLArticlesExtractor.extract(() -> new XMLParser() { @Override public void parse(StringBuilder sb) {}},
            (parser, text) ->
                ExceptionWrapper.wrap(() ->
                {
                    parser.addTitleToResult(text);
                    if(this.viewsMap.get(parser.getTitle()) == null)
                    {
                        this.queue.add(new Pair<>(parser.getTitle(), MAX_ATTEMPTS));
                    }

                    if(++read[0] % 100_000 == 0)
                    {
                        System.out.println("Read " + read[0] + " titles. In queue: " + this.queue.size());
                    }
                }, ExceptionWrapper.Action.NOTIFY_LONG),
            ARTICLES_LIMIT);

        System.out.println("Done loading titles (" + this.queue.size() + ").");
    }

    // Initiates a querying process via workers of the titles in the queue. A title that failed to be fetched
    // due to some unknown exception (usually a 429 "too many requests") is retried up to a given number of
    // times by being re-inserted into the queue.
    @SuppressWarnings("unchecked")
    private void queryTitles()
    {
        this.slowDownStart = 0;
        int[] counter = {0};

        // The outer while loop runs until all items in the queue are parsed, including retries which might
        // be inserted *after* the queue has completely been cleared, by remaining workers. The inner while
        // loop runs on the current queue until it is emptied.
        while(!this.queue.isEmpty())
        {
            System.out.println("Running outer while loop for queue depletion.");

            BlockingThreadFixedExecutor executor = new BlockingThreadFixedExecutor();

            while(!this.queue.isEmpty())
            {
                Pair<String, Integer> p;
                final int queueSize;
                synchronized(this)
                {
                    p = this.queue.poll();
                    queueSize = this.queue.size();
                }

                boolean inSlowdown = System.currentTimeMillis() < this.slowDownStart + SLOWDOWN_PERIOD_IN_MS;
                ExceptionWrapper.wrap(() -> Thread.sleep(inSlowdown ? DELAY * 2 : DELAY));

                executor.execute(() ->
                {
                    try
                    {
                        HttpURLConnection con = (HttpURLConnection) new URL(String.format(REQUEST,
                                                URLEncoder.encode(p.v1, ENCODING))).openConnection();

                        try(BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream())))
                        {
                            String response = in.readLine();
                            Map<String, ?> json = (Map<String, ?>)gson.fromJson(response, Map.class);
                            List<Map<String, ?>> items = (List<Map<String, ?>>)json.get("items");

                            int[] views = new int[items.size()];
                            int index = 0;
                            for(Map<String, ?> item : items)
                            {
                                views[index++] = ((Double)item.get("views")).intValue();
                            }

                            synchronized(this.viewsMap)
                            {
                                this.viewsMap.put(p.v1, views);

                                if(++counter[0] % 1000 == 0)
                                {
                                    System.out.println("Fetched " + counter[0] + " titles (queue size ~ " +
                                                        queueSize + "), in slowdown? = " + inSlowdown + ".");
                                }
                            }
                        }
                    }
                    // In these cases there's nothing to be done, do not retry.
                    catch(CharacterCodingException | FileNotFoundException ignore) {}
                    catch(Exception e)
                    {
                        // Just in case, slow down the requests for a predefined time.
                        this.slowDownStart = System.currentTimeMillis();

                        // Retry the title.
                        if(p.v2 > 1)
                        {
                            p.v2--;
                            synchronized(this)
                            {
                                ArticleViewsCreator.this.queue.add(p);
                            }
                        }
                    }
                });
            }

            executor.waitForTermination();
        }
    }

    private class Serializer implements CustomSerializable
    {
        @Override
        public String filePath()
        {
            return ArticleViewsCreator.this.filePath;
        }

        @Override
        public void customSerialize(DataOutputStream out) throws IOException
        {
            out.writeInt(ArticleViewsCreator.this.viewsMap.size());
            for(Map.Entry<String, int[]> e : ArticleViewsCreator.this.viewsMap.entrySet())
            {
                out.writeUTF(e.getKey());
                out.writeInt(e.getValue().length);
                for(int i = 0; i < e.getValue().length; i++)
                {
                    out.writeInt(e.getValue()[i]);
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
                int[] views = new int[in.readInt()];
                for(int j = 0; j < views.length; j++)
                {
                    views[j] = in.readInt();
                }

                ArticleViewsCreator.this.viewsMap.put(title, views);
            }
        }
    }

    // Must include the following VM option (Java 11): -Dhttps.protocols=TLSv1,TLSv1.1,TLSv1.2
    public static void main(String [] args)
    {
        new ArticleViewsCreator().create(true);
    }
}