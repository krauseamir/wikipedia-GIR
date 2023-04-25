package com.krause.wikigir.main.models.general;

import java.util.*;
import java.io.*;

import com.krause.wikigir.main.Constants;
import com.krause.wikigir.main.models.articles.dataCreation.CleanTextXMLParser;
import com.krause.wikigir.main.models.utils.*;

/**
 * Contains a mapping of all the words found in all wiki articles.
 * The words are english-letter (and digits) only, lower-cased, without any punctuation marks, etc.
 * The dictionary contains an integer identifier for each string word (for lower memory consumption
 * when storing the dictionary), as well as the document frequency for vector-space-like IR.
 */
public class Dictionary
{
    // 0 = scan all articles in the xml file.
    private static final int ARTICLES_LIMIT = 0;

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

    private final String filePath;

    private final StringsIdsMapper wordsIdsMapping;

    // Document frequencies for words, used to calculate the IDF component.
    private final Map<Integer, Integer> df;

    private final BlockingThreadFixedExecutor executor;

    private int totalDocuments;
    private long totalWords;

    private boolean created;

    // Private constructor.
    private Dictionary()
    {
        this.filePath = GetFromConfig.filePath("wikigir.base_path", "wikigir.dictionary.folder",
                                               "wikigir.dictionary.file_name");

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
        int[] processed = {0};
        int[] nextId = {1};

        WikiXMLArticlesExtractor.extract(CleanTextXMLParser::new,
            (parser, text) ->
            {
                this.executor.execute(() ->
                {
                    ExceptionWrapper.wrap(() ->
                    {
                        ProgressBar.mark(processed, Constants.NUMBER_OF_ARTICLES);
                        parser.parse(text);

                        List<String> words = TextTokenizer.tokenize((String)parser.getResult().get(
                                                        CleanTextXMLParser.CLEAN_TEXT_KEY) , true);

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
                        }
                    },
                    ExceptionWrapper.Action.NOTIFY_LONG);
                });
            },
            ARTICLES_LIMIT);

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