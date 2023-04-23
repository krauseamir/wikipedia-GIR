package com.krause.wikigir.main.models.articles.dataCreation;

import com.krause.wikigir.main.models.articles.Article;

import java.util.Map;

public class ArticlesFactory
{
    private static ArticlesFactory instance;

    /**
     * Singleton.
     * @return the singleton {@link ArticlesFactory} object.
     */
    public static ArticlesFactory getInstance()
    {
        if(instance == null)
        {
            instance = new ArticlesFactory();
        }

        return instance;
    }

    private boolean created;

    public boolean isCreated()
    {
        return this.created;
    }

    public Map<String, Article> create()
    {
        return null;
    }
}
