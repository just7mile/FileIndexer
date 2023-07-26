import kotlinx.coroutines.runBlocking
import me.just7mile.fileindexer.InvertedIndexFileIndexer
import java.nio.file.Path
import kotlin.io.path.absolutePathString

fun main() = runBlocking {
  print("Provide folder path to watch (default [${Path.of("").absolutePathString()}]): ")

  val folderPath = readln()
  val folder = Path.of(folderPath)
  println("Starting indexer for the files inside '${folder.absolutePathString()}'")

  val indexer = InvertedIndexFileIndexer()
  indexer.start(listOf(folder))

  println("Indexer is ready!")

  println("\nType a word to search (or :q to quit): ")
  var request = readln()
  while (request != ":q") {
    val result = indexer.searchWord(request)
    if (result.isEmpty()) {
      println("Could not find any appearance of the word '$request' :(")
    } else {
      println("Found in ${result.size} files:")
      result.forEach {
        println(" [${it.file.absolutePath}] (${it.locations.size} appearances):")
        it.locations.forEachIndexed { index, location ->
          println("\t ${index + 1}. On line ${location.row} column ${location.col}")
        }
      }
    }

    println("\nType a word to search (or :q to quit): ")
    request = readln()
  }

  println("Stopping indexer...")
  indexer.cancel()

  println("Bye bye!")
}