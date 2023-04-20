package com.krause.wikigir.models.general;

import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Objects;

/**
 * Represents coordinates in regular floating point notation (not NSWE).
 */
public class Coordinates
{
    // Used just for pretty printing of values.
    private static final DecimalFormat DF = new DecimalFormat("#.##");

    /**
     * Used to compactly serialize the object by simply storing its coordinates one after the other.
     * @param out the underlying output stream used.
     * @param c the coordinates to be serialized.
     * @throws IOException if the object could not be written to disk.
     */
    public static void serialize(DataOutputStream out, Coordinates c) throws IOException
    {
        out.writeDouble(c.latitude);
        out.writeDouble(c.longitude);
    }

    /**
     * Used to read serialized coordinates from disk (two doubles one after the other).
     * @param in the underlying input stream used.
     * @return the instantiated {@link Coordinates} object.
     * @throws IOException if the values could not be read from disk.
     */
    public static Coordinates deserialize(DataInputStream in) throws IOException
    {
        return new Coordinates(in.readDouble(), in.readDouble());
    }


    private double latitude;
    private double longitude;

    /**
     * Constructor.
     * @param latitude the latitude (always first in the category notation).
     * @param longitude the longitude (always second in the category notation).
     */
    public Coordinates(double latitude, double longitude)
    {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Returns the longitude.
     * @return the longitude.
     */
    public double getLongitude()
    {
        return this.longitude;
    }

    /**
     * Returns the latitude.
     * @return the latitude.
     */
    public double getLatitude()
    {
        return this.latitude;
    }

    /**
     * Calculates the great-circle distance between two coordinates using the Harvesine formula.
     * (See https://en.wikipedia.org/wiki/Haversine_formula).
     *
     * @param c1 the first coordinates.
     * @param c2 the second coordinates.
     * @return the absolute distance in kilometers.
     */
    public static Double dist(Coordinates c1, Coordinates c2)
    {
        if(c1 == null || c2 == null)
        {
            return null;
        }

        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(c2.latitude - c1.latitude);
        double lonDistance = Math.toRadians(c2.longitude - c1.longitude);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(c1.latitude)) * Math.cos(Math.toRadians(c2.latitude)) *
                        Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return c * R;
    }

    public int hashCode()
    {
        return Objects.hash(this.longitude, this.latitude);
    }

    public boolean equals(Object other)
    {
        if(!(other instanceof Coordinates))
        {
            return false;
        }

        Coordinates cOther = (Coordinates)other;
        return this.latitude == cOther.latitude && this.longitude == cOther.longitude;
    }

    public String toString()
    {
        return "(" + DF.format(this.latitude) + ", " + DF.format(this.longitude) + ")";
    }
}
