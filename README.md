# File Indexer

A simple file indexer library. An implementation using inverted index is provided in
the [InvertedIndexFileIndexer.kt](lib%2Fsrc%2Fmain%2Fkotlin%2Fme%2Fjust7mile%2Ffileindexer%2Fimpl%2FInvertedIndexFileIndexer.kt).
The implementation supports:

- Concurrent modification and search.
- Reacts to file changes.
- Only plain/text (.txt) files indexed.
- Searches are case-insensitive.

For an example of using the indexer have a look at [FileIndexerExample](examples%2FFileIndexerExample).