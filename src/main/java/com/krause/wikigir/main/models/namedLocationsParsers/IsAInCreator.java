package com.krause.wikigir.main.models.namedLocationsParsers;

import com.krause.wikigir.main.models.articles.dataCreation.ArticlesCoordinatesCreator;
import com.krause.wikigir.main.models.articles.dataCreation.ArticlesRedirectsCreator;
import com.krause.wikigir.main.models.utils.BlockingThreadFixedExecutor;
import com.krause.wikigir.main.models.general.WikiXMLArticlesExtractor;
import com.krause.wikigir.main.models.utils.CustomSerializable;
import com.krause.wikigir.main.models.utils.ExceptionWrapper;
import com.krause.wikigir.main.models.utils.GetFromConfig;
import com.krause.wikigir.main.models.general.Coordinates;

import java.util.*;
import java.io.*;

public class IsAInCreator
{
    private static final int ARTICLES_LIMIT = 0;

    private final String filePath;

    private final Map<String, Coordinates> articlesWithCoordinates;
    private final Map<String, String> redirects;

    private final Map<String, List<String>> mapping;

    private final BlockingThreadFixedExecutor executor;

    /**
     * Constructor.
     */
    public IsAInCreator(Map<String, Coordinates> articlesWithCoordinates, Map<String, String> redirects)
    {
        this.mapping = new HashMap<>();

        this.articlesWithCoordinates = articlesWithCoordinates;
        this.redirects = redirects;

        this.executor = new BlockingThreadFixedExecutor();

        this.filePath = GetFromConfig.filePath("wikigir.base_path", "wikigir.articles.folder",
                                               "wikigir.articles.is_a_in.file_name");
    }


    public Map<String, List<String>> create()
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

        return this.mapping;
    }

    @SuppressWarnings("unchecked")
    private void readFromXml()
    {
        int[] passed = {0};

        WikiXMLArticlesExtractor.extract(() -> new IsAInParser(this.articlesWithCoordinates, this.redirects),
            (parser, text) ->
                this.executor.execute(() ->
                    ExceptionWrapper.wrap(() ->
                    {
                        parser.addTitleToResult(text);
                        parser.parse(text);

                        List<String> locations = (List<String>)parser.getResult().get(IsAInParser.ENTITIES_KEY);

                        synchronized(IsAInCreator.this)
                        {
                            if(locations != null && !locations.isEmpty())
                            {
                                this.mapping.put(parser.getTitle(), locations);
                            }

                            if(++passed[0] % 100_000 == 0)
                            {
                                System.out.println("Passed " + passed[0] + " articles, found: " +
                                                   IsAInCreator.this.mapping.size());
                            }
                        }
                    }, ExceptionWrapper.Action.IGNORE)
                ), ARTICLES_LIMIT);

        this.executor.waitForTermination();
    }

    private class Serializer implements CustomSerializable
    {
        @Override
        public String filePath()
        {
            return IsAInCreator.this.filePath;
        }

        @Override
        public void customSerialize(DataOutputStream out) throws IOException
        {
            out.writeInt(IsAInCreator.this.mapping.size());
            for(Map.Entry<String, List<String>> e : IsAInCreator.this.mapping.entrySet())
            {
                out.writeUTF(e.getKey());
                out.writeInt(e.getValue().size());
                for(String s : e.getValue())
                {
                    out.writeUTF(s);
                }
            }
        }

        @Override
        public void customDeserialize(DataInputStream in) throws IOException
        {
            int count = in.readInt();
            for(int i = 0; i < count; i++)
            {
                String title = in.readUTF();
                int locationsSize = in.readInt();
                List<String> locations = new ArrayList<>();
                for(int j = 0; j < locationsSize; j++)
                {
                    locations.add(in.readUTF());
                }
                IsAInCreator.this.mapping.put(title, locations);
            }
        }
    }

    public static void main(String[] args)
    {
        System.out.println("Creating coordinates mapping (or loading from disk).");
        Map<String, Coordinates> coordinates = new ArticlesCoordinatesCreator().create();
        System.out.println("Creating redirects mapping (or loading from disk).");
        Map<String, String> redirects = new ArticlesRedirectsCreator().create();
        System.out.println("Creating is-a-in mapping (or loading from disk).");
        int howMany = new IsAInCreator(coordinates, redirects).create().size();
        System.out.println("Created. Found " + howMany + " articles with \"is a ___ in ___\" structure.");
    }
}