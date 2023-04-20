package com.krause.wikigir.main.models.utils;

import com.krause.wikigir.main.Constants;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.concurrent.*;
import java.util.Properties;

/**
 * A custom implementation of a thread pool service which maintains a fixed pool
 * size, as well as a queued runnables list and blocks whenever that list, as well
 * as all the threads in the pool, are used. Basically, it allows sending thread
 * creation requests at a rapid rate and ensuring that new requests to create
 * threads will wait until a thread is available (either in pool or queue).
 *
 * @author Amir Krause.
 */
public class BlockingThreadFixedExecutor extends ThreadPoolExecutor
{
    /**
     * Constructor with default values, utilizing all cores for fastest processing.
     */
    public BlockingThreadFixedExecutor()
    {
        this(Runtime.getRuntime().availableProcessors(), 1000, 1000);
    }

    /**
     * Constructor.
     * <br><br>
     * Creates a new thread pool executor with a fixed thread pool size (where
     * corePoolSize = maxPoolSize). Threads are set to be kept alive for a given
     * amount of time if they are idle, the queued runnables list is initialized
     * as a blocking array list to allow for blocking implementation and the
     * rejected policy (invoked when the queue, as well as the thread pool itself,
     * is full) is set to our custom blocking policy.
     *
     * @param threads 	the number of allowed threads in the fixed pool.
     * @param aliveTime the time (in seconds) to keep a thread alive when idle.
     * @param maxQueue 	the maximal number of queued runnables before blocking.
     */
    public BlockingThreadFixedExecutor(int threads, long aliveTime, int maxQueue)
    {
        super(threads, threads, aliveTime, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(maxQueue),
                new BlockThenExecutePolicy());
    }

    /**
     * Creates an executor with some common default parameter values.
     * @param threads the number of allowed threads in the fixed pool.
     */
    public BlockingThreadFixedExecutor(int threads)
    {
        this(16, 100, 1000);
    }

    /**
     * Shutdown the executor and wait for operating threads to terminate, but cap the wait time.
     */
    public void waitForTermination()
    {
        this.shutdown();

        try
        {
            Properties p = new Properties();
            p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));

            long initialTime = System.currentTimeMillis();

            while(!this.isTerminated() && System.currentTimeMillis() < initialTime +
                   Long.parseLong(p.getProperty("wikigir.executor.termination_wait_time")))
            {
                // Make it possible to do online adjustments to the waiting time, if needed.
                p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));
                this.awaitTermination(5000, TimeUnit.MILLISECONDS);
            }
        }
        catch(Exception ignored) {}
    }

    /**
     * When a runnable cannot be accepted into either the queue or the pool (since
     * they are both full), we turn to this object (overriding the default rejection
     * mechanism) to block until the queue has more room.
     *
     * @author Amir Krause.
     */
    private static class BlockThenExecutePolicy implements RejectedExecutionHandler
    {
        /**
         * Overrides the default rejected policy behavior.
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor)
        {
            // Handle spurious wake-ups.
            boolean success = false;
            while(!success)
            {
                try
                {
                    executor.getQueue().put(r);
                    success = true;
                }
                catch(InterruptedException e){}
            }
        }
    }
}