package com.krause.wikigir.main.models.general;

import com.krause.wikigir.main.Constants;
import com.krause.wikigir.main.models.utils.ExceptionWrapper;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.*;

/**
 * Executes a quick pruning of integer lists. Returns a map with the collisions count. It works in the following way:
 * <br>
 * Suppose two integers lists with a maximal integer <i>value</i> of 1000:
 * <ol>
 *     <li>list_1 = [1, 4, 9, 16, 25, 36, 49, 64]</li>
 *     <li>list_2 = [1, 8, 27, 64, 125, 216]</li>
 * </ol>
 * Assign a memory integer array mem of length 1001 and create a collisions map, mapping shared values with the number
 * of times they were detected. Set the iterations counter to 1. Iterate over the first list, for each value n1, set
 * mem[n1] = iteration (1). When iterating over the second list, still with the same iteration counter, if we find that
 * for a certain n2, mem[n2] == iteration (1), add it to the collisions map. Scale the same process to multiple lists.
 * For example, when the process is done in the first iteration, the memory map contains the value 1 in the cells:
 * [1, 4, 8, 9, 16, 25, 27, 36, 49, 64, 125, 216] and the collisions map is: {1 -> 2, 64 -> 2}.
 * <br>
 * For the next pruning process, of a different set of lists, increase the value of iteration by 1, and repeat the
 * process (this effectively "erases" the information for collisions and starts over). Reset the iterations counter
 * after too many iterations to keep its size in check.
 *
 */
public class QuickPruner
{
    private int[] memory;

    // Note that the memory size needs to be configured to be larger than the number of words in the dictionary,
    // the number of articles in the corpus and the total number of distinct categories in the corpus.
    private int memorySize;

    private int iteration;
    private int maxIteration;

    /**
     * Constructor.
     */
    public QuickPruner()
    {
        ExceptionWrapper.wrap(() ->
        {
            Properties p = new Properties();
            p.load(new BufferedInputStream(new FileInputStream(Constants.CONFIGURATION_FILE)));
            this.maxIteration = Integer.parseInt(p.getProperty("wikigir.inverted_index.quick_pruner.max_iteration"));
            this.memorySize = Integer.parseInt(p.getProperty("wikigir.inverted_index.quick_pruner.memory_size"));
        });

        clear();
    }

    /**
     * Performs the pruning.
     * @param lists the integer lists we wish to intersect.
     * @return      a map containing the intersections and collision counts.
     */
    public Map<Integer, Integer> prune(List<int[][]> lists)
    {
        Map<Integer, Integer> results = new HashMap<>();

        for(int[][] list : lists)
        {
            for (int[] pair : list)
            {
                if (this.memory[pair[0]] == this.iteration)
                {
                    results.putIfAbsent(pair[0], 1);
                    results.put(pair[0], results.get(pair[0]) + 1);
                }
                else
                {
                    this.memory[pair[0]] = this.iteration;
                }
            }
        }

        if(++this.iteration == this.maxIteration)
        {
            clear();
        }

        return results;
    }

    private void clear()
    {
        this.memory = new int[this.memorySize];
        Arrays.fill(this.memory, 0);
        this.iteration = 1;
    }
}