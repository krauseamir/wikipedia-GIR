package com.krause.wikigir.main.models.utils;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.io.*;

public interface CustomSerializable
{
    default void serialize()
    {
        ExceptionWrapper.wrap(() ->
        {
            createFoldersIfNeeded(filePath());

            try(DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                                       new FileOutputStream(filePath()))))
            {
                customSerialize(out);
            }
        });
    }

    default void deserialize()
    {
        ExceptionWrapper.wrap(() ->
        {
            try(DataInputStream in = new DataInputStream(new BufferedInputStream(
                                     new FileInputStream(filePath()))))
            {
                customDeserialize(in);
            }
        });
    }

    String filePath();
    void customSerialize(DataOutputStream out) throws IOException;
    void customDeserialize(DataInputStream in) throws IOException;

    // Make sure the full directories structure is created for the serialized file.
    private void createFoldersIfNeeded(String filePath) throws IOException
    {
        String[] parts = filePath.split("\\\\");
        parts = Arrays.copyOfRange(parts, 0, parts.length - 1);
        Files.createDirectories(Paths.get(String.join("\\", parts)));
    }
}