package crosswords.spark

import java.io.File

import crosswords.spark.JsonInputFormat._
import org.apache.hadoop.io.compress.BZip2Codec
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import play.api.libs.json.JsObject

import scala.io.Source

/**
 * @author Laurent Valette
 */
object Similarity {
  // TODO: Use black magic to adjust the category weights
  private val CLUES_WEIGHT = 1.0f
  private val DEFS_WEIGHT = 0.0f
  private val EXAMPLES_WEIGHT = 0.0f
  private val EQUIV_WEIGHT = 0.0f
  private val ASSO_WEIGHT = 0.0f

  private val CLEANED_PARTITIONS_COUNT = 20

  def main(args: Array[String]) {
    System.setProperty("hadoop.home.dir", "C:/winutils/")
    val context = new SparkContext("local[8]", "shell")

    //val crosswords = context.jsObjectFile("../data/crosswords/*.bz2").map(_._2)
    //val words = context.jsObjectFile("../data/definitions/*.bz2").map(_._2)
    //clean(crosswords, words, "../data/cleaned")

    val cleanData = loadCleaned("../data/cleaned", context)
    val weightedData = weightCategories(cleanData)
    val edges = buildEdges(weightedData)
    val dict = createDictionary(edges).cache()
    val indexed = toIndex(edges, dict)
    val result = combine(indexed).cache()

    for (line <- Source.stdin.getLines()) {
      val query = context.parallelize(Array(line))
      val queryIndex = Dictionary.getID(dict)(query).first()._2

      val queryResults = result.filter(t => t._1 == queryIndex)
      val withWords = Dictionary.getWord(dict)(queryResults.map(_._2))
      val res1 = queryResults.collect()
      val res2 = withWords.collect()
      val test = res1.zip(res2)
      val top = test.map(t => (t._2._2, t._1._3)).sortBy(-_._2)
      top.foreach(println)
    }

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

    val defs = Bags.definitions(wikiEntries).map(cleanToCSV).coalesce(CLEANED_PARTITIONS_COUNT)
    val examples = Bags.examples(wikiEntries).map(cleanToCSV).coalesce(CLEANED_PARTITIONS_COUNT)
    val equiv = Bags.equivalents(wikiEntries).map(cleanToCSV).coalesce(CLEANED_PARTITIONS_COUNT)
    val asso = Bags.associated(wikiEntries).map(cleanToCSV).coalesce(CLEANED_PARTITIONS_COUNT)

    clues.saveAsTextFile(outputDirectory + File.separator + "clues", classOf[BZip2Codec])
    defs.saveAsTextFile(outputDirectory + File.separator + "defs", classOf[BZip2Codec])
    examples.saveAsTextFile(outputDirectory + File.separator + "examples", classOf[BZip2Codec])
    equiv.saveAsTextFile(outputDirectory + File.separator + "equiv", classOf[BZip2Codec])
    asso.saveAsTextFile(outputDirectory + File.separator + "asso", classOf[BZip2Codec])
  }

  /**
   * Load the cleaned crosswords and wiktionary entries
   * @param inputDirectory The path (with no ending file separator) where to read the previous results
   * @param context The Spark context
   * @return A sequence of RDD containing the cleaned data from the clues, definitions, examples, equivalents and associated
   */
  def loadCleaned(inputDirectory: String, context: SparkContext): Seq[RDD[(String, Seq[String])]] = {
    def parseCSV(s: String): (String, Seq[String]) = {
      val args = s.split(",")
      (args.head, args.tail)
    }

    val clues = context.textFile(inputDirectory + File.separator + "clues/part-*").map(parseCSV)
    val defs = context.textFile(inputDirectory + File.separator + "defs/part-*").map(parseCSV)
    val examples = context.textFile(inputDirectory + File.separator + "examples/part-*").map(parseCSV)
    val equiv = context.textFile(inputDirectory + File.separator + "equiv/part-*").map(parseCSV)
    val asso = context.textFile(inputDirectory + File.separator + "asso/part-*").map(parseCSV)
    Seq(clues, defs, examples, equiv, asso)
  }

  /**
   * Weight each category and group all the collections together.
   * @param l A sequence of RDD containing the cleaned data from the clues, definitions, examples, equivalents and associated
   * @return A union of all the collections after adding the category weights.
   */
  def weightCategories(l: Seq[RDD[(String, Seq[String])]]): RDD[(String, Seq[String], Float)] = {
    l(0).map(t => (t._1, t._2, CLUES_WEIGHT)) ++
      l(1).map(t => (t._1, t._2, DEFS_WEIGHT)) ++
      l(2).map(t => (t._1, t._2, EXAMPLES_WEIGHT)) ++
      l(3).map(t => (t._1, t._2, EQUIV_WEIGHT)) ++
      l(4).map(t => (t._1, t._2, ASSO_WEIGHT))
  }

  /**
   * Build an index of every word in the vocabulary.
   * @param edges A collection of edges between word/sentence and word
   * @return An RDD of unique index and word
   * @see crosswords.spark.Dictionary#build
   */
  def createDictionary(edges: RDD[(String, String, Float)]): RDD[(Long, String)] = {
    val flatten = edges.flatMap(t => Array(t._1, t._2))
    Dictionary.build(flatten)
  }

  /**
   * Build each edge of the graph.
   * @param extracted An RDD where each element is a word/sentence, its definition and the category weight
   * @return An RDD where each element represent an edge between the two words and the category weight
   */
  def buildEdges(extracted: RDD[(String, Seq[String], Float)]): RDD[(String, String, Float)] = {
    val test = extracted.filter(t => t._2.nonEmpty && !t._1.contains(" "))
    test.flatMap(t => t._2.map(s => (t._1, s, t._3)))
    // TODO: Do we split the argument 1 in case it is a sentence?
    // TODO: There are empty definitions, discard these?
  }

  /**
   * Transform a collection of string-indexed edges to a collection of indexes-indexed edges.
   * @param edges A collection of edges with their weights
   * @param dictionary A collection of all the vocabulary with a unique index
   * @return A collection of edges indexed by indexes
   */
  def toIndex(edges: RDD[(String, String, Float)], dictionary: RDD[(Long, String)]): RDD[((Long, Long), Float)] = {
    val dict = Dictionary.swap(dictionary).collectAsMap()
    edges.map(t => ((dict(t._1), dict(t._2)), t._3))
  }

  /**
   * Compute the similarity between words
   * @param edges A collection of edges with their category weights
   * @return A collection of edges with the similarity between the two words
   */
  def combine(edges: RDD[((Long, Long), Float)]): RDD[(Long, Long, Float)] = {
    edges.reduceByKey { case (c1, c2) => c1 + c2 }.map(t => (t._1._1, t._1._2, t._2))
    // TODO: How do we compute the similarity
  }
}