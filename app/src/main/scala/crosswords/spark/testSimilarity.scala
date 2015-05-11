package crosswords.spark

import java.io.File

import crosswords.spark.JsonInputFormat._
import org.apache.hadoop.io.compress.BZip2Codec
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import play.api.libs.json.JsObject

import scala.io.Source

import org.apache.spark.SparkConf

/**
 * @author Matteo Filipponi
 * @author Utku Sirin
 * @author Laurent Valette
 */
object Similarity {

  // TODO: Use black magic to adjust the category weights
  private val CLUES_WEIGHT = 1.0f
  private val DEFS_WEIGHT = 0.6f
  private val EXAMPLES_WEIGHT = 0.6f
  private val EQUIV_WEIGHT = 0.4f
  private val ASSO_WEIGHT = 0.1f

  private val CLEANED_PARTITIONS_COUNT = 20

  def main(args: Array[String]) {
    //System.setProperty("hadoop.home.dir", "C:/winutils/")
    //val context = new SparkContext("local[8]", "shell")

    // cluster configuartions for memory
    val conf = new SparkConf()
             .setAppName("crosswords")
             .set("spark.executor.memory", "50g")
             .set("spark.driver.memory","50g")
             .set("spark.storage.memoryFraction", "0.2")
             .set("spark.task.maxFailures","4")
             .set("spark.yarn.executor.memoryOverhead", "80")
             .set("spark.driver.maxResultSize","20g")
    val context = new SparkContext(conf)

    //val crosswords = context.jsObjectFile("../data/crosswords/*.bz2").map(_._2)
    //val words = context.jsObjectFile("../data/definitions/*.bz2").map(_._2)
    //clean(crosswords, words, "../data/cleaned")

    val cleanData = loadCleaned("./trainCleaned", context)
    val weightedData = weightCategories(cleanData)
    val edges = buildEdges(weightedData)
    val dict = createDictionary(edges)
    val indexed = toIndex(edges, dict)
    val result = combine(indexed)

    // write the intermediate steps to debug
    //weightedData.saveAsTextFile("weightedData")
    //edges.saveAsTextFile("edges")
    //dict.saveAsTextFile("dict")
    //indexed.saveAsTextFile("indexed")
    //result.saveAsTextFile("result")

    val csvResult = result.map(t => t._1 + "," + t._2 + "," + t._3).coalesce(CLEANED_PARTITIONS_COUNT)
    csvResult.saveAsTextFile("matrix/adjacency", classOf[BZip2Codec])

    val csvDict = dict.map(t => t._1 + "," + t._2).coalesce(CLEANED_PARTITIONS_COUNT)
    csvDict.saveAsTextFile("matrix/index2word", classOf[BZip2Codec])

    // test the learned adjacency matrix
    val testData = loadCleaned("./testCleaned", context)
    val precision = testAdjMatrix(result,testData,dict);

    context.stop()
  }

