# LuceneMap - Fast, Scalable, Persistent Java Map

Persistent map that implements the java Map interface. Keys and values have to implement the Serializable interface. 
Based on the Lucene 6 NRT search.

```
Map<String, String> map = new LuceneMap<>();
```

To get an in-memory map that is not persistent:
```
Map<String, String> inMemory = new LuceneMap<>(true);
```

To specify the folder where the map data is stored:
```
Map<String, String> map = new LuceneMap<>("my/dir");
```

## Thread-safety
LuceneMap is thread safe as it relies on the Lucene IndexWriter for storing values and the Lucene SearcherManager for reading values. 

## Limitations
* keys and values cannot exceed 32 KB.
* put() might return an outdated value (performance tradeoff).

