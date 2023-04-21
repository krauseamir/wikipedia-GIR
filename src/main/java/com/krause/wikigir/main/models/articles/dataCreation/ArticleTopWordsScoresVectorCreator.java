package com.krause.wikigir.main.models.articles.dataCreation;

import com.krause.wikigir.main.models.general.*;
import com.krause.wikigir.main.models.general.Dictionary;
import com.krause.wikigir.main.models.utils.ExceptionWrapper;
import com.krause.wikigir.main.Constants;
import com.krause.wikigir.main.models.utils.Pair;

import java.util.*;
import java.io.*;

/**
 * Parses the wiki xml file to generate pure textual tokenization and tf-idf structure (for vector-space-like IR) for
 * each article. Note that the dictionary creation must be run first (for availability of the idf).
 */
public class ArticleTopWordsScoresVectorCreator extends ScoresVectorCreator
{
    // The number of articles to process (0 = all articles).
    private static final int ARTICLES_LIMIT = 0;

    /**
     * Constructor.
     */
    public ArticleTopWordsScoresVectorCreator()
    {
        if(!Dictionary.getInstance().isCreated())
        {
            throw new RuntimeException("Dictionary must be created before articles tf-idf terms vectors are created.");
        }

        ExceptionWrapper.wrap(() ->
        {
            Properties p = new Properties();
            p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));

            super.maxVectorElements = Integer.parseInt(p.getProperty("wikigir.articles.max_terms_vector_size"));

            super.filePath = p.getProperty("wikigir.base_path") + p.getProperty("wikigir.articles.folder") +
                    p.getProperty("wikigir.articles.tf_idf_vector_file_name");
        });
    }

    /**
     * Creates a mapping from a article's title to its tf-idf word structure.
     * @return the mapping.
     */
    public Map<String, ScoresVector> create()
    {
        if(!new File(super.filePath).exists())
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
        int[] parsed = {0};

        WikiXMLArticlesExtractor.extract(CleanTextXmlParser::new,
            (parser, text) ->
                super.executor.execute(() ->
                    ExceptionWrapper.wrap(() ->
                    {
                        parser.parse(text);
                        parser.addTitleToResult(text);

                        if(parser.getTitle() == null || parser.getResult().get(CleanTextXmlParser.CLEAN_TEXT_KEY) == null)
                        {
                            return;
                        }

                        List<String> words = TextTokenizer.tokenize((String)parser.getResult().get(
                                CleanTextXmlParser.CLEAN_TEXT_KEY), true);

                        words = TextTokenizer.filterStopWords(words);

                        ScoresVector scoresVector = createScoresVector(words);

                        synchronized(ArticleTopWordsScoresVectorCreator.this)
                        {
                            this.vectorsMap.put(parser.getTitle(), scoresVector);

                            if (++parsed[0] % 10_000 == 0)
                            {
                                System.out.println("Passed and processed " + parsed[0] + " articles.");
                            }
                        }
                    }, ExceptionWrapper.Action.NOTIFY_LONG)
                ), ARTICLES_LIMIT);

        super.executor.waitForTermination();
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

        if(terms.size() > super.maxVectorElements)
        {
            // Sort from maximal score to lowest score (need to reverse).
            terms.sort(Comparator.comparingDouble(Pair::getV2));
            Collections.reverse(terms);
            terms = terms.subList(0, super.maxVectorElements);
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

        wordScores = normalize(wordScores);

        // For garbage collection, the map is no longer needed.
        workingMap.clear();

        return new ScoresVector(wordIds, wordScores);
    }

    public static void main(String[] args)
    {
        System.out.println("Creating the dictionary.");
        Dictionary.getInstance().create();
        System.out.println("Parsing articles text and creating vectors.");
        new ArticleTopWordsScoresVectorCreator().create();
    }
}