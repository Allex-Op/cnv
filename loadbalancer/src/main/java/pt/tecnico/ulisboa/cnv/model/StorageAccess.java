package pt.tecnico.ulisboa.cnv.model;

import pt.tecnico.ulisboa.cnv.AwsHandler;
import pt.tecnico.ulisboa.cnv.Configs;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that handles how and when the MSS
 * will be read to obtain more informatiom, cache the information previously read and find the most
 * similar request from the entries held.
 */
public class StorageAccess {
    // Contains the last time that the entries were updated with MSS information
    private static long lastUpdateTimestamp = 0;
    private static long EXPIRATION_TIME = 5000;

    // Cache of entries
    private static List<DbEntry> gridScanEntries = new ArrayList<>();
    private static List<DbEntry> progressiveScanEntries = new ArrayList<>();
    private static List<DbEntry> greedyScanEntries = new ArrayList<>();


    /**
     * Returns the most similar request to the provided
     * arguments.
     *
     * This function needs to be synchronized as locked as the lists
     * can be changed by an update when the timeout expires.
     *
     * Which can influence the process of finding the most similar request
     *
     */
    public static synchronized DbEntry getMostSimilarRequest(RequestArguments args) {
        updateEntries(args);
        String strategy = args.getStrategy().toLowerCase();
        List<DbEntry> entries = new ArrayList<>();

        if(strategy.contains("grid"))
            entries = gridScanEntries;
        else if(strategy.contains("progressive"))
            entries = progressiveScanEntries;
        else if(strategy.contains("greedy"))
            entries = greedyScanEntries;

        return findMostSimilar(entries, args);
    }

    /**
     *  Finds the most similar request by counting the number of
     *  parameters in common that each entry has with the current
     *  arguments. Although, the viewport parameter is more critical
     *  than the others, therefore it will be counted as 3 parameters in common.
     */
    private static DbEntry findMostSimilar(List<DbEntry> entries, RequestArguments args) {
        DbEntry mostSimilar = entries.get(0);
        int equalArgs = 0;
        for (DbEntry entry : entries) {
            int currEqualArgs = 0;
            int currEntryViewPort = (entry.x1 - entry.x0) * (entry.y1 - entry.y0);

            if(entry.height == args.getHeight())
                currEqualArgs++;
            if(entry.width == args.getWidth())
                currEqualArgs++;
            if(currEntryViewPort == args.calculateViewPort())
                currEqualArgs += 3;
            if(entry.input.equals(args.getInput()))
                currEqualArgs++;
            if(entry.xS == args.getxS() && entry.yS == args.getyS())
                currEqualArgs++;

            if(currEqualArgs > equalArgs) {
                mostSimilar = entry;
                equalArgs = currEqualArgs;
            }
        }

        return mostSimilar;
    }



    /**
     *  Updates the cache of entries or not depending on the last update
     *  instant, which currently is 5 seconds.
     */
    private static void updateEntries(RequestArguments args) {
        long currentTime = System.currentTimeMillis();
        if(currentTime - lastUpdateTimestamp > EXPIRATION_TIME) {
            gridScanEntries = AwsHandler.readFromMss(Configs.GRID_SCAN);
            progressiveScanEntries = AwsHandler.readFromMss(Configs.PROGRESSIVE_SCAN);
            greedyScanEntries = AwsHandler.readFromMss(Configs.GREEDY_RANGE_SCAN);

            lastUpdateTimestamp = System.currentTimeMillis();
        }
    }
}