  /**
   * WARNING: This function takes a lot of time to execute! It also does not support multiple workers due to a limitation
   * of the Stemmer.
   * Clean the crossword and wiktionary definitions and save the results in the specified directory.
   * The results are saved in csv-like format where all the elements before the dash '-' compose the expression and all the
   * elements after it compose the definition.
   * @param crosswordEntries A collection of crosswords
   * @param wikiEntries A collection of wiktionary articles
   * @param outputDirectory The path (with no ending file separator) where to write the results
   * @see crosswords.spark.Stem
   */
  def clean(crosswordEntries: RDD[JsObject], wikiEntries: RDD[JsObject], outputDirectory: String): Unit = {
    def cleanToCSV(t: (String, String)): String = {
      val words = Stem.clean(t._1).mkString(",")
      val defs = Stem.clean(t._2).mkString(",")
      if (words.equals("") || defs.equals("")) {
        ""
      } else {
        words + "-" + defs
      }
    }

    val clues = Bags.clues(crosswordEntries).cache()
    val defs = Bags.definitions(wikiEntries).cache()
    val examples = Bags.examples(wikiEntries).cache()
    val equiv = Bags.equivalents(wikiEntries).cache()
    val asso = Bags.associated(wikiEntries).cache()

    // Build stem to unstem index
    val unstemVocabulary = (clues ++ defs ++ examples ++ equiv ++ asso).flatMap(t => t._1.split(" +") ++ t._2.split(" +"))
    val distinctUnstem = unstemVocabulary.distinct().coalesce(CLEANED_PARTITIONS_COUNT)
    val stem2Unstem = distinctUnstem.map(word => Stem.reduce(Stem.normalize(word)) + "," + word)
    stem2Unstem.saveAsTextFile(outputDirectory + File.separator + "stem2unstem", classOf[BZip2Codec])

    // Clean data
    val cleanClues = clues.map(cleanToCSV).filter(s => !s.equals(""))
    val cleanDefs = defs.map(cleanToCSV).filter(s => !s.equals("")).coalesce(CLEANED_PARTITIONS_COUNT)
    val cleanExamples = examples.map(cleanToCSV).filter(s => !s.equals("")).coalesce(CLEANED_PARTITIONS_COUNT)
    val cleanEquiv = equiv.map(cleanToCSV).filter(s => !s.equals("")).coalesce(CLEANED_PARTITIONS_COUNT)
    val cleanAsso = asso.map(cleanToCSV).filter(s => !s.equals("")).coalesce(CLEANED_PARTITIONS_COUNT)


    cleanClues.saveAsTextFile(outputDirectory + File.separator + "clues", classOf[BZip2Codec])
    cleanDefs.saveAsTextFile(outputDirectory + File.separator + "defs", classOf[BZip2Codec])
    cleanExamples.saveAsTextFile(outputDirectory + File.separator + "examples", classOf[BZip2Codec])
    cleanEquiv.saveAsTextFile(outputDirectory + File.separator + "equiv", classOf[BZip2Codec])
    cleanAsso.saveAsTextFile(outputDirectory + File.separator + "asso", classOf[BZip2Codec])
  }

