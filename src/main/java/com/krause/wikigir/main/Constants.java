package com.krause.wikigir.main;

import java.text.DecimalFormat;

public class Constants
{
    public static final String CONFIGURATION_FILE = "program.properties";

    // Used just for pretty printing of values.
    public static final DecimalFormat DF = new DecimalFormat("#.##");

    /**
     * Since generating most data structures involves heavy parsing of the Wikipedia XML file, we print the state of
     * advancement every GENERATION_PRINT_CHECKPOINT articles, so the process does not seem stuck for too long.
     */
    public static final int GENERATION_PRINT_CHECKPOINT = 100_000;
}
