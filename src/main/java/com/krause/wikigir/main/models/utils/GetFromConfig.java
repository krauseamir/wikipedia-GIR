package com.krause.wikigir.main.models.utils;

import com.krause.wikigir.main.Constants;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class GetFromConfig
{
    public static String filePath(final String... filePartsConfigNames)
    {
        final StringBuilder path = new StringBuilder();

        ExceptionWrapper.wrap(() ->
        {
            Properties p = new Properties();
            p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));

            for(String partConfigName : filePartsConfigNames)
            {
                String part = p.getProperty(partConfigName);
                if(StringUtils.isEmpty(part))
                {
                    throw new RuntimeException("Invalid configuration '" + partConfigName + "'");
                }

                path.append(part);
            }
        });

        return path.toString();
    }

    public static int intValue(final String config)
    {
        int[] result = {0};
        ExceptionWrapper.wrap(() -> result[0] = Integer.parseInt(getStringValue(config)));
        return result[0];
    }

    public static double doubleValue(final String config)
    {
        double[] result = {0.0};
        ExceptionWrapper.wrap(() -> result[0] = Double.parseDouble(getStringValue(config)));
        return result[0];
    }

    public static String stringValue(final String config)
    {
        String[] result = new String[1];
        ExceptionWrapper.wrap(() -> result[0] = getStringValue(config));
        return result[0];
    }

    private static String getStringValue(final String config) throws IOException
    {
        Properties p = new Properties();
        p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));

        String strValue = p.getProperty(config);
        if(StringUtils.isEmpty(strValue))
        {
            throw new RuntimeException("Invalid configuration '" + strValue + "'");
        }

        return strValue;
    }
}
