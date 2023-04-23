package com.krause.wikigir.main.models.utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.io.*;

/**
 * Holds a mapping (and reverse mapping) of strings to integer IDs. The mapping is written
 * to disk for future retrieval and id continuity (i.e., same int identification throughout runs).
 */
public class StringsIdsMapper
{
    private String filePath;

    private Map<String, Integer> stringsToIds;
    private Map<Integer, String> idsToStrings;

    /**
     * Constructor 1.
     * @param filePath the full (absolute) path to the file containing the mapping.
     *                 (Does not initially exist, created upon first creation).
     */
    public StringsIdsMapper(String filePath)
    {
        this.filePath = filePath;
        this.stringsToIds = new HashMap<>();
        this.idsToStrings = new HashMap<>();
    }

    /**
     * Constructor 2. Used in case we want to add the words to be mapped externally (not to/from stored file).
     */
    public StringsIdsMapper()
    {
        this(null);
    }

    /**
     * Creates the mapping, either by loading from disk or anew.
     * @param strings   the strings to be matched with identifiers.
     * @return          this mapping object.
     */
    public StringsIdsMapper createFromCollection(Collection<String> strings)
    {
        if(this.filePath == null)
        {
            return this;
        }

        if(new File(this.filePath).exists())
        {
            new Serializer().deserialize();
        }
        else
        {
            int nextId = 0;
            for(String string : strings)
            {
                add(string, nextId);
                nextId++;
            }

            new Serializer().serialize();
        }

        return this;
    }

    public void add(String s, int id)
    {
        this.stringsToIds.put(s, id);
        this.idsToStrings.put(id, s);
    }

    /**
     * Returns a string ID, given the string.
     * @param s the given string.
     * @return a string ID, given the string.
     */
    public Integer getID(String s)
    {
        return this.stringsToIds.get(s);
    }

    /**
     * Returns the string matching the given ID.
     * @param i the given ID.
     * @return the string matching the given ID.
     */
    public String getString(int i)
    {
        return this.idsToStrings.get(i);
    }

    /**
     * Returns all mapped strings.
     * @return all mapped strings.
     */
    public Collection<String> getStrings()
    {
        return this.stringsToIds.keySet();
    }

    /**
     * Returns all mapped ids.
     * @return all mapped ids.
     */
    public Collection<Integer> getIDs()
    {
        return this.idsToStrings.keySet();
    }

    public int size()
    {
        return this.idsToStrings.size();
    }

    public class Serializer implements CustomSerializable
    {
        @Override
        public String filePath()
        {
            return StringsIdsMapper.this.filePath;
        }

        @Override
        public void customSerialize(DataOutputStream out) throws IOException
        {
            out.writeInt(StringsIdsMapper.this.stringsToIds.size());
            for(Map.Entry<String, Integer> e : StringsIdsMapper.this.stringsToIds.entrySet())
            {
                out.writeUTF(e.getKey());
                out.writeInt(e.getValue());
            }
        }

        @Override
        public void customDeserialize(DataInputStream in) throws IOException
        {
            int size = in.readInt();
            for(int i = 0; i < size; i++)
            {
                String title = in.readUTF();
                int id = in.readInt();
                StringsIdsMapper.this.stringsToIds.put(title, id);
                StringsIdsMapper.this.idsToStrings.put(id, title);
            }
        }
    }
}