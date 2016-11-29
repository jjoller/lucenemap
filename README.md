# lucenemap

Persistent map that implements the java Map interface. Stored objects have to implement the Serializable interface. 
Uses a Lucene under the hood.

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