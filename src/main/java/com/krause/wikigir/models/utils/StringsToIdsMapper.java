package com.krause.wikigir.models.utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.io.*;

/**
 * Holds a mapping (and reverse mapping) of strings to integer IDs. The mapping is written
 * to disk for future retrieval and id continuity (i.e., same int identification throughout runs).
 */
public class StringsToIdsMapper
{
    private String filePath;

    private Map<String, Integer> stringsToIds;
    private Map<Integer, String> idsToStrings;

    /**
     * Constructor.
     * @param filePath the full (absolute) path to the file containing the mapping.
     *                 (Does not initially exist, created upon first creation).
     */
    public StringsToIdsMapper(String filePath)
    {
        this.filePath = filePath;
        this.stringsToIds = new HashMap<>();
        this.idsToStrings = new HashMap<>();
    }

    /**
     * Creates the mapping, either by loading from disk or anew.
     * @param strings   the strings to be matched with identifiers.
     * @return          this mapping object.
     */
    public StringsToIdsMapper create(Collection<String> strings)
    {
        if(new File(this.filePath).exists())
        {
            new Serializer().deserialize();
        }
        else
        {
            int nextId = 0;
            for(String string : strings)
            {
                this.stringsToIds.put(string, nextId);
                this.idsToStrings.put(nextId, string);
                nextId++;
            }

            new Serializer().serialize();
        }

        return this;
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

    private class Serializer implements CustomSerializable
    {
        public String filePath()
        {
            return StringsToIdsMapper.this.filePath;
        }

        public void customSerialize(DataOutputStream out) throws IOException
        {
            out.writeInt(StringsToIdsMapper.this.stringsToIds.size());
            for(Map.Entry<String, Integer> e : StringsToIdsMapper.this.stringsToIds.entrySet())
            {
                out.writeUTF(e.getKey());
                out.writeInt(e.getValue());
            }
        }

        public void customDeserialize(DataInputStream in) throws IOException
        {
            int size = in.readInt();
            for(int i = 0; i < size; i++)
            {
                String title = in.readUTF();
                int id = in.readInt();
                StringsToIdsMapper.this.stringsToIds.put(title, id);
                StringsToIdsMapper.this.idsToStrings.put(id, title);
            }
        }
    }
}