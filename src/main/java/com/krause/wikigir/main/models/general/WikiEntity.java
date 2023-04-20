package com.krause.wikigir.main.models.general;

/**
 * Represents either a Wikipedia article or a category. These two entities share the following properties: they have
 * a (unique, identifying) title; they can be queried for coordinates (for articles - the human tagged coordinates
 * provided by Wikipedia editors, if present; for categories - the average coordinates of its contained articles).
 * <br>
 * The title is equal to the Wikipedia link, minus the host and folders, and after replacing "_" with " ". For example,
 * "https://en.wikipedia.org/wiki/Caleta_de_Fuste" -> "Caleta de Fuste".
 */
public abstract class WikiEntity
{
    protected String title;

    /**
     * Constructor.
     * @param title the entity's title.
     */
    public WikiEntity(String title)
    {
        this.title = title;
    }

    /**
     * Returns the coordinates for the implementing entity. An additional parameter, requestingEntity is provided since
     * the request for coordinates can come in the context of another entity whose location we attempt to assess. In
     * certain cases, that location needs to be taken into account when returning this entity's coordinates - for
     * example in the categories case where the requesting entity was a contributor to the category's coordinates.
     *
     * @param requestingEntity  the requesting entity (could be null).
     * @return                  the coordinates.
     */
    public abstract Coordinates getCoordinates(WikiEntity requestingEntity);

    /**
     * Returns the coordinates without taking a requesting entity into account. This can be used either for articles,
     * or categories - when their actual average coordinates are computed regardless of a particular tested article.
     *
     * @return the coordinates.
     */
    public Coordinates getCoordinates()
    {
        return getCoordinates(null);
    }

    /**
     * Returns the entity's title.
     * @return the entity's title.
     */
    public String getTitle()
    {
        return this.title;
    }
}