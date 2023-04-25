package com.krause.wikigir.main.models.articles.dataCreation;

import com.krause.wikigir.main.Constants;
import com.krause.wikigir.main.models.general.Dictionary;
import com.krause.wikigir.main.models.general.*;
import com.krause.wikigir.main.models.utils.*;

import java.util.*;
import java.io.*;

/**
 * Parses the wiki xml file to generate pure textual tokenization and tf-idf structure (for vector-space-like IR)
 * for each article. Note that the dictionary creation must be run first (for availability of the idf).
 */
public class ArticleTopWordsScoresVectorCreator
{
    // The number of articles to process (0 = all articles).
    private static final int ARTICLES_LIMIT = 0;

    private final String filePath;
    private final int maxVectorElements;
    private final Map<String, ScoresVector> vectorsMap;
    private final BlockingThreadFixedExecutor executor;

    /**
     * Constructor.
     */
    public ArticleTopWordsScoresVectorCreator()
    {
        this.maxVectorElements = GetFromConfig.intValue("wikigir.articles.max_terms_vector_size");
        this.filePath = GetFromConfig.filePath("wikigir.base_path", "wikigir.articles.folder",
                                               "wikigir.articles.tf_idf_vector_file_name");

        this.vectorsMap = new HashMap<>();
        this.executor = new BlockingThreadFixedExecutor();
    }

    /**
     * Creates a mapping from a article's title to its tf-idf word structure.
     * @return the mapping.
     */
    public Map<String, ScoresVector> create()
    {
        if(!Dictionary.getInstance().isCreated())
        {
            throw new RuntimeException("Dictionary must be created before articles tf-idf terms vectors are created.");
        }

        if(!new File(this.filePath).exists())
        {
            // The dictionary is a prerequisite to get word IDs and scores.
            if(!Dictionary.getInstance().isCreated())
            {
                Dictionary.getInstance().create();
            }

            readFromXml();
            new Serializer().serialize();
        }
        else
        {
            new Serializer().deserialize();
        }

        return this.vectorsMap;
    }

    // Parses the entire Wikipedia XML file to generate the mappings.
    private void readFromXml()
    {
        int[] processed = {0};

        WikiXMLArticlesExtractor.extract(CleanTextXMLParser::new,
            (parser, text) ->
                this.executor.execute(() ->
                    ExceptionWrapper.wrap(() ->
                    {
                        ProgressBar.mark(processed, Constants.NUMBER_OF_ARTICLES);
                        parser.parse(text);
                        parser.addTitleToResult(text);

                        if(parser.getTitle() == null || parser.getResult().get(CleanTextXMLParser.CLEAN_TEXT_KEY) == null)
                        {
                            return;
                        }

                        List<String> words = TextTokenizer.tokenize((String)parser.getResult().get(
                                CleanTextXMLParser.CLEAN_TEXT_KEY), true);

                        words = TextTokenizer.filterStopWords(words);

                        ScoresVector scoresVector = createScoresVector(words);

                        synchronized(ArticleTopWordsScoresVectorCreator.this)
                        {
                            this.vectorsMap.put(parser.getTitle(), scoresVector);
                        }
                    }, ExceptionWrapper.Action.NOTIFY_LONG)
                ), ARTICLES_LIMIT);

        this.executor.waitForTermination();
    }

    // Creates a single article's tf-idf structure based on the tokenized words extracted from
    // the xml file and the dictionary, pre-loaded from the same file.
    private ScoresVector createScoresVector(List<String> words)
    {
        Map<Integer, Integer> workingMap = new HashMap<>();
        for(String word : words)
        {
            // Should not happen, we should have mappings for all words.
            Integer id = Dictionary.getInstance().wordToId(word);
            if(id == null)
            {
                continue;
            }

            workingMap.putIfAbsent(id, 0);
            workingMap.put(id, workingMap.get(id) + 1);
        }

        return generateTfIfdScoresVector(workingMap);
    }

    // Generates the tf-idf structure.
    private ScoresVector generateTfIfdScoresVector(Map<Integer, Integer> workingMap)
    {
        List<Pair<Integer, Float>> terms = new ArrayList<>();

        for(Map.Entry<Integer, Integer> e : workingMap.entrySet())
        {
            terms.add(new Pair<>(e.getKey(), (float)(Math.log10(1 + e.getValue()) *
                    Dictionary.getInstance().logIdf(e.getKey()))));
        }

        if(terms.size() > this.maxVectorElements)
        {
            // Sort from maximal score to lowest score (need to reverse).
            terms.sort(Comparator.comparingDouble(Pair::getV2));
            Collections.reverse(terms);
            terms = terms.subList(0, this.maxVectorElements);
        }

        // Sort by word id to easily compute the dot product, later.
        terms.sort(Comparator.comparingInt(Pair::getV1));

        int[] wordIds = new int[terms.size()];
        float[] wordScores = new float[terms.size()];
        for(int i = 0; i < terms.size(); i++)
        {
            wordIds[i] = terms.get(i).v1;
            wordScores[i] = terms.get(i).v2;
        }

        normalize(wordScores);

        // For garbage collection, the map is no longer needed.
        workingMap.clear();

        return new ScoresVector(wordIds, wordScores);
    }

    // Make the L2 norm of the scores vector normalized (length = 1).
    private void normalize(float[] wordScores)
    {
        double norm = 0;
        for(float score : wordScores)
        {
            norm += Math.pow(score, 2);
        }

        norm = (float)Math.sqrt(norm);

        for(int i = 0; i < wordScores.length; i++)
        {
            wordScores[i] = (float)(wordScores[i] / norm);
        }
    }

    private class Serializer implements CustomSerializable
    {
        @Override
        public String filePath()
        {
            return ArticleTopWordsScoresVectorCreator.this.filePath;
        }

        @Override
        public void customSerialize(DataOutputStream out) throws IOException
        {
            out.writeInt(ArticleTopWordsScoresVectorCreator.this.vectorsMap.size());
            for(Map.Entry<String, ScoresVector> e : ArticleTopWordsScoresVectorCreator.this.vectorsMap.entrySet())
            {
                out.writeUTF(e.getKey());

                out.writeInt(e.getValue().getIds().length);
                for (int id : e.getValue().getIds())
                {
                    out.writeInt(id);
                }

                out.writeInt(e.getValue().getScores().length);
                for (float score : e.getValue().getScores())
                {
                    out.writeFloat(score);
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

                int[] wordIds = new int[in.readInt()];
                for(int j = 0; j < wordIds.length; j++)
                {
                    wordIds[j] = in.readInt();
                }

                float[] wordScores = new float[in.readInt()];
                for(int j = 0; j < wordScores.length; j++)
                {
                    wordScores[j] = in.readFloat();
                }

                ArticleTopWordsScoresVectorCreator.this.vectorsMap.put(title, new ScoresVector(wordIds, wordScores));
            }
        }
    }

    public static void main(String[] args)
    {
        System.out.println("Creating the dictionary (or loading from disk).");
        Dictionary.getInstance().create();
        System.out.println("Parsing articles text and creating vectors (or loading from disk).");
        new ArticleTopWordsScoresVectorCreator().create();
    }
}