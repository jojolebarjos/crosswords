
package crosswords.spark

import java.io.{File, FileWriter, BufferedWriter}
import org.apache.spark.SparkContext
import JsonInputFormat._
import org.apache.spark.SparkContext.rddToPairRDDFunctions

object GraphTest {

  def main(args: Array[String]) {

    // Open context
    val context = new SparkContext("local", "shell")

    // Load JSON data
    val crosswords = context.jsObjectFile("../data/crosswords/*").map(_._2)
    val entries = context.jsObjectFile("../data/definitions/*").map(_._2)

    // Extract words
    val bags =
      Bags.clues(crosswords) ++
      Bags.definitions(entries) ++
      Bags.equivalents(entries) ++
      Bags.associated(entries)
    val words =
      bags.flatMap(b => Stem.clean(b._1 + " " + b._2))

    // TODO cache dictionary on disk

    // Create dictionary
    val dictionary = Dictionary.build(words)
    val dump = dictionary.collect()
    val output = new BufferedWriter(new FileWriter(new File("../data/dictionary.txt")))
    for ((id, word) <- dump)
      output.write(id + " " + word + "\r\n")
    output.close()

    // TODO build graph

  }

}