  /**
   * Load the cleaned crosswords and wiktionary entries
   * @param inputDirectory The path (with no ending file separator) where to read the previous results
   * @param context The Spark context
   * @return A sequence of RDD containing the cleaned data from the clues, definitions, examples, equivalents and associated
   */
  def loadCleaned(inputDirectory: String, context: SparkContext): Seq[RDD[(Seq[String], Seq[String])]] = {
    def parseCSV(s: String): (Seq[String], Seq[String]) = {
      val args = s.split("-")
      (args(0).split(","), args(1).split(","))
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
  def weightCategories(l: Seq[RDD[(Seq[String], Seq[String])]]): RDD[(Seq[String], Seq[String], Float)] = {
    l(0).map(t => (t._1, t._2, CLUES_WEIGHT)) ++
      l(1).map(t => (t._1, t._2, DEFS_WEIGHT)) ++
      l(2).map(t => (t._1, t._2, EXAMPLES_WEIGHT)) ++
      l(3).map(t => (t._1, t._2, EQUIV_WEIGHT)) ++
      l(4).map(t => (t._1, t._2, ASSO_WEIGHT))
  }

  /**
   * Build an index of every word in the vocabulary.
   * @param edges A collection of edges between a word and another word
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
  def buildEdges(extracted: RDD[(Seq[String], Seq[String], Float)]): RDD[(String, String, Float)] = {
    // Flattens the content of the tuples: foreach tuple, foreach word w1, foreach word w2, we yield (w1, w2, weight)
    extracted.flatMap(t => t._1.flatMap(w1 => t._2.map(w2 => (w1, w2, t._3)))).filter(_._3 != 0)
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
   * Compute and normalize the similarity between words. The similarity is between 0 (inclusive) and 1 (inclusive).
   * @param edges A collection of edges with their category weights
   * @return A collection of edges with the similarity between the two words
   */
  def combine(edges: RDD[((Long, Long), Float)]): RDD[(Long, Long, Float)] = {
    val combined = edges.reduceByKey((c1, c2) => c1 + c2)
    // Group by row, then normalize each line by dividing by its maximum value
    val rowIndexed = combined.map(t => (t._1._1, (t._1._2, t._2))).groupByKey()
    rowIndexed.flatMap { row =>
      val maxValue = row._2.maxBy(t => t._2)._2
      row._2.map(t => (row._1, t._1, t._2 / maxValue))
    }
  }

  def testAdjMatrix(result: RDD[(Long, Long, Float)], testDataRaw: Seq[RDD[(Seq[String], Seq[String])]], dictionary: RDD[(Long, String)]) = {
    val testData = testDataRaw(0).map(t => (t._1, t._2))
    //testData.saveAsTextFile("testData")

    // reverse the dictionary to get the word ids
    val dict = Dictionary.swap(dictionary).collectAsMap()

    val indexed = testData.map{t =>
                                val clues = t._2
                                val size = clues.size
                                var i = 0;
                                var cluesIndexed = new Array[Long](size)
                                //if(dict.contains(t._1(0))) {
                                    while(i < size) {
                                        //if(dict.contains(clues(i)))
                                            cluesIndexed(i) = dict.getOrElse(clues(i),-1.toLong)
                                        //else
                                        //  cluesIndexed(i) = -1.toLong; // means no id is available for this word
                                        i = i + 1
                                    }
                                    (dict.getOrElse(t._1(0),-1.toLong),cluesIndexed)
                                //}
                            }
    //indexed.saveAsTextFile("indexed")

    // start testing...
    println("testing starts...");
    val testQueries = indexed.toArray
    val tlen = testQueries.size
    var i = 0;
    var correct = 0;
    val dictRev = dictionary.collectAsMap()
    while(i < tlen) {
        val query = testQueries(i)
        val wordid = query._1
        val clueids = query._2
        val clen = clueids.size
        var j = 0;
        var cluestr = ""
        while(j < clen) {
            cluestr = cluestr + dictRev(clueids(j)) + " ";
            j = j + 1
        }
        println("The query word: " + dictRev(wordid));
        println("The query clues: " + cluestr);

        // find the maximum weight word for the query
        val clues = query;
        val size = clueids.size
        val summedWeights = result.map { row =>
                                var k = 0;
                                var isInClue = 0
                                while(k < size && isInClue == 0) {
                                    if(row._2 == clueids(k))
                                        isInClue = 1
                                    k = k + 1;
                                }
                                if(isInClue == 1)
                                    (row._1.toLong,row._3.toFloat)
                                else
                                    (row._1.toLong,0.toFloat)

                    }.reduceByKey((a,b) => a + b)

        // find the maximum weighted word
        /*
        val sweight = summedWeights.max() (new Ordering[Tuple2[Long, Float]]() {
                                                override def compare(x: (Long, Float), y: (Long, Float)): Int =
                                                    Ordering[Float].compare(x._2, y._2)
                                            })
        */
        //summedWeights.map(l => (l._2,l._1)).sortByKey(false)
        val N = 5;
        val topwords = summedWeights.map(l => (l._2,l._1)).takeOrdered(N)(Ordering[(Float,Long)].reverse);
        var l = 0;
        var found = 0;
        while(l < N) {
            println("The word with max weight: wordid:" + topwords(l)._2 + " word: " + dictRev(topwords(l)._2) + " weight:" + topwords(l)._1)
            if(topwords(l)._2 == wordid && found == 0) {
                correct = correct + 1;
                found = 1
            }
            l = l + 1;
        }
        i = i + 1;
        println("iteration: " + i + " # correct: " + correct)
        println("========================================================================")
    }
    val precision = correct / tlen;
    println("precision: " + precision);

  }


}
