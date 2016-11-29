# LuceneMap - Fast, Scalable, Persistent Java Map

Persistent map that implements the java Map interface. Keys and values have to implement the Serializable interface. 
Based on the Lucene NRT search.

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