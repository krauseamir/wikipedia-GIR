package com.krause.wikigir.models.utils;

import java.io.*;

public interface CustomSerializable
{
    default void serialize()
    {
        ExceptionWrapper.wrap(() ->
        {
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
}
