package com.krause.wikigir.main.models.categories.dataCreation;

import com.krause.wikigir.main.models.articles.dataCreation.ArticlesFactory;
import com.krause.wikigir.main.models.utils.*;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.*;
import java.io.*;

/**
 * Represents the entire Wikipedia extracted category graph. Contains both methods for creation of the graph from
 * the raw categories files and main XML file and for serializing / deserializing it from disk. Note that mapping
 * of categories and their tree structure relies solely on their string values (names).
 */
public class CategoryNamesGraph
{
    private static CategoryNamesGraph instance;

    /**
     * Singleton.
     * @return the singleton {@link CategoryNamesGraph} object.
     */
    public static CategoryNamesGraph getInstance()
    {
        if(instance == null)
        {
            instance = new CategoryNamesGraph();
        }

        return instance;
    }

    /**
     * A single category's data - it's name and super/sub categories' nodes.
     */
    public static class CategoryNode
    {
        private String category;
        private Set<CategoryNode> subcategories;
        private Set<CategoryNode> supercategories;

        public CategoryNode(String category)
        {
            this.category = category;
            this.subcategories = new HashSet<>();
            this.supercategories = new HashSet<>();
        }

        public void addSupercategory(CategoryNode supercategory)
        {
            this.supercategories.add(supercategory);
        }

        public void addSubcategory(CategoryNode subcategory)
        {
            this.subcategories.add(subcategory);
        }

        public int hashCode()
        {
            return this.category.hashCode();
        }

        public boolean equals(Object other)
        {
            if(!(other instanceof CategoryNode))
            {
                return false;
            }

            return this.category.equals(((CategoryNode)other).category);
        }
    }

    private final String filePath;

    // The main mapping in order to perform graph traversal 0 given a category name, get its node.
    private final Map<String, CategoryNode> namesToNodes;

    /**
     * Constructor.
     */
    private CategoryNamesGraph()
    {
        this.filePath = GetFromConfig.filePath("wikigir.bash_path", "wikigir.categories.folder",
                                               "wikigir.categories.graph_file_name");

        this.namesToNodes = new HashMap<>();
    }

    /**
     * Generates the graph either from file or from the raw categories files.
     * @return this graph object.
     */
    public CategoryNamesGraph create()
    {
        if(new File(this.filePath).exists())
        {
            new Serializer().deserialize();
        }
        else
        {
            List<Pair<String, String[]>> nodesToSubcategories = parseCategoriesFile();

            linkCategories(nodesToSubcategories);

            new CategoryParentsFromXML(this.namesToNodes).extract();

            new Serializer().serialize();
        }

        return this;
    }

    /**
     * Retrieves the super categories for given category ids - starting from them, upwards, BFS-wise.
     * The resulting categories are sorted into levels (where level 0 is the initial category),
     * without any specific internal ordering.
     *
     * @param catIds the initial category ids.
     * @param levels the number of levels to scan (including the first category, so reach levels - 1).
     * @return the mapping of super categories.
     */
    public Map<Integer, Set<String>> getAncestors(int[] catIds, int levels)
    {
        if(!ArticlesFactory.getInstance().isCreated())
        {
            throw new RuntimeException("Must run ArticlesFactory.getInstance().create() before running getAncestors()");
        }

        StringsIdsMapper idToStr = ArticlesFactory.getInstance().getCategoriesIdsMapper();

        List<String> initialCategories = IntStream.of(catIds).boxed().map(idToStr::getString).
                filter(Objects::nonNull).collect(Collectors.toList());
        if(initialCategories.isEmpty())
        {
            return null;
        }

        // Don't visit the same category twice (trim the branching).
        Set<String> seen = new HashSet<>();

        Map<Integer, Set<String>> ancestors = new HashMap<>();

        Set<String> currLevel = new HashSet<>(initialCategories);

        for(int i = 0; i < levels; i++)
        {
            ancestors.put(i, currLevel);

            // Don't needlessly calculate the next level.
            if(i < levels - 1)
            {
                Set<String> nextLevel = new HashSet<>();
                for (String category : currLevel)
                {
                    CategoryNode n = this.namesToNodes.get(category);
                    if (n != null)
                    {
                        for(CategoryNode supercategory : n.supercategories)
                        {
                            if(seen.contains(supercategory.category))
                            {
                                continue;
                            }

                            seen.add(supercategory.category);
                            nextLevel.add(supercategory.category);
                        }
                    }
                }

                currLevel = nextLevel;
            }
        }

        return ancestors;
    }

