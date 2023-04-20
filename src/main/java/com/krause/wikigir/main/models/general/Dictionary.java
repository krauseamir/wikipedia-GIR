package com.krause.wikigir.main.models.general;

import java.util.*;
import java.io.*;

import com.krause.wikigir.main.models.utils.BlockingThreadFixedExecutor;
import com.krause.wikigir.main.models.articles.CleanTextXmlParser;
import com.krause.wikigir.main.models.utils.CustomSerializable;
import com.krause.wikigir.main.models.utils.ExceptionWrapper;
import com.krause.wikigir.main.models.utils.StringsIdsMapper;
import com.krause.wikigir.main.Constants;

/**
 * Contains a mapping of all the words found in all wiki articles.
 * The words are english-letter (and digits) only, lower-cased, without any punctuation marks, etc.
 * The dictionary contains an integer identifier for each string word (for lower memory consumption
 * when storing the dictionary), as well as the document frequency for vector-space-like IR.
 */
public class Dictionary
{
    // 0 = scan all articles in the xml file.
    private static final int PAGES_LIMIT = 0;

    // The singleton instance.
    private static Dictionary dictionary;

    /**
     * Singleton.
     * @return the singleton {@link Dictionary} object.
     */
    public static synchronized Dictionary getInstance()
    {
        if(dictionary == null)
        {
            dictionary = new Dictionary();
        }

        return dictionary;
    }

    private String filePath;

    private StringsIdsMapper wordsIdsMapping;

    // Document frequencies for words, used to calculate the IDF component.
    private Map<Integer, Integer> df;

    private int totalDocuments;
    private long totalWords;

    private BlockingThreadFixedExecutor executor;

    private boolean created;

    // Private constructor.
    private Dictionary()
    {
        try
        {
            Properties p = new Properties();
            p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));

            this.filePath = p.getProperty("wikigir.base_path") +
                            p.getProperty("wikigir.dictionary.folder") +
                            p.getProperty("wikigir.dictionary.file_name");
        }
        catch(Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }

        this.wordsIdsMapping = new StringsIdsMapper();
        this.df = new HashMap<>();

        this.totalDocuments = 0;
        this.totalWords = 0;

        this.executor = new BlockingThreadFixedExecutor();

        this.created = false;
    }

    /**
     * Creates the dictionary by parsing the entire wiki xml file, or loads it from disk.
     */
    public void create()
    {
        if(new File(this.filePath).exists())
        {
            new Serializer().deserialize();
        }
        else
        {
            readFromXml();
            new Serializer().serialize();
        }

        this.created = true;
    }

    public Integer wordToId(String word)
    {
        return this.wordsIdsMapping.getID(word);
    }

    public String idToWord(int id)
    {
        return this.wordsIdsMapping.getString(id);
    }

    public double logIdf(Integer id)
    {
        if(id == null)
        {
            return Math.log10(this.totalDocuments);
        }

        Integer df = this.df.get(id);

        if(df == null)
        {
            return Math.log10(this.totalDocuments);
        }

        return Math.log10((double)this.totalDocuments / (double)df);
    }

    public int size()
    {
        return this.wordsIdsMapping.size();
    }

    public boolean isCreated()
    {
        return this.created;
    }

    private void readFromXml()
    {
        int[] counter = {0};
        int[] nextId = {1};

        WikiXmlArticlesExtractor.extract(CleanTextXmlParser::new,
            (parser, text) ->
            {
                this.executor.execute(() ->
                {
                    ExceptionWrapper.wrap(() ->
                    {
                        parser.parse(text);

                        List<String> words = TextTokenizer.tokenize((String)parser.getResult().get(
                                                        CleanTextXmlParser.CLEAN_TEXT_KEY) , true);

                        words = TextTokenizer.filterStopWords(words);

                        this.totalDocuments++;
                        this.totalWords += words.size();

                        Set<String> uniquePageWords = new HashSet<>(words);

                        // Make sure different threads behave nicely.
                        synchronized(Dictionary.this)
                        {
                            uniquePageWords.forEach(word ->
                            {
                                if(this.wordsIdsMapping.getID(word) == null)
                                {
                                    this.wordsIdsMapping.add(word, nextId[0]++);
                                }

                                int id = this.wordsIdsMapping.getID(word);
                                this.df.putIfAbsent(id, 0);
                                this.df.put(id, this.df.get(id) + 1);
                            });

                            if(++counter[0] % 10_000 == 0)
                            {
                                System.out.println("Passed " + counter[0] + " articles, dictionary" +
                                                   " now contains " + this.df.size() + " words.");
                            }
                        }
                    },
                    ExceptionWrapper.Action.NOTIFY_LONG);
                });
            },
            PAGES_LIMIT);

        this.executor.waitForTermination();
    }

    private class Serializer implements CustomSerializable
    {
        @Override
        public String filePath()
        {
            return Dictionary.this.filePath;
        }

        @Override
        public void customSerialize(DataOutputStream out) throws IOException
        {
            out.writeInt(Dictionary.this.totalDocuments);
            out.writeLong(Dictionary.this.totalWords);

            List<String> words = new ArrayList<>(Dictionary.this.wordsIdsMapping.getStrings());
            List<Integer> ids = new ArrayList<>(Dictionary.this.wordsIdsMapping.getIDs());

            out.writeInt(Dictionary.this.wordsIdsMapping.size());
            for(int i = 0; i < Dictionary.this.wordsIdsMapping.size(); i++)
            {
                out.writeUTF(words.get(i));
                out.writeInt(ids.get(i));
            }

            out.writeInt(Dictionary.this.df.size());
            for (Map.Entry<Integer, Integer> e : Dictionary.this.df.entrySet())
            {
                out.writeInt(e.getKey());
                out.writeInt(e.getValue());
            }
        }

        @Override
        public void customDeserialize(DataInputStream in) throws IOException
        {
            Dictionary.this.totalDocuments = in.readInt();
            Dictionary.this.totalWords = in.readLong();

            int wordsToIds = in.readInt();
            for(int i = 0; i < wordsToIds; i++)
            {
                Dictionary.this.wordsIdsMapping.add(in.readUTF(), in.readInt());
            }

            int dfs = in.readInt();
            for(int i = 0; i < dfs; i++)
            {
                Dictionary.this.df.put(in.readInt(), in.readInt());
            }
        }
    }

    public static void main(String[] args)
    {
        new Dictionary().create();
    }
}