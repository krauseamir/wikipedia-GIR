package com.krause.wikigir.main.models.utils;

import com.krause.wikigir.main.Constants;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
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

    public static int[] intValues(final String... configs)
    {
        int[] result = new int[configs.length];

        ExceptionWrapper.wrap(() ->
        {
            Properties p = new Properties();
            p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));

            for(int i = 0; i < configs.length; i++)
            {
                String strValue = p.getProperty(configs[i]);
                if(StringUtils.isEmpty(strValue))
                {
                    throw new RuntimeException("Invalid configuration '" + strValue + "'");
                }

                result[i] = Integer.parseInt(strValue);
            }
        });

        return result;
    }
}
