package com.krause.wikigir.main.models.articles.articleType;

/**
 * Defines location types. Each type can have variants to look for in text (both singular and plural
 * forms) and a "priority", which indicates how "far up" it is in the hierarchy (city < country etc).
 *
 * Defines article types, which have some importance for the purpose of geographical information retrieval and/or
 * prediction of coordinates. When appropriate, the article type also includes a "location type priority", which
 * indicates how "far up" it is in the hierarchy (city < country etc.). In addition, each article type might contain
 * "variants", which are evidence for that article type we can look for in the text or categories, for the purpose of
 * determining the type.
 * <br>
 * Note that this is all heuristic: the hierarchy was determined based on reason, and the variants (which are of utmost
 * importance for detecting article types) were chosen after observing hundreds of articles and trying to capture most
 * of the relevant words and phrases, given that they do not bring about many false positives.
 */
public enum ArticleType
{
    NONE        (-1, new String[] {}),  // Given to pages with no location type.
                                        // Ships are recognised differently since they are very hard to locate.
    SHIP        (-1, new String[] {"ship", "ships", "warship", "warships", "frigate", "frigates", "submarine",
                                   "submarines", "aircraft carrier", "aircraft carriers", "freighter", "freighter",
                                   "caravel", "caravels", "galleon", "galleons", "galley", "galleys", "ironclad",
                                   "ironclads", "battleship", "battleships", "cruiser", "cruisers", "destroyer",
                                   "destroyers", "steamship", "steamships", "fleet", "fleets"}),
    PERSON      (-1, new String[] {}),
    LAND        (0,  new String[] {"island", "islands", "peninsula", "archipelago", "massif"}),
    SEA         (0,  new String[] {"ocean", "oceans", "sea", "seas"}),
    COUNTRY     (1,  new String[] {"country", "countries", "kingdom", "empire", "monarchy", "republic"}),
    STATE       (2,  new String[] {"state", "states"}),
    AUTONOMOUS  (3,  new String[] {"autonomy", "autonomies", "microstate", "microstates", "canton", "cantons"}),
    REGION      (4,  new String[] {"region", "regions", "province", "provinces", "area", "areas", "county",
                                   "counties", "territory", "territories", "sites", "sites"}),
    NATURE      (4,  new String[] {"lake", "lakes", "swamp", "swamps", "ridge", "ridges", "mountain", "mountains",
                                   "river", "rivers", "stream", "streams", "affluent", "affluents", "creek", "creeks",
                                   "hill", "hills", "valley", "valleys", "coral", "corals", "reef", "glen", "glens"}),
    SETTLEMENT  (5,  new String[] {"city", "cities", "capital", "capitals", "town", "towns", "village", "villages",
                                   "commune", "communes", "port", "ports", "settlement", "settlements", "municipal",
                                   "municipality", "colony", "colonies", "hamlet", "hamlets", "borough", "boroughs",
                                   "suburb", "suburbs", "metropolis", "neighborhood", "neighborhoods"}),
    SPOT        (6,  new String[] {"house", "museum", "stadium", "statue", "monument", "sculpture", "building", "tower",
                                   "castle", "farm", "square", "fort", "citadel", "hotel", "motel", "memorial",
                                   "landmark", "garden", "factory", "university", "college", "theater", "theatre",
                                   "apartment", "palace", "temple", "cathedral", "mosque", "synagogue", "bridge",
                                   "fountain", "tomb", "church", "chapel", "campus", "plantation", "hospital", "estate",
                                   "shipyard", "station", "airport", "cemetery", "graveyard", "residence", "mall",
                                   "observatory", "street", "avenue", "zoo"});
                                   // Note - no plural variants were used to prefer precision over recall.
                                   // Additional words were dropped, such as "school", "field", "store", "club", etc.,
                                   // which have more than an actual location meaning (choose precision over recall).

    private int locationPriority;
    private String[] variants;

    /**
     * Constructor.
     * @param locationPriority  the location type's priority (in the location hierarchy), if applicable (-1 otherwise).
     * @param variants          the text variants for this article type.
     */
    ArticleType(int locationPriority, String[] variants)
    {
        this.locationPriority = locationPriority;
        this.variants = variants;
    }

    public int getLocationPriority()
    {
        return this.locationPriority;
    }

    public String[] getVariants()
    {
        return this.variants;
    }
}