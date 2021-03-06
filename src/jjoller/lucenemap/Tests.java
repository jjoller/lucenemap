package jjoller.lucenemap;

import jjoller.lucenemap.LuceneMap.StorageLocation;

import java.util.Map;
import java.util.Random;

/**
 * Created by jost on 29.11.16.
 */
public class Tests {

    public static void main(String[] args) {

        Map<String, String> map = new LuceneMap<>(StorageLocation.RAM);

        //jjoller.lucenemap.LuceneMap<String, String> map = new jjoller.lucenemap.LuceneMap<>();


        assertTrue(map.size() == 0);

        map.put("key", "val");

        assertTrue(map.size() == 1);
        assertTrue(map.containsKey("key"));
        assertTrue(map.containsValue("val"));
        assertFalse(map.containsKey("notkey"));
        assertFalse(map.containsValue("notkey"));

        map.put("key", "val2");
        assertTrue(map.containsKey("key"));
        assertTrue(map.containsValue("val2"));
        assertFalse(map.containsKey("notkey"));
        assertFalse(map.containsValue("val"));

        assertTrue(map.entrySet().size() == 1);
        assertTrue(map.entrySet().iterator().next().getKey().equals("key"));
        assertTrue(map.entrySet().iterator().next().getValue().equals("val2"));


        Random random = new Random();
        long t = System.currentTimeMillis();
        int n = 1000;
        for (int i = 0; i < n; i++) {
            map.put(random.nextDouble() + "", random.nextDouble() + "");
        }
        System.out.println("took " + (System.currentTimeMillis() - t) + " ms to write " + n + " entries");


        t = System.currentTimeMillis();
        n = 1000;
        for (int i = 0; i < n; i++) {
            map.get(random.nextDouble());
        }
        System.out.println("took " + (System.currentTimeMillis() - t) + " ms to lookup " + n + " entries");

    }


    static void assertFalse(boolean v) {
        assertTrue(!v);
    }

    static void assertTrue(boolean v) {
        if (!v) {
            throw new IllegalStateException("Test failed");
        }
    }
}
