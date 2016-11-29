import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.store.SimpleFSDirectory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Persistent Map implementation based on Lucene
 */
public class LuceneMap<K extends Serializable, V extends Serializable> implements Map {

    private static final Logger log = Logger.getLogger(LuceneMap.class.getName());

    private static final String DEFAULT_DIRECTORY = "lucenemap";
    private static final String VALUE_FIELD = "value";
    private static final String KEY_FIELD = "key";

    public LuceneMap() {
        this(DEFAULT_DIRECTORY);
    }

    public LuceneMap(boolean inMemory) {
        this(null, inMemory);
    }

    public LuceneMap(String folderUrl) {
        this(folderUrl, false);
    }

    public LuceneMap(String folderUrl, boolean inMemory) {

        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig writerConfig = new IndexWriterConfig(analyzer);
        writerConfig.setCommitOnClose(true);
        writerConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);

        int numDocs;

        try {
            Directory directory;
            if (!inMemory) {
                File folder = new File(folderUrl);
                if (!folder.exists()) {
                    folder.mkdir();
                }
                directory = new SimpleFSDirectory(folder.toPath());
            } else {
                directory = new RAMDirectory();
            }
            writer = new IndexWriter(directory, writerConfig);
            numDocs = writer.numDocs();
            searcherManager = new SearcherManager(writer, true, true, null);
            log.info("Map loaded, size: " + numDocs);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //=========================================================
        // This thread handles the actual reader reopening. (http://www.lucenetutorial.com/lucene-nrt-hello-world.html)
        //=========================================================
        ControlledRealTimeReopenThread<IndexSearcher> nrtReopenThread = new ControlledRealTimeReopenThread<>(
                writer, searcherManager, 1.0, 0.1);
        nrtReopenThread.setName("NRT Reopen Thread");
        nrtReopenThread.setDaemon(true);
        nrtReopenThread.start();

    }

    private IndexWriter writer;
    private SearcherManager searcherManager;

    /**
     * Read the object from Base64 string.
     */
    private Object fromString(String s) throws IOException, ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(s);
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    /**
     * Write the object to a Base64 string.
     */
    private String toString(Serializable o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    public Optional<V> lookup(Object key, boolean refresh) {

        K k = (K) key;
        try {
            Optional<V> result = Optional.empty();
            Optional<Document> doc = document(this.toString(k), refresh);
            if (doc.isPresent()) {
                result = Optional.of((V) fromString(doc.get().get(VALUE_FIELD)));
            }
            return result;
        } catch (ClassNotFoundException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<Document> document(String id, boolean refresh) {

        Builder builder = new BooleanQuery.Builder();
        builder.add(new TermQuery(new Term(KEY_FIELD, id)), Occur.MUST);
        return loadDoc(builder.build(), refresh);
    }

    private Optional<Document> loadDoc(Query q, boolean refresh) {

        Optional<Document> d = Optional.empty();
        try {
            if (refresh)
                this.searcherManager.maybeRefresh();
            IndexSearcher searcher = this.searcherManager.acquire();
            try {
                TopDocs docs = searcher.search(q, 1);
                if (docs.scoreDocs.length > 0) {
                    Document document = searcher.doc(docs.scoreDocs[0].doc);
                    if (document != null) {
                        d = Optional.of(document);
                    }
                }
            } finally {
                this.searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return d;
    }

    private void save(K key, V val) {

        try {
            Document doc = new Document();
            String valueString = this.toString(val);
            String keyString = this.toString(key);
            StringField valueField = new StringField(VALUE_FIELD, valueString, Store.YES);
            StringField keyField = new StringField(KEY_FIELD, keyString, Store.YES);
            doc.add(keyField);
            doc.add(valueField);
            writer.updateDocument(new Term(KEY_FIELD, keyString), doc);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public int size() {

        int indexSize;
        try {
            this.searcherManager.maybeRefresh();
            IndexSearcher searcher = this.searcherManager.acquire();
            try {
                indexSize = searcher.getIndexReader().numDocs();
            } finally {
                this.searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return indexSize;
    }

    @Override
    public boolean isEmpty() {
        return size() <= 0;
    }

    @Override
    public boolean containsKey(Object o) {

        return lookup(o, true).isPresent();
    }

    @Override
    public boolean containsValue(Object o) {

        try {
            Builder builder = new BooleanQuery.Builder();
            V v = (V) o;
            builder.add(new TermQuery(new Term(VALUE_FIELD, this.toString(v))), Occur.MUST);
            return loadDoc(builder.build(), true).isPresent();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object get(Object o) {

        Optional<V> val = this.lookup(o, true);
        if (val.isPresent()) {
            return val.get();
        } else {
            return null;
        }
    }

    @Override
    public Object put(Object o, Object o2) {
        Optional<V> val = this.lookup(o, false);
        K k = (K) o;
        V v = (V) o2;
        this.save(k, v);
        return val.isPresent() ? val.get() : null;
    }

    @Override
    public Object remove(Object o) {

        Object old = this.get(o);
        try {
            K k = (K) o;
            writer.deleteDocuments(new Term(KEY_FIELD, this.toString(k)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return old;
    }

    @Override
    public void putAll(Map map) {
        map.keySet().forEach(k -> this.put(k, map.get(k)));
    }

    @Override
    public void clear() {

        try {
            this.writer.deleteAll();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<K> keySet() {
        return entrySet().stream().map(e -> e.getKey()).collect(Collectors.toSet());
    }

    @Override
    public Collection<V> values() {
        return entrySet().stream().map(e -> e.getValue()).collect(Collectors.toList());
    }

    @Override
    public Set<Entry<K, V>> entrySet() {

        Set<Entry<K, V>> entries = new HashSet<>();
        try {
            this.searcherManager.maybeRefresh();
            IndexSearcher searcher = this.searcherManager.acquire();
            try {
                IndexReader reader = searcher.getIndexReader();
                for (int i = 0; i < reader.maxDoc(); i++) {
                    Document doc = reader.document(i);
                    V v = (V) this.fromString(doc.get(VALUE_FIELD));
                    K k = (K) this.fromString(doc.get(KEY_FIELD));
                    entries.add(new Entry<K, V>() {
                        @Override
                        public K getKey() {
                            return k;
                        }

                        @Override
                        public V getValue() {
                            return v;
                        }

                        @Override
                        public V setValue(V v) {
                            return v;
                        }
                    });
                }

            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } finally {
                this.searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return entries;
    }
}
