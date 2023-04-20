package com.krause.wikigir.dataFetch;

import java.io.BufferedWriter;
import java.io.FileWriter;

/**
 * A helper class for writing regular textual files which contain a lot of lines. The file
 * is effectively split into multiple smaller files (with a predefined lines quota).
 */
public class MultipleFilesWriter
{
    private int currFileNum;
    private int itemsPerFile;
    private int writtenToFile;

    private String fileBasePath;

    private BufferedWriter writer;

    /**
     * Constructor.
     * @param itemsPerFile the number of lines that can be written to each sub-file.
     * @param fileBasePath the full base path to the sub-files (they are this base path + counters).
     * @throws Exception if creation of the buffered writer failed.
     */
    public MultipleFilesWriter(int itemsPerFile, String fileBasePath) throws Exception
    {
        this.itemsPerFile = itemsPerFile;
        this.currFileNum = 0;
        this.writtenToFile = 0;
        this.fileBasePath = fileBasePath;

        this.writer = new BufferedWriter(new FileWriter(this.fileBasePath + "_" + this.currFileNum + ".txt"));
    }

    /**
     * Writes a single *line* of text to file.
     * @param text the line of text.
     * @throws Exception if the line could not be written.
     */
    public synchronized void write(String text) throws Exception
    {
        String[] parts = text.split("\n");
        for(String part : parts)
        {
            this.writer.write(part);
            this.writer.newLine();
        }

        if(++this.writtenToFile == this.itemsPerFile)
        {
            this.writtenToFile = 0;
            this.currFileNum++;
            this.writer.close();
            this.writer = new BufferedWriter(new FileWriter(this.fileBasePath + "_" + this.currFileNum + ".txt"));
        }
    }

    /**
     * Finalizes the writing process. This must be invoked.
     * @throws Exception if closing the writer failed.
     */
    public synchronized void close() throws Exception
    {
        this.writer.close();
    }
}