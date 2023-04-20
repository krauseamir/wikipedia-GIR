package com.krause.wikigir.main.models.utils;

import java.util.Objects;

/**
 * A generic pair object.
 * @param <V1> the first item's class.
 * @param <V2> the second item's class.
 */
public class Pair <V1, V2>
{
    public V1 v1;
    public V2 v2;

    public Pair(V1 v1, V2 v2)
    {
        this.v1 = v1;
        this.v2 = v2;
    }

    public V1 getV1()
    {
        return this.v1;
    }

    public V2 getV2()
    {
        return this.v2;
    }

    public int hashCode()
    {
        return 31 * this.v1.hashCode() + this.v2.hashCode();
    }

    public boolean equals(Object other)
    {
        if(!(other instanceof Pair))
        {
            return false;
        }

        Pair<?, ?> p = (Pair<?, ?>)other;
        return Objects.equals(this.v1, p.v1) && Objects.equals(this.v2, p.v2);
    }

    public String toString()
    {
        return "[v1: " + this.v1 + ", v2: " + this.v2 + "]";
    }
}