    // Parses the raw categories files (acquired via CategoriesAPIQuerier) to produce a list of mappings from
    // categories to their subcategories. Note that the same category can appear multiple times, thus its subcategories
    // are "split", if several API requests were needed to get all of them.
    private List<Pair<String, String[]>> parseCategoriesFile()
    {
        List<Pair<String, String[]>> result = new ArrayList<>();

        String dir = GetFromConfig.filePath("wikigir.base_path", "wikigir.categories.folder",
                                            "wikigir.categories.web_api.text_files.folder");

        String[] fileNames = Objects.requireNonNull(new File(dir).list());

        for(String fileName : fileNames)
        {
            ExceptionWrapper.wrap(() ->
            {
                try(BufferedReader reader = new BufferedReader(new FileReader(dir + fileName)))
                {
                    String line;
                    while((line = reader.readLine()) != null)
                    {
                        String[] parts = line.split("\t");
                        String category = parts[1].trim();
                        String[] subcategories = parts[2].substring(1, parts[2].length() - 1).split("\\|\\|\\|");

                        // Remember to transform the subcategories (as acquired by the API call) to normalized-Wiki form
                        // by replacing " " with "_", retaining the case. It creates consistency across representations.
                        subcategories = Stream.of(subcategories).map(x -> x.replaceAll(" ", "_")).toArray(String[]::new);
                        result.add(new Pair<>(category, subcategories));
                    }
                }
            }, ExceptionWrapper.Action.NOTIFY_LONG);
        }

        return result;
    }

    // The same category could be written multiple times, which happens if the sub categories could not be fetched in a
    // single API call, but required several. Note that in these cases the sub categories are merged under the
    // appropriate super category.
    private void linkCategories(List<Pair<String, String[]>> categoriesToSubcategories)
    {
        // Create distinct Node objects for each category, since it can appear multiple times.
        Set<String> set = categoriesToSubcategories.stream().map(Pair::getV1).collect(Collectors.toSet());
        set.forEach(category -> this.namesToNodes.put(category, new CategoryNode(category)));

        // Iterate over all subcategories (the same category can appear multiple times).
        for(Pair<String, String[]> p : categoriesToSubcategories)
        {
            CategoryNode catNode = this.namesToNodes.get(p.v1);

            if(catNode == null)
            {
                continue;
            }

            for(String subcategory : p.v2)
            {
                CategoryNode subCatNode = this.namesToNodes.get(subcategory);

                if(subCatNode == null)
                {
                    continue;
                }

                catNode.subcategories.add(subCatNode);
                subCatNode.supercategories.add(catNode);
            }
        }
    }

    private class Serializer implements CustomSerializable
    {
        @Override
        public String filePath()
        {
            return CategoryNamesGraph.this.filePath;
        }

        @Override
        public void customSerialize(DataOutputStream out) throws IOException
        {
            // First, write only the keys, so new (empty) node objects can be created.
            out.writeInt(CategoryNamesGraph.this.namesToNodes.size());
            for(Map.Entry<String, CategoryNode> e : CategoryNamesGraph.this.namesToNodes.entrySet())
            {
                out.writeUTF(e.getKey());
            }
            // Then, write all super and sub categories to be linked (As keys).
            for(Map.Entry<String, CategoryNode> e : CategoryNamesGraph.this.namesToNodes.entrySet())
            {
                out.writeUTF(e.getKey());
                out.writeInt(e.getValue().subcategories.size());
                for(CategoryNode n : e.getValue().subcategories)
                {
                    out.writeUTF(n.category);
                }
                out.writeInt(e.getValue().supercategories.size());
                for(CategoryNode n : e.getValue().supercategories)
                {
                    out.writeUTF(n.category);
                }
            }
        }

        @Override
        public void customDeserialize(DataInputStream in) throws IOException
        {
            int nodesMapSize = in.readInt();
            for(int i = 0; i < nodesMapSize; i++)
            {
                String nodeName = in.readUTF();
                CategoryNamesGraph.this.namesToNodes.put(nodeName, new CategoryNode(nodeName));
            }
            for(int i = 0; i < nodesMapSize; i++)
            {
                CategoryNode categoryNode = CategoryNamesGraph.this.namesToNodes.get(in.readUTF());
                int subsCount = in.readInt();
                for(int j = 0; j < subsCount; j++)
                {
                    categoryNode.subcategories.add(CategoryNamesGraph.this.namesToNodes.get(in.readUTF()));
                }
                int supersCount = in.readInt();
                for(int j = 0; j < supersCount; j++)
                {
                    categoryNode.supercategories.add(CategoryNamesGraph.this.namesToNodes.get(in.readUTF()));
                }
            }
        }
    }

    public static void main(String[] args)
    {
        System.out.println("creating categories graph (or loading from disk).");
        new CategoryNamesGraph().create();
    }
}