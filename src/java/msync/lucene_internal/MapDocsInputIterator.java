package msync.lucene_internal;

import clojure.lang.IFn;
import clojure.lang.Keyword;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class MapDocsInputIterator implements InputIterator {

    private final Map<Keyword, IFn> _actions;
    private final Object _state;

    final Keyword WEIGHT = Keyword.intern("weight");
    final Keyword HAS_PAYLOAD = Keyword.intern("payload?");
    final Keyword PAYLOAD = Keyword.intern("payload");
    final Keyword HAS_CONTEXTS = Keyword.intern("contexts?");
    final Keyword CONTEXTS = Keyword.intern("contexts");
    final Keyword NEXT = Keyword.intern("next");

    public MapDocsInputIterator(Map<Keyword, IFn> actions, Object state) {
        _state = state;
        _actions = actions;
    }

    @Override
    public long weight() {
        return (long) _actions.get(WEIGHT).invoke(_state);
    }

    @Override
    public BytesRef payload() {
        return (BytesRef) _actions.get(PAYLOAD).invoke(_state);
    }

    @Override
    public boolean hasPayloads() {
        return (boolean) _actions.get(HAS_PAYLOAD).invoke(_state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<BytesRef> contexts() {
        return (Set<BytesRef>) _actions.get(CONTEXTS).invoke(_state);
    }

    @Override
    public boolean hasContexts() {
        return (boolean) _actions.get(HAS_CONTEXTS).invoke(_state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public BytesRef next() throws IOException {
        return (BytesRef) _actions.get(NEXT).invoke(_state);
    }
}
