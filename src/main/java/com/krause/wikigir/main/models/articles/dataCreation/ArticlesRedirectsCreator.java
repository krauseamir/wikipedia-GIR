package com.krause.wikigir.main.models.articles.dataCreation;

import com.krause.wikigir.main.Constants;
import com.krause.wikigir.main.models.general.WikiXMLArticlesExtractor;
import com.krause.wikigir.main.models.general.XMLParser;
import com.krause.wikigir.main.models.utils.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.*;
import java.io.*;

/**
 * Parses the Wikipedia XML file and detects articles which actually redirect to other articles (e.g., there is a newer
 * version for the title), as well as the redirect destination.
 */
public class ArticlesRedirectsCreator
{
    // Parse all articles.
    private static final int ARTICLES_LIMIT = 0;

    private static final String REDIRECT_KEY = "redirect_to";
    private static final Pattern REDIRECT_PATTERN = Pattern.compile("<redirect\\s+title\\s*=\\s*\"(.*?)\"\\s*/\\s*>");

    private final String filePath;
    private final Map<String, String> redirectsMap;
    private final BlockingThreadFixedExecutor executor;

    /**
     * Constructor.
     */
    public ArticlesRedirectsCreator()
    {
        this.filePath = GetFromConfig.filePath("wikigir.base_path", "wikigir.articles.folder",
                                               "wikigir.articles.redirects.file_name");
        this.redirectsMap = new HashMap<>();
        this.executor = new BlockingThreadFixedExecutor();
    }

    /**
     * Creates the redirects mapping by parsing the XML file, or reads it from disk if previously created.
     * @return the redirects mapping.
     */
    public Map<String, String> create()
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

        return this.redirectsMap;
    }

    private void readFromXml()
    {
        int[] processed = {0};

        WikiXMLArticlesExtractor.extractRedirects(getRedirectsParserFactory(),
            (parser, text) ->
                this.executor.execute(() ->
                    ExceptionWrapper.wrap(() ->
                    {
                        ProgressBar.mark(processed, Constants.NUMBER_OF_ARTICLES_AND_REDIRECTS);
                        parser.parse(text);

                        if(parser.getTitle() == null)
                        {
                            return;
                        }

                        synchronized(ArticlesRedirectsCreator.this)
                        {
                            String redirect = (String)parser.getResult().get(REDIRECT_KEY);
                            if(redirect != null)
                            {
                                ArticlesRedirectsCreator.this.redirectsMap.put(parser.getTitle(), redirect);
                            }
                        }
                    })
                ), ARTICLES_LIMIT);

        this.executor.waitForTermination();
    }

    private XMLParser.XMLParserFactory getRedirectsParserFactory()
    {
        return new XMLParser.XMLParserFactory()
        {
            @Override
            public XMLParser getParser()
            {
                return new XMLParser()
                {
                    @Override
                    public void parse(StringBuilder sb)
                    {
                        this.addTitleToResult(sb);

                        Matcher m = REDIRECT_PATTERN.matcher(sb.toString());
                        if(m.find())
                        {
                            this.result.put(REDIRECT_KEY, m.group(1).trim());
                        }
                    }
                };
            }
        };
    }

    private class Serializer implements CustomSerializable
    {
        @Override
        public String filePath()
        {
            return ArticlesRedirectsCreator.this.filePath;
        }

        @Override
        public void customSerialize(DataOutputStream out) throws IOException
        {
            out.writeInt(ArticlesRedirectsCreator.this.redirectsMap.size());
            for(Map.Entry<String, String> e : ArticlesRedirectsCreator.this.redirectsMap.entrySet())
            {
                out.writeUTF(e.getKey());
                out.writeUTF(e.getValue());
            }
        }

        @Override
        public void customDeserialize(DataInputStream in) throws IOException
        {
            int count = in.readInt();
            for(int i = 0; i < count; i++)
            {
                String title = in.readUTF();
                String redirect = in.readUTF();
                ArticlesRedirectsCreator.this.redirectsMap.put(title, redirect);
            }
        }
    }

    public static void main(String[] args)
    {
        new ArticlesRedirectsCreator().create();
    }
}