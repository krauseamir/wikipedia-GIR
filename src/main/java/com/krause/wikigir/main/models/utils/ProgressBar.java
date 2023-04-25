package com.krause.wikigir.main.models.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class ProgressBar
{
    // Must be larger than 6, preferably 100 or more (if it fits the screen).
    private static final int BAR_LENGTH = 100;

    public static void mark(int[] done, int total)
    {
        synchronized (ProgressBar.class)
        {
            if(done[0] == 0)
            {
                printTitle();
            }
            else if(done[0] % (total / 100) == 0)
            {
                System.out.print("|");
            }

            done[0]++;
        }
    }

    private static void printTitle()
    {
        List<String> title = new ArrayList<>();

        title.add("0%");

        String[] spaces = new String[BAR_LENGTH - 6];
        Arrays.fill(spaces, " ");
        title.addAll(Arrays.asList(spaces));

        title.add("100%");

        System.out.println(String.join("", title));
    }
}
