# lucenemap

Persistent map that implements the java Map interface. Stored objects have to implement the Serializable interface. 

Map<String, String> map = new LuceneMap<>();

write this to get an in-memory map that is not persistent:

Map<String, String> inMemory = new LuceneMap<>(true);

to specify the folder where the map data is stored:
Map<String, String> map = new LuceneMap<>("my/dir");
