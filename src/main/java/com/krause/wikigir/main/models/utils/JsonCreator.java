package com.krause.wikigir.main.models.utils;

import java.util.*;

/**
 * Creates a generic JSON representation of a given object.
 */
public class JsonCreator
{
    /**
     * Creates a generic JSON representation of the given object.
     * @param o the given object.
     * @return  the JSON (string) representation.
     */
    public static String create(Object o)
    {
        StringBuilder result = new StringBuilder();

        createHelper(result, o);

        return result.toString();
    }

    private static void createHelper(StringBuilder result, Object o)
    {
        if(o == null)
        {
            result.append("null");
        }
        else if(o instanceof Number || o instanceof Boolean)
        {
            result.append(o);
        }
        else if(o instanceof String)
        {
            result.append("\"").append(o).append("\"");
        }
        else if(o instanceof Pair)
        {
            Pair<?, ?> p = (Pair<?, ?>)o;
            result.append("{\"").append(p.v1).append("\":");
            createHelper(result, p.v2);
            result.append("}");
        }
        else if(o instanceof Map)
        {
            result.append("{");
            int count = 0;
            Map<?, ?> m = ((Map<?, ?>)o);
            for(Map.Entry<?, ?> e : m.entrySet())
            {
                result.append("\"").append(e.getKey()).append("\":");
                createHelper(result, e.getValue());
                if(count++ < m.size() - 1)
                {
                    result.append(",");
                }
            }
            result.append("}");
        }
        else if(o instanceof List)
        {
            result.append("[");
            int count = 0;
            List<?> l = (List<?>)o;
            for(Object element : l)
            {
                createHelper(result, element);
                if(count++ < l.size() - 1)
                {
                    result.append(",");
                }
            }
            result.append("]");
        }
    }

    public static void main(String[] args)
    {

    }
}
