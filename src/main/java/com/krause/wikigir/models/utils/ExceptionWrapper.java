package com.krause.wikigir.models.utils;

/**
 * Provides a common handler for exceptions, without having to wrap them in try-catch at each location.
 */
public class ExceptionWrapper
{
    /**
     * Represents the action that should be taken when catching an exception.
     */
    public enum Action
    {
        EXIT,
        NOTIFY_SHORT,
        NOTIFY_LONG,
        IGNORE
    }

    /**
     * A functional interface for executing code that might throw an exception.
     */
    public interface Exceptionable
    {
        void doAction() throws Exception;
    }

    /**
     * Executes code with the default print-and-exit (program termination) action when catching an exception.
     * @param e the function to be performed.
     */
    public static void wrap(Exceptionable e)
    {
        wrap(e, Action.EXIT);
    }

    /**
     * Executes code with the given action upon exception detection.
     * @param e      the function to be performed.
     * @param action the action to be taken.
     */
    public static void wrap(Exceptionable e, Action action)
    {
        try
        {
            e.doAction();
        }
        catch(Exception ex)
        {
            switch(action)
            {
                case EXIT:
                    ex.printStackTrace();
                    System.exit(1);
                case NOTIFY_SHORT:
                    System.err.println("Exception: " + ex.getMessage());
                    break;
                case NOTIFY_LONG:
                    ex.printStackTrace();
                    break;
                case IGNORE:
                default:
                    break;
            }
        }
    }
}