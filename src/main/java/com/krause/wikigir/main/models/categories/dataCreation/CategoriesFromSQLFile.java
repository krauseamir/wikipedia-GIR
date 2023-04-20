package com.krause.wikigir.main.models.categories.dataCreation;

import com.krause.wikigir.main.Constants;
import com.krause.wikigir.main.models.utils.ExceptionWrapper;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.*;

public class CategoriesFromSQLFile
{
    private String filePath;

    public CategoriesFromSQLFile()
    {
        ExceptionWrapper.wrap(() ->
        {
            Properties p = new Properties();
            p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));

            this.filePath = p.getProperty("wikigir.base_path") +
                            p.getProperty("wikigir.categories.folder") +
                            p.getProperty("wikigir.categories.raw_data.folder") +
                            p.getProperty("wikigir.categories.raw_data.sql_file_name");
        });
    }

    /**
     * Reads the wikipedia categories SQL file and transforms the "insert into..." directives into category names.
     * @return a set of unique category names as fetched from the SQL file.
     */
    public Set<String> parse()
    {
        Set<String> result = new HashSet<>();

        ExceptionWrapper.wrap(() ->
        {
            try(BufferedReader reader = new BufferedReader(new FileReader(this.filePath)))
            {
                String line;
                while((line = reader.readLine()) != null)
                {
                    if(!line.startsWith("INSERT INTO"))
                    {
                        continue;
                    }

                    // Process the line, which contains many "insert" commands - split to each command
                    // then extract only the category name (which is the only value needed here).
                    line = line.substring(line.indexOf("VALUES") + "VALUES".length() + 1);
                    String[] values = line.split("\\),\\(");
                    for(int i = 0; i < values.length; i++)
                    {
                        values[i] = values[i].substring(values[i].indexOf(",") + 2);
                        String category = values[i].substring(0, values[i].indexOf(",") - 1);
                        result.add(category);
                    }
                }
            }
        });

        return result;
    }
}