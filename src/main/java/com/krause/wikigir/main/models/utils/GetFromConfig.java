package com.krause.wikigir.main.models.utils;

import org.apache.commons.lang3.StringUtils;
import com.krause.wikigir.main.Constants;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;

public class GetFromConfig
{
    private static Properties properties = null;

    public static String filePath(final String... filePartsConfigNames)
    {
        List<String> parts = new ArrayList<>();

        for(String partConfigName : filePartsConfigNames)
        {
            parts.add(getProperty(partConfigName));
        }

        return String.join("", parts);
    }

    public static int intValue(final String config)
    {
        return Integer.parseInt(getProperty(config));
    }

    public static double doubleValue(final String config)
    {
        return Double.parseDouble(getProperty(config));
    }

    public static String stringValue(final String config)
    {
        return getProperty(config);
    }

    private static String getProperty(final String config)
    {
        checkLoadProperties();

        String strValue = properties.getProperty(config);
        if(StringUtils.isEmpty(strValue))
        {
            throw new RuntimeException("Invalid configuration '" + strValue + "'");
        }

        return strValue;
    }

    private static void checkLoadProperties()
    {
        if(properties != null)
        {
            return;
        }

        ExceptionWrapper.wrap(() ->
        {
            properties = new Properties();
            properties.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));
        });
    }
}