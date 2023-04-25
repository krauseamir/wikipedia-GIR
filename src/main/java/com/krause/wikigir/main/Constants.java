package com.krause.wikigir.main;

import java.text.DecimalFormat;

public class Constants
{
    public static final String CONFIGURATION_FILE = "program.properties";

    // Parsed beforehand, only used for convenience (printing progress bars).
    public static final int NUMBER_OF_ARTICLES = 5913419;

    // TODO: set this.
    // Parsed beforehand, only used for convenience (printing progress bars).
    public static final int NUMBER_OF_ARTICLES_AND_REDIRECTS = 5913419;

    // Used just for pretty printing of values.
    public static final DecimalFormat DF = new DecimalFormat("#.##");
}
