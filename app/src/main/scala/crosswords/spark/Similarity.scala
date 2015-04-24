package crosswords.spark

import java.io.File

import crosswords.spark.JsonInputFormat._

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import play.api.libs.json.JsObject

/**
 * @author Laurent Valette
 */
object Similarity {
  // TODO: Use black magic to adjust the category weights
  private val CLUES_WEIGHT = 0.6f
  private val DEFS_WEIGHT = 0.6f
  private val EXAMPLES_WEIGHT = 0.4f
  private val EQUIV_WEIGHT = 0.4f
  private val ASSO_WEIGHT = 0.2f

  def main (args: Array[String]) {
    val context = new SparkContext()

    // System.setProperty("hadoop.home.dir", "C:/winutils/")

    val crosswords = context.jsObjectFile("hdfs:///projects/crosswords/data/definitions/*.bz2").map(_._2)
    val words = context.jsObjectFile("hdfs:///projects/crosswords/data/crosswords/*.bz2").map(_._2)
    clean(crosswords, words, "hdfs:///projects/crosswords/data/cleaned")

    //val test = loadCleaned("../data/tmp/cleaned", context)
    //val first = test._1.first()
    //println(first)

    context.stop()
  }

  /**
   * Clean the crossword and wiktionary definitions and save the results in the specified directory.
   * The results are saved in csv where the first element of each line is the word
   * @param crosswordEntries A collection of crosswords
   * @param wikiEntries A collection of wiktionary articles
   * @param outputDirectory The path (with no ending file separator) where to write the results
   * @see crosswords.spark.Stem
   */
  def clean(crosswordEntries: RDD[JsObject], wikiEntries: RDD[JsObject], outputDirectory: String): Unit = {
    def cleanToCSV(t: (String, String)): String = (t._1 +: Stem.clean(t._2)).mkString(",")

    val clues = Bags.clues(crosswordEntries).map(cleanToCSV)

    val defs = Bags.definitions(wikiEntries).map(cleanToCSV)
    val examples = Bags.examples(wikiEntries).map(cleanToCSV)
    val equiv = Bags.equivalents(wikiEntries).map(cleanToCSV)
    val asso = Bags.associated(wikiEntries).map(cleanToCSV)

    clues.saveAsTextFile(outputDirectory + File.separator + "clues")
    defs.saveAsTextFile(outputDirectory + File.separator + "defs")
    examples.saveAsTextFile(outputDirectory + File.separator + "examples")
    equiv.saveAsTextFile(outputDirectory + File.separator + "equiv")
    asso.saveAsTextFile(outputDirectory + File.separator + "asso")
  }

  /**
   * Load the cleaned crosswords and wiktionary entries
   * @param inputDirectory The path (with no ending file separator) where to read the previous results
   * @param context The Spark context
   * @return A tuple containing the data from the clues, definitions, examples, equivalents and associated
   */
  def loadCleaned(inputDirectory: String, context: SparkContext) = {
    def parseCSV(s: String): (String, Seq[String]) = {
      val args = s.split(",")
      (args.head, args.tail)
    }

    val clues = context.textFile(inputDirectory + File.separator + "clues/part-*").map(parseCSV)
    val defs = context.textFile(inputDirectory + File.separator + "defs/part-*").map(parseCSV)
    val examples = context.textFile(inputDirectory + File.separator + "examples/part-*").map(parseCSV)
    val equiv = context.textFile(inputDirectory + File.separator + "equiv/part-*").map(parseCSV)
    val asso = context.textFile(inputDirectory + File.separator + "asso/part-*").map(parseCSV)
    (clues, defs, examples, equiv, asso)
  }

  /**
   * Extract clues from crosswords, definitions, examples, equivalents and associated from wiki.
   * Weight each category and clean the definitions.
   * @param crosswordEntries A collection of crosswords
   * @param wikiEntries A collection of wiktionary articles
   * @return An RDD where each element is a word/sentence, its cleaned definition and a category weight
   */
  def extract(crosswordEntries: RDD[JsObject], wikiEntries: RDD[JsObject]): RDD[(String, Seq[String], Float)] = {
    val clues = Bags.clues(crosswordEntries).map(t => (t._1, t._2, CLUES_WEIGHT))

    val defs = Bags.definitions(wikiEntries).map(t => (t._1, t._2, DEFS_WEIGHT))
    val examples = Bags.examples(wikiEntries).map(t => (t._1, t._2, EXAMPLES_WEIGHT))
    val equiv = Bags.equivalents(wikiEntries).map(t => (t._1, t._2, EQUIV_WEIGHT))
    val asso = Bags.associated(wikiEntries).map(t => (t._1, t._2, ASSO_WEIGHT))

    (clues ++ defs ++ examples ++ equiv ++ asso).map(t => (t._1, Stem.clean(t._2), t._3))
  }

  /**
   * Build an index of every word in the vocabulary.
   * @param edges A collection of edges between word/sentence and word
   * @return An RDD of unique index and word
   * @see crosswords.spark.Dictionary
   */
  def createDictionary(edges: RDD[(String, String, Float)]): RDD[(Long, String)] = {
    ???
  }

  /**
   * Build each edge of the graph.
   * @param extracted An RDD where each element is a word/sentence, its definition and the category weight
   * @return An RDD where each element represent an edge between the two words and the category weight
   */
  def buildEdges(extracted: RDD[(String, Seq[String], Float)]): RDD[(String, String, Float)] = {
    // TODO: Do we split the argument 1 in case it is a sentence?
    ???
  }

  /**
   * Transform a collection of string-indexed edges to a collection of indexes-indexed edges.
   * @param edges A collection of edges with their weights
   * @param dictionary A collection of all the vocabulary with a unique index
   * @return A collection of edges indexed by indexes
   */
  def toIndex(edges: RDD[(String, String, Float)], dictionary: RDD[(Long, String)]): RDD[(Long, Long, Float)] = {
    ???
  }

  /**
   * Compute the similarity between words
   * @param edges A collection of edges with their category weights
   * @return A collection of edges with the similarity between the two words
   */
  def combine(edges: RDD[(Long, Long, Float)]): RDD[(Long, Long, Float)] = {
    // TODO: How do we compute the similarity
    ???
  }
}
